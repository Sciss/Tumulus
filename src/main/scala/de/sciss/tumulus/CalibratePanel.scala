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

class CalibratePanel(w: MainWindow, photoRecorder: PhotoRecorder) // (implicit config: Config)
  extends BorderPanel {

  private[this] val ggBack = mkBackPane(MainWindow.CardCalibrate) {
    w.home()
  }

  private[this] val ggPhoto = new PhotoComponent(photoRecorder)

  private def adjustSettings(fun: PhotoSettings => PhotoSettings): PhotoSettings = {
    val set0  = photoRecorder.settings
    val set1  = fun(set0)
    photoRecorder.settings = set1
    val saved = Try(set1.save()).isSuccess
    if (!saved) Main.setStatus("Error: could not save settings!")
    set1
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
    showShutter(shut)
    adjustSettings(_.copy(shutterHz = shut))
  }

  private def showShutter(hz: Int): Unit =
    Main.setStatus(s"Shutter is at 1/${hz}s.")

  private[this] var wbLastClick = System.currentTimeMillis()

  private def showGains(prefix: String, set: PhotoSettings): Unit = {
    import numbers.Implicits._
    Main.setStatus(f"$prefix gains: [red: ${set.redGain.ampDb}%1.1f dB, blue: ${set.blueGain.ampDb}%1.1f dB]")
  }

  private[this] val wb = UI.mkButton("White Balance") {
    val now = System.currentTimeMillis()
    if (now - wbLastClick < 1500) {   // "double click"
      ggPhoto.image.foreach { meta =>
        val gains = WhiteBalance.analyze(meta.img)
        val newSet = adjustSettings { in =>
          if (photoRecorder.gainsSupported) {
            in.copy(redGain = in.redGain * gains.red, blueGain = in.blueGain * gains.blue)
          } else {
            in.copy(redGain = gains.red, blueGain = gains.blue)
          }
        }
        showGains("New", newSet)
      }
    } else {
      val currSet = photoRecorder.settings
      showGains("Current", currSet)
    }
    wbLastClick = now
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
    showShutter(photoRecorder.settings.shutterHz)
    ggPhoto.enableCropEditing { in =>
      adjustSettings { set0 =>
        set0.copy(cropLeft = in.left, cropTop = in.top, cropRight = in.right, cropBottom = in.bottom)
      }
    }
  }

  // init settings
  {
    val set0    = photoRecorder.settings
    val isoIdx  = PhotoSettings.StandardIso.indexWhere(_ == set0.iso)
    if (isoIdx >= 0) bgIso.select(iso(isoIdx))
  }
}