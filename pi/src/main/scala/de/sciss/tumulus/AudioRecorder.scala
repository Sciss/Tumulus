/*
 *  AudioRecorder.scala
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

package de.sciss.tumulus

import de.sciss.file._
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.synth.{Buffer, Node, Server, Synth, Txn}
import de.sciss.model.impl.ModelImpl
import de.sciss.osc
import de.sciss.processor.Processor
import de.sciss.synth.io.{AudioFile, AudioFileSpec, AudioFileType, SampleFormat}
import de.sciss.synth.proc.AuralSystem
import de.sciss.synth.proc.graph.impl.SendReplyResponder
import de.sciss.synth.{ControlSet, SynthGraph, addToHead, addToTail}
import de.sciss.tumulus.UI.requireEDT
import de.sciss.tumulus.Util.protect
import de.sciss.tumulus.impl.{MicMeterImpl, ProcImpl}

import scala.concurrent.stm.Ref
import scala.concurrent.{Future, Promise, blocking, stm}
import scala.swing.{Component, Swing}
import scala.util.{Failure, Success}

object AudioRecorder {
  sealed trait Update
  case object Booted extends Update

  private final val ReplyRecOk  = "/rec_ok"
  private final val ReplyRecTm  = "/rec_tm"

  def apply()(implicit config: Config): AudioRecorder = new AudioRecorder
}
class AudioRecorder private (implicit config: Config) extends ModelImpl[AudioRecorder.Update] {
  import AudioRecorder._

  private[this] val auralSystem = AuralSystem(global = true)

  private[this] val ggMeter     = new MicMeterImpl

  private[this] var _booting    = false
  private[this] var _booted     = false
  private[this] var _running    = false

  def meterComponent: Component = ggMeter.component

  def booted: Boolean = _booted

  private def bootComplete(s: Server)(implicit tx: Txn): Unit = {
    ggMeter.init(s)
    startAudioLink(s)
    deferTx {
      _booted = true
      dispatch(Booted)
//      ggRun.enabled = true
//      Main.setStatus("Recorder ready.")
    }
  }

  def boot(): Unit = {
    requireEDT()
    if (_booting || _booted) return
    _booting = true

    Main.setStatus("Initializing recorder...")

    val sCfg = Server.Config()
    //    val cCfg = Client.Config()

    sCfg.deviceName         = Some(config.jackName)
    sCfg.audioBusChannels   = 32
    sCfg.audioBuffers       = 32
    sCfg.inputBusChannels   = 1
    sCfg.outputBusChannels  = 2
    sCfg.transport          = osc.TCP

    atomic { implicit tx =>
      auralSystem.addClient(new AuralSystem.Client {
        def auralStarted(s: Server)(implicit tx: Txn): Unit = bootComplete(s)

        def auralStopped()(implicit tx: Txn): Unit = ()
      })
      auralSystem.start(sCfg /* , cCfg */)
    }
  }

  private def atomic[A](body: Txn => A): A =
    stm.atomic { itx =>
      val tx = Txn.wrap(itx)
      body(tx)
    }

  private def startAudioLink(s: Server)(implicit tx: Txn): Unit = {
    val gMon = SynthGraph {
      import de.sciss.synth._
      import ugen._
      val mic = In.ar(NumOutputBuses.ir)
      //      mic.poll(label = "MIC")
      val hpf = HPF.ar(mic, config.hpf)
      ReplaceOut.ar(NumOutputBuses.ir, hpf)
      if (config.audioMonitor) {
        val bal = Pan2.ar(hpf)
        Out.ar(0, bal)
      }
    }
    Synth.playOnce(gMon, nameHint = Some("monitor"))(target = s.defaultGroup, addAction = addToHead)
  }

  def iterate(baseName: String): Future[Unit] = {
    requireEDT()
    if (_running) throw new Exception("Audio recorder still running")
    _running = true

    import Main.ec

    val futRecOpt: Option[Future[Unit]] = atomic { implicit tx =>
      auralSystem.serverOption.map { s =>
        iterRec(baseName, s)
      }
    }

    val ok  = futRecOpt.isDefined
    val msg = if (ok) "Recording sound..." else "Could not record!"
    Main.setStatus(msg)
    _running = ok

    val futRec = futRecOpt.getOrElse(Future.failed(new Exception("Audio server not booted")))
    // Await.result(futRec, Duration.Inf)

    futRec.onComplete { _ =>
      UI.deferIfNeeded {
        enableRunning()
      }
    }
    futRec
  }

  private def iterRec(baseName: String, s: Server)(implicit tx: Txn): Future[Unit] = {
    val fTmp = File.createTemp(suffix = ".irc")
    val buf = Buffer.diskOut(s)(path = fTmp.path, fileType = AudioFileType.IRCAM, sampleFormat = SampleFormat.Float)
    val gRec = SynthGraph {
      import de.sciss.synth.Ops.stringToControl
      import de.sciss.synth._
      import ugen._
      val mic   = In.ar(NumOutputBuses.ir)
      val dur   = "dur".ir
      DiskOut.ar("buf".ir, mic)
      val max   = RunningMax.ar(mic.abs, 0)
      //      Out.kr("max".kr, max)
      val rem   = Line.kr(dur, 0, dur = dur)
      val remC  = rem.ceil
      val tm    = HPZ1.kr(remC) < 0
      val done  = Done.kr(rem)
      SendReply.kr(done, max  , msgName = ReplyRecOk)
      SendReply.kr(tm  , remC , msgName = ReplyRecTm)
      FreeSelf .kr(done)
    }
    //    val bus   = Bus.control(s, 1)
    val args  = List[ControlSet]("dur" -> config.audioDur, "buf" -> buf.id)
    val syn = Synth.play(gRec, nameHint = Some("rec"))(target = s.defaultGroup, addAction = addToTail, args = args,
      dependencies = buf :: Nil)
    val maxRef = Ref(-1f)
    val recDone = new RecDoneResponder(syn, maxRef)
    val recTime = new RecTimeResponder(syn)
    recDone.add()
    recTime.add()

    val res = Promise[Unit]()

    syn.onEnd {
      protect(res) {
        val maxAmp = atomic { implicit tx =>
          recDone.remove()
          recTime.remove()
          buf.dispose()
          maxRef.get(tx.peer)
        }
        if (config.verbose) println(s"MAX AMP: $maxAmp")
        UI.deferIfNeeded {
          protect(res) {
            val fut = iterNormalize(baseName, fTmp, maxAmp)
            res.tryCompleteWith(fut)
          }
        }
      }
    }

    res.future
  }

  private def iterNormalize(baseName: String, fTmp: File, maxAmp: Float): Future[Unit] = {
    UI.requireEDT()
    Main.setStatus("Normalizing sound...")

    val gain  = if (maxAmp > 0f) 1.0f / maxAmp else 1f
    val fNorm = File.createTemp(suffix = ".aif")

    val pNorm = new ProcImpl[Unit] {
      protected def body(): Unit = blocking {
        val afIn = AudioFile.openRead(fTmp)
        try {
          val afOut = AudioFile.openWrite(fNorm,
            AudioFileSpec(fileType = AudioFileType.AIFF, sampleFormat = SampleFormat.Int16, numChannels = 1,
              sampleRate = afIn.sampleRate))
          try {
            val buf = afIn.buffer(8192)
            val buf0 = buf(0)
            val numFrames = afIn.numFrames
            var rem = numFrames
            while (rem > 0) {
              val chunk = Math.min(8192, rem).toInt
              afIn.read(buf, 0, chunk)
              var i = 0
              while (i < chunk) {
                buf0(i) *= gain
                i += 1
              }
              afOut.write(buf, 0, chunk)
              rem -= chunk
              checkAborted()
              progress = rem.toDouble / numFrames
            }
          } finally {
            afOut.close()
          }

        } finally {
          afIn.close()
        }
      }
    }

    import Main.ec
    pNorm.start()

    pNorm.onComplete { trNorm =>
      fTmp.delete()
      Swing.onEDT {
        trNorm match {
          case Success(_) =>
          case Failure(ex) =>
            val msg = ex match {
              case Processor.Aborted() => ""
              case _ => s"Normalization failed! ${ex.getMessage}"
            }
            Main.setStatus(msg)
        }
      }
    }

    Util.flatMapEDT(pNorm) { _ =>
      iterFLAC(baseName, fNorm)
    }
  }

  private def iterFLAC(baseName: String, fNorm: File): Future[Unit] = {
    UI.requireEDT()
    Main.setStatus("Compressing sound...")

    import Main.ec
    val fOut      = (File.tempDir / baseName).replaceExt("flac")
    val flacArgs  = List("-8", "-s", "-f", "-o", fOut.path, fNorm.path)
    val flacP     = IO.process("flac", flacArgs, timeOutSec = 240)(_ => ())
    flacP.onComplete { trFlac =>
      fNorm.delete()
      Swing.onEDT {
        trFlac match {
          case Success(_) =>
            if (config.verbose) println(s"Flac file: ${fOut.path}")

          case Failure(ex) =>
            val msg = ex match {
              case Processor.Aborted() => ""
              case _ => s"FLAC compression failed! ${ex.getMessage}"
            }
            Main.setStatus(msg)
        }
      }
    }

    Util.flatMapEDT(flacP) { _ =>
      iterUpload(fOut)
    }
  }

  private def iterUpload(fOut: File): Future[Unit] =
    Util.uploadWithStatus("sound")(fOut)(_.delete())

  private def enableRunning(): Unit =
    _running = false

  private final class RecTimeResponder(protected val synth: Node)
    extends SendReplyResponder {

    private[this] val NodeId = synth.peer.id

    protected val body: Body = {
      case osc.Message(ReplyRecTm, NodeId, 0, rem: Float) =>
        Swing.onEDT {
          Main.setStatus(s"Recording sound... -${rem.toInt}s")
        }
    }

    protected def added()(implicit tx: Txn): Unit = ()
  }

  private final class RecDoneResponder(protected val synth: Node, res: Ref[Float])
    extends SendReplyResponder {

    private[this] val NodeId = synth.peer.id

    protected val body: Body = {
      case osc.Message(ReplyRecOk, NodeId, 0, max: Float) =>
        atomic { implicit tx =>
          res.set(max)(tx.peer)
        }
    }

    protected def added()(implicit tx: Txn): Unit = ()
  }
}
