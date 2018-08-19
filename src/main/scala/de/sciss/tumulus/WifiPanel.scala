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

import de.sciss.tumulus.UI.{mkBackPane, mkButton}
import de.sciss.virtualkeyboard.VirtualKeyboard

import scala.swing.{BorderPanel, BoxPanel, Button, Component, Dimension, GridPanel, Label, Orientation, TextField}

class WifiPanel(w: MainWindow) // (implicit config: Config)
  extends BorderPanel {

  private[this] val ggBack = mkBackPane("Wi-Fi Settings") {
    w.home()
  }

  private[this] val ggPass = new TextField(12)

  private[this] val ggScan: Button = mkButton("Scan") {
    // scan()
  }

  private[this] val ggKeyboard = Component.wrap(new VirtualKeyboard)
  ggKeyboard.preferredSize = new Dimension(320, 160)

  add(new GridPanel(0, 1) {
    contents += ggBack
    contents += new Label("Running version:")
    contents += UI.mkInfoLabel(Main.version)
  }, BorderPanel.Position.North)

  add(ggPass, BorderPanel.Position.Center)

  add(new BoxPanel(Orientation.Vertical) {
    contents += ggKeyboard
    contents += ggScan
  }, BorderPanel.Position.South)

  // whenShown(this)(if (!hasScanned) scan())
}
