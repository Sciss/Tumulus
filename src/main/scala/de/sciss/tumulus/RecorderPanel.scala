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

import java.awt.Color

import de.sciss.tumulus.UI._

import scala.swing.{BorderPanel, Component, Dimension, Graphics2D, GridPanel}

class RecorderPanel(w: MainWindow)(implicit config: Config)
  extends BorderPanel {

//  type S = InMemory
//  private[this] val system: S = InMemory()

  private[this] val audioRecorder = new AudioRecorder

  audioRecorder.addListener {
    case AudioRecorder.Booted =>
      ggRun.enabled = true
      Main.setStatus("Recorder ready.")
  }

  private[this] val ggBack = mkBackPane("Recorder") {
    w.home()
  }

  private[this] val ggCam: Component = new Component {
    preferredSize = new Dimension(320, 240)
    opaque        = true

    override protected def paintComponent(g: Graphics2D): Unit = {
      g.setColor(Color.blue)
      val p = peer
      g.fillRect(0, 0, p.getWidth, p.getHeight)
    }
  }

  private[this] val ggRun   = mkToggleButton("Run") { sel =>
    if (sel) {
      audioRecorder.run()
    } else {

    }
  }
  ggRun.enabled = false

//  add(new GridPanel(0, 1) {
//    contents += ggBack
//  }, BorderPanel.Position.North)

  add(ggBack, BorderPanel.Position.North)

  add(ggCam, BorderPanel.Position.Center)

  add(new GridPanel(0, 1) {
    contents += audioRecorder.meterComponent
    contents += ggRun
  }, BorderPanel.Position.South)

  whenShownAndHidden(this) {
    audioRecorder.boot()
  } {
    ggRun.selected = false
  }
}