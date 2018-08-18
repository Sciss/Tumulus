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

import de.sciss.tumulus.UI._

import scala.swing.{BorderPanel, GridPanel}

class RecorderPanel(w: MainWindow, photoRecorder: PhotoRecorder)(implicit config: Config)
  extends BorderPanel {

  private[this] val audioRecorder = AudioRecorder()

  audioRecorder.addListener {
    case AudioRecorder.Booted => checkReady()
  }

  photoRecorder.addListener {
    case PhotoRecorder.Booted => checkReady()
  }

  private def checkReady(): Unit = {
    val ok = audioRecorder.booted && photoRecorder.booted
    ggRun.enabled = ok
    if (ok) Main.setStatus("Recorder ready.")
  }

  private[this] val ggBack = mkBackPane(MainWindow.CardRecorder) {
    w.home()
  }

  private[this] val ggPhoto = new PhotoComponent(photoRecorder)

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

  add(ggPhoto, BorderPanel.Position.Center)

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