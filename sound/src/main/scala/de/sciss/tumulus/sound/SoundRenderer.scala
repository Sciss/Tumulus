/*
 *  SoundRenderer.scala
 *  (Tumulus)
 *
 *  Copyright (c) 2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.tumulus.sound

import de.sciss.file._
import de.sciss.fscape.{GE, Graph, graph, stream}
import de.sciss.numbers
import de.sciss.synth.io.{AudioFile, AudioFileSpec, SampleFormat}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object SoundRenderer {
  def any2stringadd: Any = ()

  def main(args: Array[String]): Unit = {
    test()
  }

  def test(): Unit = {
//    val fIn     = file("/data/projects/Tumulus/data/rec180911_142333.wav")
//    val fIn     = file("/data/projects/Tumulus/data/rec180911_142507.wav")
//    val fIn     = file("/data/projects/Tumulus/data/rec180911_142640.wav")
//    val fIn     = file("/data/projects/Tumulus/data/rec180911_142813.wav")
//    val fIn     = file("/data/projects/Tumulus/data/rec180911_142946.wav")
//    val fIn     = file("/data/projects/Tumulus/data/rec180911_143120.wav")
//    val fIn     = file("/data/projects/Tumulus/data/rec180912_141405.wav")
//    val fIn     = file("/data/projects/Tumulus/data/rec180912_141540.wav")
//    val fIn     = file("/data/projects/Tumulus/data/rec180912_141714.wav")
//    val fIn     = file("/data/projects/Tumulus/data/rec180912_141848.wav")
//    val fIn     = file("/data/projects/Tumulus/data/rec180912_142023.wav")
    val fIn     = file("/data/projects/Tumulus/data/rec180912_142157.wav")
    val fOut    = file(s"/data/temp/${fIn.base}-min-phase.aif")
    val specIn  = AudioFile.readSpec(fIn)
    implicit val cfg: Config = Config()
    val fut     = run(fIn = fIn, specIn = specIn, fOut = fOut)
    Await.result(fut, Duration.Inf)
    println("Done.")
    sys.exit()
  }

  def run(fIn: File, specIn: AudioFileSpec, fOut: File)(implicit config: Config): Future[Unit] = {
    import numbers.Implicits._

    val rotate      = false
    val absSquared  = false
    val useHPF      = true
    val headroom    = -0.2.dbAmp
    val boostLim    = config.boostLimDb.dbAmp
    val splLoud     = config.splLoud
    val refLoud     = config.refLoud
    val sr          = specIn.sampleRate
    val numFramesIn = specIn.numFrames.toInt
    val highPassHz  = config.highPassHz

    val g = Graph {
      import graph._
      val fftSize     = numFramesIn.nextPowerOfTwo * 2
      val in          = AudioFileIn(fIn, numChannels = 1) // ++ DC(0).take(fftSize - specIn.numFrames)
      val inR         = if (!useHPF) in else HPF(in, highPassHz/sr) // RotateWindow(in, fftSize, fftSize/2)
      val fft         = Real1FullFFT(in = inR, size = fftSize)

      val fftFlt      = fft
      val logC0       = fftFlt.complex.log.max(-320)
      val logC        = logC0

      val cep         = Complex1IFFT(in = logC, size = fftSize) / fftSize
      val cepOut      = FoldCepstrum(in = cep, size = fftSize,
        crr = +1, cri = +1, clr = 0, cli = 0,
        ccr = +1, cci = -1, car = 0, cai = 0)

      val freq        = Complex1FFT(in = cepOut, size = fftSize) * fftSize
      val fftOut      = freq.complex.exp

      val outW        = Real1FullIFFT (in = fftOut, size = fftSize)
      val convSize    = (numFramesIn + numFramesIn - 1).nextPowerOfTwo
      val outWT       = outW.take(numFramesIn)
      val outWR       = if (!rotate) outWT else RotateWindow(outWT, convSize, convSize/2)
      val conv1       = Real1FFT(outWR, size = numFramesIn, padding = convSize - numFramesIn, mode = 1)
      val conv2       = if (!absSquared) {
        conv1.complex.squared
      } else {
        conv1.complex.absSquared
      }
      val conv3_      = Real1IFFT(conv2, size = convSize, mode = 1)
      val conv3       = if (!rotate) conv3_ else RotateWindow(conv3_, convSize, convSize/2)
      val outLen      = numFramesIn + numFramesIn - 1
      val convOut     = conv3.take(outLen)
      val bleach      = Biquad(convOut, b0 = 1, b1 = -0.95)

      def normalize(in: GE): GE = {
        val max       = RunningMax(in.abs).last
        val gain      = max.reciprocal * headroom
        val buf       = BufferDisk(in)
        val sig0      = buf * gain
        val loud0     = Loudness(sig0, sampleRate = sr, size = sr/4, spl = splLoud)
        val loud      = RunningMax(loud0).last
        val buf1      = BufferDisk(in)
        val gain1     = (-loud.max(refLoud) + refLoud).dbAmp.pow(0.6) * gain  // 0.7 -- some good guess for phon <-> dB
        val sig       = buf1 * gain1
        val limAtk    = (0.02 * sr).toInt
        val limRls    = (0.20 * sr).toInt
        val sigB      = sig * boostLim
        val lim       = Limiter(sigB, attack = limAtk, release = limRls, ceiling = headroom)
        BufferMemory(sigB, limAtk * 2.5 /* limAtk + limRls */) * lim
      }

      val sig         = normalize(bleach)

      val specOut     = AudioFileSpec(numChannels = 1, sampleRate = sr, sampleFormat = SampleFormat.Int24)
      /* val framesWritten = */ AudioFileOut(sig, fOut, specOut)
//      framesWritten.poll(sr, "framesWritten")
    }

    val fscCfg = stream.Control.Config()
    fscCfg.useAsync = false
    val ctrl = stream.Control(fscCfg)
    ctrl.run(g)
    ctrl.status
  }
}
