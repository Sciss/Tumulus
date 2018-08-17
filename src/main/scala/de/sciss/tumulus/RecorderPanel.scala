/*
 *  RecorderPanel.scala
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

import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.synth.{InMemory, Server, Txn}
import de.sciss.osc
import de.sciss.synth.proc.AuralSystem
import de.sciss.tumulus.UI._
import de.sciss.tumulus.impl.MicMeterImpl

import scala.swing.{BorderPanel, GridPanel}

class RecorderPanel(w: MainWindow)(implicit config: Config)
  extends BorderPanel {

  type S = InMemory
  private[this] val system: S = InMemory()

  private[this] val auralSystem = AuralSystem(global = true)

  private[this] val ggBack = mkBackPane("Recorder") {
    w.home()
  }

  private[this] val ggMeter = new MicMeterImpl

  add(new GridPanel(0, 1) {
    contents += ggBack
    contents += ggMeter.component
//    contents += new Label("Running version:")
//    contents += UI.mkInfoLabel(Main.version)
//    contents += new Label("Available update:")
  }, BorderPanel.Position.North)

  whenShown(this) {
    boot()
  }

  private[this] var _hasBooted = false

  private def booted(s: Server)(implicit tx: Txn): Unit = {
//    val inBus   = Bus.soundIn(s, 1)
//    val mIn     = AudioBusMeter(AudioBusMeter.Strip(inBus, s.defaultGroup , addBefore) :: Nil)
    ggMeter.init(s)
//    deferTx {
//      add(mIn.component, BorderPanel.Position.East)
//      revalidate()
//      repaint()
//    }
  }

  def boot(): Unit = {
    requireEDT()
    if (_hasBooted) return
    _hasBooted = true

    val sCfg = Server.Config()
//    val cCfg = Client.Config()

    sCfg.deviceName         = Some(config.jackName)
    sCfg.audioBusChannels   = 32
    sCfg.audioBuffers       = 32
    sCfg.inputBusChannels   = 1
    sCfg.outputBusChannels  = 2
    sCfg.transport          = osc.TCP

    system.step { implicit tx =>
      auralSystem.addClient(new AuralSystem.Client {
        def auralStarted(s: Server)(implicit tx: Txn): Unit = booted(s)

        def auralStopped()(implicit tx: Txn): Unit = ()
      })
      auralSystem.start(sCfg /* , cCfg */)
    }
  }
}