package de.sciss.tumulus

import de.sciss.equal.Implicits._
import de.sciss.file._
import de.sciss.fscape.{Graph, stream}
import de.sciss.fscape.graph._
import de.sciss.numbers.Implicits._
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object EqualizerTest extends App {
  val fIn     = file("/data/projects/Tumulus/audio_work/ir180906_124741.aif")
  val fOut    = file("/data/projects/Tumulus/audio_work/test-filter2.aif")
  val specIn  = AudioFile.readSpec(fIn)
  require (specIn.numChannels === 1)
  import specIn.sampleRate

  val numFrames = specIn.numFrames.toInt
  val freqMin   =   120.0 * 1.1
  val freqMax   = 20000.0 / 1.1
  val fftSizeH  = numFrames.nextPowerOfTwo
  val fftSize   = fftSizeH * 2
  val binSize   = sampleRate / fftSize
  val binFMin   = (freqMin  / binSize).ceil .toInt
  val binFMax   = (freqMax  / binSize).floor.toInt

  // these two critically determine the severity of the filter
  val maxBoost    = 30.0.dbAmp  // the higher, the stronger the filter (depth of notches)
  val smoothSide  = 4           // the higher, the stronger the filter (slope width of notches)

  println(s"numFrames = $numFrames, fftSize = $fftSize, binFMix = $binFMin, binFMax = $binFMax")

  val g = Graph {
    val in      = AudioFileIn(fIn, numChannels = 1)
    val fft     = Real1FFT(in, size = numFrames, padding = fftSize - numFrames, mode = 2)
    val mag0    = fft.complex.mag
    val magLo   = WindowApply(mag0, size = fftSizeH, index = binFMin)
    val magHi   = WindowApply(mag0, size = fftSizeH, index = binFMax)
    val mag0Buf = {
      val mag0Smooth = SlidingPercentile(mag0, len = smoothSide * 2 + 1, frac = 1.0).drop(smoothSide) ++ DC(0).take(smoothSide)
      BufferMemory(mag0Smooth /* mag0 */, fftSize)
    }
    val magPeri =
      DC(magLo).take(binFMin) ++
      DC(0.0)  .take(binFMax - binFMin) ++
      DC(magHi).take(fftSizeH - binFMax)

    val mag     = mag0Buf max magPeri
    val magMax  = RunningMax(mag).last
    val magBuf  = BufferMemory(mag, fftSizeH)
    val inv     = (magBuf.reciprocal * magMax).min(maxBoost)
    val invC    = inv zip DC(0.0).take(fftSizeH)
//    val sig0    = Real1IFFT(invC, size = fftSize, mode = 2)

    val invCLog   = invC.complex.log.max(-320) // (-80)

    val fftSizeCep  = fftSize
    val cep         = Complex1IFFT(in = invCLog, size = fftSizeCep) / fftSize
    val cepOut      = FoldCepstrum(in = cep, size = fftSizeCep,
      crr = +1, cri = +1, clr = 0, cli = 0,
      ccr = +1, cci = -1, car = 0, cai = 0)

    val fltF        = Complex1FFT(in = cepOut, size = fftSizeCep) * fftSize
    val fltFExp     = fltF.complex.exp
    val flt0        = Real1FullIFFT (in = fltFExp, size = fftSizeCep)
    val fltNorm0    = {
      val max   = RunningMax(flt0.abs).last
      val gain  = max.reciprocal * -0.2.dbAmp
      BufferMemory(flt0, fftSize) * gain
    }

    val flt         = ResizeWindow(fltNorm0, fftSize, stop = -fftSizeH)

    val written     = AudioFileOut(flt, fOut, AudioFileSpec(numChannels = 1, sampleRate = sampleRate))
    written.poll(sampleRate, "frames-written")
  }

  val config = stream.Control.Config()
  config.useAsync = false
  implicit val ctrl: stream.Control = stream.Control(config)
  ctrl.run(g)
  Await.result(ctrl.status, Duration.Inf)
  println("Done.")
  sys.exit()
}
