package de.sciss.tumulus

import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.synth
import de.sciss.lucre.synth.{Buffer, Server, Synth, Txn}
import de.sciss.numbers.Implicits._
import de.sciss.swingplus.Spinner
import de.sciss.synth.proc.AuralSystem
import de.sciss.synth.{SynthGraph, ugen}
import javax.swing.SpinnerNumberModel

import scala.concurrent.stm.TxnExecutor
import scala.swing.event.ValueChanged
import scala.swing.{Button, Frame, GridPanel, Label, Swing}

object FindChannelVolumes {
  final val NumChannels = 12
  final val MaxAmpDb    = -12.0
  final val MinAmpDb    = -36.0

  val data0 = List(-24.0, -24.0, -24.0, -24.0, -24.0, -24.0, -20.0, -18.0, -18.0, -20.0, -18.0, -20.0)

  def main(args: Array[String]): Unit = {
    run()
  }

  def run(): Unit = {
    val as                  = AuralSystem()
    val sCfg                = Server.Config()
    sCfg.outputBusChannels  = NumChannels
    sCfg.inputBusChannels   = 0
    sCfg.deviceName         = Some("Tumulus")

    atomic { implicit tx =>
      as.start(sCfg)
      as.addClientNow(new AuralSystem.Client {
        def auralStarted(s: synth.Server)(implicit tx: Txn): Unit = booted(s)

        def auralStopped()(implicit tx: Txn): Unit = ()
      })
    }
  }

  def atomic[A](fun: Txn => A): A = {
    TxnExecutor.defaultAtomic { itx =>
      val tx = Txn.wrap(itx)
      fun(tx)
    }
  }

  def mkGUI(syn: Synth): Unit = {
    val data  = data0.toArray // Array.fill(NumChannels)(MinAmpDb)
    val mAmp  = new SpinnerNumberModel(MinAmpDb, MinAmpDb, MaxAmpDb, 0.1)
    val mCh   = new SpinnerNumberModel(1, 1, NumChannels, 1)

    val ggAmp = new Spinner(mAmp) {
      listenTo(this)
      reactions += {
        case ValueChanged(_) =>
          val ampDb = mAmp.getNumber.doubleValue()
          val ch    = mCh.getNumber.intValue() - 1
          data(ch)  = ampDb
          val ampVal = ampDb.dbAmp
          atomic { implicit tx =>
            syn.set("amp" -> ampVal)
          }
      }
    }
    val ggCh = new Spinner(mCh) {
      listenTo(this)
      reactions += {
        case ValueChanged(_) =>
          val ch      = mCh.getNumber.intValue() - 1
          val ampDb   = data(ch)
          val ampVal  = ampDb.dbAmp
          mAmp.setValue(ampDb)
          atomic { implicit tx =>
            syn.set("amp" -> ampVal, "out" -> ch)
          }
      }
    }

    val ggPrint = Button("Print") {
      println(data.mkString("List(", ", ", ")"))
    }

    val f = new Frame {
      title = "Channel Volumes"
      contents = new GridPanel(3, 2) {
        hGap      = 2
        vGap      = 2
        contents += new Label("Channel:")
        contents += ggCh
        contents += new Label("Peak Amp [dBFS]:")
        contents += ggAmp
        contents += new Label()
        contents += ggPrint
        border    = Swing.EmptyBorder(8)
      }
      pack().centerOnScreen()

      override def closeOperation(): Unit =
        sys.exit()
    }

    f.open()
  }

  def booted(s: Server)(implicit tx: Txn): Unit = {
    val buf = Buffer(s)(numFrames = 529200)
//    buf.read("/data/projects/Tumulus/audio_work/MinPhaseNorm.aif")
    buf.read("/data/projects/Tumulus/audio_work/rec180911_142946-min-phaseLim.aif")
    val g = SynthGraph {
      import de.sciss.synth.Ops.stringToControl
      import ugen._
      val b     = "buf".kr
      val amp   = "amp".kr(0.0)
      val out   = "out".kr(0)
      val tr    = HPZ1.kr(out) sig_!= 0
      val play  = PlayBuf.ar(numChannels = 1, buf = b, trig = tr, loop = 1)
      val sig   = Limiter.ar(play * amp, level = -12.0.dbAmp)
      Out.ar(out, sig)
    }
    val syn = Synth.playOnce(g, nameHint = Some("adjust"))(target = s, args = List("buf" -> buf.id),
      dependencies = buf :: Nil)

    deferTx {
      mkGUI(syn)
    }
  }
}
