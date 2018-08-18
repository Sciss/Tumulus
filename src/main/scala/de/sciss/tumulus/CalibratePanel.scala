/*
 *  CalibratePanel.scala
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

import de.sciss.numbers
import de.sciss.tumulus.UI.mkBackPane

import scala.swing.{BorderPanel, ButtonGroup, GridPanel}
import scala.util.Try

class CalibratePanel(w: MainWindow, photoRecorder: PhotoRecorder)(implicit config: Config)
  extends BorderPanel {

  private[this] val ggBack = mkBackPane(MainWindow.CardCalibrate) {
    w.home()
  }

  private[this] val ggPhoto = new PhotoComponent(photoRecorder)

  private def adjustSettings(fun: PhotoSettings => PhotoSettings): Unit = {
    val set0  = photoRecorder.settings
    val set1  = fun(set0)
    photoRecorder.settings = set1
    val saved = Try(set1.save()).isSuccess
    if (!saved) Main.setStatus("Error: could not save settings!")
  }

  private[this] val iso = PhotoSettings.StandardIso.map { v =>
    UI.mkToggleButton(v.toString) { sel =>
      if (sel) {
        adjustSettings(_.copy(iso = v))
      }
    }
  }

  private[this] val bgIso = new ButtonGroup(iso: _*)
  private[this] val pIso  = new GridPanel(1, 0) {
    contents ++= iso
  }

  private def adjustShutter(dir: Int): Unit = {
    val set0  = photoRecorder.settings
    import numbers.Implicits._
    val dif   = PhotoSettings.StandardShutter.map(_ absDif set0.shutterHz)
    val idx0  = dif.indexOf(dif.min)
    val idx1  = (idx0 + dir).clip(0, dif.size - 1)
    val shut  = PhotoSettings.StandardShutter(idx1)
    Main.setStatus(s"Shutter is at 1/${shut}s.")
    adjustSettings(_.copy(shutterHz = shut))
  }

  private[this] val wb = UI.mkButton("White Balance") {
    Main.setStatus("TODO: White balance")
  }

  private[this] val ggShutterClose = {
    val b = UI.mkButton(null) {
      adjustShutter(1)
    }
    b.icon = UI.getIconResource("shutter-close.png")
    b
  }

  private[this] val ggShutterOpen = {
    val b = UI.mkButton(null) {
      adjustShutter(-1)
    }
    b.icon = UI.getIconResource("shutter-open.png")
    b
  }

  add(ggBack, BorderPanel.Position.North)

  add(ggPhoto, BorderPanel.Position.Center)

  add(new GridPanel(0, 1) {
    contents += pIso
    contents += new GridPanel(1, 2) {
      contents += wb
      contents += new GridPanel(1, 2) {
        contents += ggShutterClose
        contents += ggShutterOpen
      }
    }
  }, BorderPanel.Position.South)

  UI.whenShown(this) {
    photoRecorder.boot()
  }

  // init settings
  {
    val set0    = photoRecorder.settings
    val isoIdx  = PhotoSettings.StandardIso.indexWhere(_ == set0.iso)
    if (isoIdx >= 0) bgIso.select(iso(isoIdx))
  }
}