/*
 *  UpdatePanel.scala
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

import scala.swing.{BorderPanel, Button, GridPanel, Label, ListView}
import UI._

class UpdatePanel(w: MainWindow)(implicit config: Config)
  extends BorderPanel {

  private[this] var _hasScanned = false

  def hasScanned: Boolean = _hasScanned

  private[this] val ggBack = mkButton("Back") {
    w.home()
  }
  private[this] val ggList = new ListView[String] {
    visibleRowCount = 3
  }

  private[this] val ggScan = mkButton("Scan") {
    _hasScanned = true
  }

  private[this] val ggInstall = mkButton("Install") {

  }

  add(new GridPanel(0, 1) {
    contents += ggBack
    contents += new Label("Available updates:")
  }, BorderPanel.Position.North)

  add(ggList, BorderPanel.Position.Center)

  add(new GridPanel(0, 1) {
    contents += ggScan
    contents += ggInstall
  }, BorderPanel.Position.South)

  def scan(): Unit = {

  }
}
