/*
 *  MainWindow.scala
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

import de.sciss.kollflitz.Vec
import de.sciss.lucre.stm.TxnLike.{peer => stmPeer}
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.synth.{Server, Synth, Txn}
import de.sciss.{numbers, osc}
import de.sciss.swingplus.Spinner
import de.sciss.synth.proc.AuralSystem
import de.sciss.synth.swing.{AudioBusMeter, ServerStatusPanel}
import de.sciss.synth.{SynthGraph, addAfter, addToTail, ugen}
import de.sciss.tumulus.ScreenLight
import de.sciss.tumulus.sound.Main.atomic
import javax.swing.SpinnerNumberModel

import scala.concurrent.stm.Ref
import scala.swing.event.{ButtonClicked, ValueChanged}
import scala.swing.{BorderPanel, BoxPanel, Button, FlowPanel, Frame, GridPanel, Label, Orientation, ToggleButton}

class MainWindow(as: AuralSystem, light: LightDispatch, oscT: osc.UDP.Transmitter.Undirected)(implicit config: Config) {
  private[this] val ggServer  = new ServerStatusPanel
  private[this] val pTop      = new BoxPanel(Orientation.Vertical) {
    contents += ggServer
  }
  private[this] val lbStatus  = {
    val res = new Label("Ready.")
    res.preferredSize = res.preferredSize
    res
  }

  private[this] val ggLightTest = Button("Test Light") {
    val vec: Vec[Int] = (0 until config.ledGroups).flatMap { group =>
      (0 until config.ledPerGroup).map { i =>
        import numbers.Implicits._
        val h = (group.linLin(0f, config.ledGroups, 0f, 1f) + i.linLin(0f, config.ledPerGroup, 0f, 0.5f / config.ledGroups)) % 1f
        java.awt.Color.getHSBColor(h, 1f, 1f).getRGB
      }
    }
    light.setRGB(vec)
  }

  private[this] val ggLightOff = Button("Light Off") {
    val vec: Vec[Int] = Vector.fill(config.ledCount)(0)
    light.setRGB(vec)
  }

  private[this] val synthRef = Ref(Option.empty[Synth])

  private[this] val ggSoundChan = {
    val mCh = new SpinnerNumberModel(0, 0, config.numChannels, 1)
    val res = new Spinner(mCh) {
      listenTo(this)
      reactions += {
        case ValueChanged(_) =>
          import numbers.Implicits._
          val ch = mCh.getNumber.intValue() - 1
          atomic { implicit tx =>
            synthRef.swap(None).foreach(_.dispose())
            if (ch >= 0) {
              as.serverOption.foreach { s =>
                val ampDb   = config.chanAmpsDb(ch)
                val ampVal  = (ampDb - 12.0).dbAmp

                val g = SynthGraph {
                  import de.sciss.synth.Ops.stringToControl
                  import ugen._
                  val noise = WhiteNoise.ar("amp".kr(0f))
                  val out   = "out".kr
                  Out.ar(out, noise)
                }

                // addToTail, so we can mute the regular output,
                // but we're before the meters
                val syn = Synth.play(g, nameHint = Some("test-sound"))(
                  target = s, addAction = addToTail, args = List("amp" -> ampVal, "out" -> ch)
                )
                synthRef.swap(Some(syn)).foreach(_.dispose())
              }
            }
          }
      }
    }
    res
  }

  private[this] val mMasterGainDb =
    new SpinnerNumberModel(config.masterGainDb, math.min(-30.0, (config.masterGainDb - 1).round.toDouble), 0.0, 0.1)

  private def masterGainValue: Double = {
    import numbers.Implicits._
    mMasterGainDb.getNumber.doubleValue().dbAmp
  }

  private def setMasterGain(linear: Double): Unit =
    atomic { implicit tx =>
      as.serverOption.foreach { s =>
        s.defaultGroup.set("master-amp" -> linear)
      }
    }

  private[this] val ggMasterMute = new ToggleButton("Mute") {
    listenTo(this)
    reactions += {
      case ButtonClicked(_) =>
        val ampVal = if (selected) 0.0 else masterGainValue
        setMasterGain(ampVal)
    }
  }

  private[this] val ggMasterGain = {
    val res = new Spinner(mMasterGainDb) {
      listenTo(this)
      reactions += {
        case ValueChanged(_) =>
          val ampVal = masterGainValue
          ggMasterMute.selected = false
          setMasterGain(ampVal)
      }
    }
    res
  }

  private[this] val ggLights = {
    val res = ScreenLight.component()
    light.setView(res)
    res
  }

  private[this] val pBottom = new GridPanel(3, 1) {
    vGap = 4
    contents += new FlowPanel(ggMasterMute, new Label("Master gain [dB]:"), ggMasterGain)
    contents += new FlowPanel(ggLightTest, ggLightOff, new Label("Test chan.:"), ggSoundChan)
    contents += lbStatus
  }

  private def packFrame(): Unit = {
    frame.pack().centerOnScreen()
  }

  private[this] val frame: Frame = new Frame {
    title = s"${Main.name} - ${Main.fullVersion}"

    contents = new BorderPanel {
      add(pTop              , BorderPanel.Position.North  )
      add(ggLights.component, BorderPanel.Position.Center )
      add(pBottom           , BorderPanel.Position.South  )
    }
    open()

    override def closeOperation(): Unit = {
      Main.quit()
    }
  }

  packFrame()
  frame.open()

  atomic { implicit tx =>
    as.addClientNow(new ServerUser("main-window") {
      def booted(s: Server)(implicit tx: Txn): Unit = {
        tx.afterCommit(println("Server booted (MainWindow)."))
        val busOut = de.sciss.synth.AudioBus(s.peer, 0, config.numChannels) // Bus.soundOut(s, config.numChannels)
        deferTx {
          ggServer.server = Some(s.peer)
          val strip   = AudioBusMeter.Strip(busOut, target = s.defaultGroup.peer, addAction = addAfter)
          val meter   = AudioBusMeter(strip :: Nil)
          pTop.contents += meter.component
          packFrame()
        }
      }
    })
  }

  def setStatus(s: String): Unit =
    lbStatus.text = s
}
