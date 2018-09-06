/*
 *  WifiPanel.scala
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

import de.sciss.tumulus.UI.mkBackPane

import scala.concurrent.{Future, blocking}
import scala.swing.{BorderPanel, BoxPanel, GridPanel, Orientation, Swing}

class AdvancedPanel(w: MainWindow)(implicit config: Config)
  extends BorderPanel {

  private[this] val ggBack = mkBackPane("Advanced Settings") {
    w.home()
  }

  private[this] val ggDHCP_IPv4 = UI.mkToggleButton("DHCP IP v4") { sel =>
    if (sel) {
      import Main.ec
      val fut = Future {
        blocking {
          val (code, _) = IO.sudo("dhclient", List("-4", "-v", WifiPanel.interface))
          code == 0
        }
      }
      fut.onComplete { tr =>
        val ok = tr.toOption.contains(true)
        val msg = if (ok) "succeeded." else "failed!"
        Swing.onEDT {
          Main.setStatus(s"dhclient $msg")
        }
      }
    }
  }

//  private[this] val ggPass = new TextField(12)
//
//  private[this] val ggKeyboard = Component.wrap(new VirtualKeyboard)
//  ggKeyboard.preferredSize = new Dimension(320, 160)
//
//  private[this] val pKeyboard = new BoxPanel(Orientation.Vertical) {
//    contents += new FlowPanel(new Label("Enter password:", null, Alignment.Leading))
//    contents += ggPass
//    contents += ggKeyboard
//  }

  private[this] val ggReboot = UI.mkButton("Reboot") {
    Main.reboot()
  }

  private[this] val ggExit = UI.mkButton("Close App") {
    sys.exit()
  }

  add(new GridPanel(0, 1) {
    contents += ggBack
    contents += ggDHCP_IPv4
    //      contents += new Label
    //    }
  }, BorderPanel.Position.North)

  add(new BoxPanel(Orientation.Vertical) {
//    contents += pKeyboard
    contents += new GridPanel(1, 2) {
      contents += ggExit
      contents += ggReboot
    }
  }, BorderPanel.Position.South)

  // UI.whenShown(this)(if (!hasScanned) scan())
}
