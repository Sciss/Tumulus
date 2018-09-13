package de.sciss.tumulus.sound

import de.sciss.file._
import de.sciss.fscape.stream.Control
import de.sciss.fscape.{GE, Graph, graph}
import de.sciss.numbers
import de.sciss.synth.io.{AudioFile, AudioFileSpec, SampleFormat}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object CreateSoundPool {
  def any2stringadd: Any = ()

  def main(args: Array[String]): Unit = {
    minPhaseTest()
  }

  def minPhaseTest(): Unit = {
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
    val fut     = mkMinPhase(fIn = fIn, fOut = fOut)
    Await.result(fut, Duration.Inf)
    println("Done.")
    sys.exit()
  }

  def mkMinPhase(fIn: File, fOut: File): Future[Unit] = {
    import numbers.Implicits._

    val specIn  = AudioFile.readSpec(fIn)

    val rotate      = false // true
    val absSquared  = false
    val useHPF      = true

    val g = Graph {
      import graph._
      val sr          = 44100.0
      val highPass    = 150.0 // 50.0
      val numFramesIn = specIn.numFrames.toInt
      val fftSize     = numFramesIn.nextPowerOfTwo * 2
      val in          = AudioFileIn(fIn, numChannels = 1) // ++ DC(0).take(fftSize - specIn.numFrames)
      val inR         = if (!useHPF) in else HPF(in, highPass/sr) // RotateWindow(in, fftSize, fftSize/2)
      val fft         = Real1FullFFT(in = inR, size = fftSize)
//      val fftRe       = fft.complex.real * (LFSaw(2.0 / fftSize) + 1.0)
//      val fftIm       = fft.complex.imag * (LFSaw(2.0 / fftSize) + 1.0)
//      val fftRe       = Biquad(fft.complex.real, b0 = 1, b1 = -1)
//      val fftIm       = Biquad(fft.complex.imag, b0 = 1, b1 = -1)
//      val fftFlt      = ZipWindow(fftRe, fftIm)
      val fftFlt      = fft
      val logC0       = fftFlt.complex.log.max(-320) // (-80)

      // unwrap phases is _not_ needed

      //      val logC = {
      //        val mag     = logC0.complex.real
      //        val phase0  = logC0.complex.phase
      //        val phaseD  = Biquad(phase0, b0 = 1, b1 = -1)
      //        val dn      = phaseD > +math.Pi
      //        val up      = phaseD < -math.Pi
      //        val phaseA  = up * math.Pi - dn * math.Pi
      //        val phase   = phase0 + phaseA
      //        mag zip phase
      //      }

      val logC = logC0

      val cep         = Complex1IFFT(in = logC, size = fftSize) / fftSize
      val cepOut      = FoldCepstrum(in = cep, size = fftSize,
        crr = +1, cri = +1, clr = 0, cli = 0,
        ccr = +1, cci = -1, car = 0, cai = 0)

      val freq        = Complex1FFT(in = cepOut, size = fftSize) * fftSize
      val fftOut      = freq.complex.exp

      val outW        = Real1FullIFFT (in = fftOut, size = fftSize)
      val convSize    = (numFramesIn + numFramesIn - 1).nextPowerOfTwo
//      val convSize    = (numFramesIn + numFramesIn - 1).nextPowerOfTwo * 2
      //      val outWR       = outW.take(numFramesIn) // RotateWindow(outW, fftSize, fftSize/2)
      val outWT       = outW.take(numFramesIn)
      val outWR       = if (!rotate) outWT else RotateWindow(outWT, convSize, convSize/2)
      val conv1       = Real1FFT(outWR, size = numFramesIn, padding = convSize - numFramesIn, mode = 1)
      val conv2       = if (!absSquared) {
        conv1.complex.squared
//        val isLocalMax  = DetectLocalMax(BufferDisk(conv1).complex.mag, size = convSize/1024)
//        val isLocalMaxC = isLocalMax zip isLocalMax
//        BufferDisk(conv1) * BufferMemory(isLocalMaxC, convSize * 2)

      } else {
        conv1.complex.absSquared
      }
      val conv3_      = Real1IFFT(conv2, size = convSize, mode = 1)
      val conv3       = if (!rotate) conv3_ else RotateWindow(conv3_, convSize, convSize/2)
      val outLen      = numFramesIn + numFramesIn - 1
//      val outLen      = (numFramesIn + numFramesIn - 1) * 2
      val convOut     = conv3.take(outLen)
      val bleach      = Biquad(convOut, b0 = 1, b1 = -0.95) // Bleach(convOut, filterLen = 512, feedback =  0.001, filterClip = 16) - convOut

      def normalize(in: GE): GE = {
        val max       = RunningMax(in.abs).last
        val headroom  = -0.2.dbAmp
        val boost     = 18.0.dbAmp
        val spl       = 55
        val ref       = 42 // 32
        val gain      = max.reciprocal * headroom
        val buf       = BufferDisk(in)
        val sig0      = buf * gain
        val loud0     = Loudness(sig0, sampleRate = sr, size = sr/4, spl = spl)
        val loud      = RunningMax(loud0).last
        val buf1      = BufferDisk(in)
        val gain1     = (-loud.max(ref) + ref).dbAmp.pow(0.6) * gain  // 0.7 -- some good guess for phon <-> dB
        val sig       = buf1 * gain1
        val limAtk    = (0.02 * sr).toInt
        val limRls    = (0.20 * sr).toInt
        val sigB      = sig * boost
        val lim       = Limiter(sigB, attack = limAtk, release = limRls, ceiling = headroom)
        BufferMemory(sigB, limAtk + limRls) * lim
      }

      val sig         = normalize(bleach)

      val specOut     = AudioFileSpec(numChannels = 1, sampleRate = sr, sampleFormat = SampleFormat.Int24)
      /* val framesWritten = */ AudioFileOut(sig, fOut, specOut)
//      framesWritten.poll(sr, "framesWritten")
    }

    val cfg = Control.Config()
    cfg.useAsync = false
    val ctl = Control(cfg)
    ctl.run(g)
    ctl.status
  }
}
