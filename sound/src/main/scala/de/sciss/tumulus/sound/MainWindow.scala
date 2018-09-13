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
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.synth.{Server, Synth, Txn}
import de.sciss.osc
import de.sciss.numbers
import de.sciss.swingplus.Spinner
import de.sciss.synth.{SynthGraph, addAfter}
import de.sciss.synth.ugen
import de.sciss.synth.proc.AuralSystem
import de.sciss.synth.swing.{AudioBusMeter, ServerStatusPanel}
import de.sciss.tumulus.sound.Main.atomic
import javax.swing.SpinnerNumberModel
import de.sciss.lucre.stm.TxnLike.{peer => stmPeer}

import scala.concurrent.stm.Ref
import scala.swing.event.ValueChanged
import scala.swing.{BorderPanel, BoxPanel, Button, FlowPanel, Frame, Label, Orientation}

class MainWindow(as: AuralSystem, oscT: osc.UDP.Transmitter.Undirected)(implicit config: Config) {
  private[this] val ggServer  = new ServerStatusPanel
  private[this] val pTop      = new BoxPanel(Orientation.Vertical) {
    contents += ggServer
  }

  private[this] val ggLightTest = Button("Test Light") {
    val vec: Vec[Int] = (0 until config.ledGroups).flatMap { group =>
      (0 until config.ledPerGroup).map { i =>
        import numbers.Implicits._
        val h = (group.linLin(0f, config.ledGroups, 0f, 1f) + i.linLin(0f, config.ledPerGroup, 0f, 0.5f / config.ledGroups)) % 1f
        java.awt.Color.getHSBColor(h, 1f, 1f).getRGB
      }
    }
    val m = osc.Message("/led", vec: _*)
    oscT.send(m, config.lightSocket)
  }

  private[this] val ggLightOff = Button("Light Off") {
    val vec: Vec[Int] = Vector.fill(config.ledCount)(0)
    val m = osc.Message("/led", vec: _*)
    oscT.send(m, config.lightSocket)
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
                val ampDb   = config.chanAmps(ch)
                val ampVal  = (ampDb - 12.0).dbAmp

                val g = SynthGraph {
                  import ugen._
                  import de.sciss.synth.Ops.stringToControl
                  val noise = WhiteNoise.ar("amp".kr(0f))
                  val out   = "out".kr
                  Out.ar(out, noise)
                }

                val syn = Synth.play(g, nameHint = Some("test-sound"))(
                  target = s, args = List("amp" -> ampVal, "out" -> ch)
                )
                synthRef.swap(Some(syn)).foreach(_.dispose())
              }
            }
          }
      }
    }
    res
  }

  private[this] val pBottom   = new FlowPanel(ggLightTest, ggLightOff, new Label("Test chan.:"), ggSoundChan)

  private def packFrame(): Unit = {
    frame.pack().centerOnScreen()
  }

  private[this] val frame: Frame = new Frame {
    title = s"${Main.name} - ${Main.fullVersion}"

    contents = new BorderPanel {
      add(pTop    , BorderPanel.Position.North)
      add(pBottom , BorderPanel.Position.South)
    }
    open()
  }

  packFrame()
  frame.open()

  atomic { implicit tx =>
    as.addClientNow(new AuralSystem.Client {
      def auralStarted(s: Server)(implicit tx: Txn): Unit = {
        for (_ <- 0 until 4) s.nextNodeId() // XXX TODO
        val busOut = de.sciss.synth.AudioBus(s.peer, 0, config.numChannels) // Bus.soundOut(s, config.numChannels)
        deferTx {
          ggServer.server = Some(s.peer)
          val strip   = AudioBusMeter.Strip(busOut, target = s.defaultGroup.peer, addAction = addAfter)
          val meter   = AudioBusMeter(strip :: Nil)
          pTop.contents += meter.component
          packFrame()
        }
      }

      def auralStopped()(implicit tx: Txn): Unit = deferTx {
        ggServer.server = None
      }
    })
  }
}
