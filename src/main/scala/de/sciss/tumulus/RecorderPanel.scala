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
import java.awt.image.BufferedImage

import de.sciss.tumulus.UI._

import scala.swing.{BorderPanel, Component, Dimension, Graphics2D, GridPanel}

class RecorderPanel(w: MainWindow)(implicit config: Config)
  extends BorderPanel {

//  type S = InMemory
//  private[this] val system: S = InMemory()

  private[this] val audioRecorder = AudioRecorder()
  private[this] val photoRecorder = PhotoRecorder()

  private[this] var previewImage: BufferedImage = _

  audioRecorder.addListener {
    case AudioRecorder.Booted => checkReady()
  }

  photoRecorder.addListener {
    case PhotoRecorder.Booted => checkReady()
    case PhotoRecorder.Preview(img) =>
      previewImage = img
      ggCam.repaint()
  }

  private def checkReady(): Unit = {
    val ok = audioRecorder.booted && photoRecorder.booted
    ggRun.enabled = ok
    if (ok) Main.setStatus("Recorder ready.")
  }

  private[this] val ggBack = mkBackPane("Recorder") {
    w.home()
  }

  private[this] val ggCam: Component = new Component {
    preferredSize = new Dimension(320, 240)
    opaque        = true

    override protected def paintComponent(g: Graphics2D): Unit = {
      g.setColor(Color.black)
      val p = peer
      val w = p.getWidth
      val h = p.getHeight
      g.fillRect(0, 0, w, h)
      val img = previewImage
      if (img != null) {
        g.drawImage(img, 0, 0, w, h, p)
      }
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
    photoRecorder.boot()

  } {
    ggRun.selected = false
  }
}