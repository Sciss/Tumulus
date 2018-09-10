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

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import de.sciss.equal.Implicits._
import de.sciss.file._
import de.sciss.tumulus.Main._self
import de.sciss.tumulus.UI._
import de.sciss.tumulus.Util._
import javax.swing.Timer

import scala.concurrent.Future
import scala.swing.{BorderPanel, GridPanel, Swing}
import scala.util.control.NonFatal

class RecorderPanel(w: MainWindow, photoRecorder: PhotoRecorder)(implicit config: Config)
  extends BorderPanel {

  private[this] val fmtName       = new SimpleDateFormat("'rec'yyMMdd'_'HHmmss", Locale.US)
  private[this] val audioRecorder = AudioRecorder()
  private[this] var _running      = false

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

  private[this] var intervalSilence = 0
  private[this] var intervalCount   = 0

  private[this] val intervalTimer: Timer = new Timer(1000, Swing.ActionListener { _ =>
    if (intervalCount > 0) {
      intervalCount -= 1
      if (intervalCount === 0) {
        intervalTimer.stop()
        startRunning()

      } else {
        if (intervalSilence == 0) {
          Main.setStatus(s"Waiting for next iteration... -${intervalCount}s")
        } else {
          intervalSilence -= 1
        }
      }
    }
  })
  intervalTimer.setRepeats(true)

  private[this] val ggRun = mkToggleButton("Run") { sel =>
    if (sel) {
      startRunning()
    } else {
      stopRunning()
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
    stopRunning()
  }

  private def stopRunning(): Unit = {
    intervalTimer.stop()
    if (!_running) Main.setStatus("")
  }

  private def startRunning(): Unit = {
    requireEDT()
    if (_running) return

    _running = true
    try {
      val fut = iterate()
      import Main.ec
      fut.onComplete { tr =>
        Swing.onEDT {
          _running = false
          val isSuccess = tr.isSuccess
          if (isSuccess) {
            Main.setStatus("Iteration completed.")
          }
          if (ggRun.selected) {
            intervalSilence = if (isSuccess) 2 else 30
            intervalCount   = if (isSuccess) config.recInterval else config.errorInterval
            intervalTimer.restart()
          }
        }
      }

    } catch {
      case NonFatal(ex) =>
        _running = false
        Main.setStatus(s"Recording failed! ${ex.getMessage}")
    }
  }

  private def iterate(): Future[Unit] = {
    val baseName  = fmtName.format(new Date)
    val futAudio  = audioRecorder.iterate(baseName)
    val futPhoto  = flatMapEDT(futAudio) { _ =>
      ggPhoto.image.fold[Future[Unit]] {
        Future.successful(())
      } { meta =>
        iterUploadPhoto(baseName, meta)
      }
    }
    val futMeta   = flatMapEDT(futPhoto) { _ =>
      val set = ggPhoto.image.fold(photoRecorder.settings)(_.settings)
      iterUploadMeta(baseName, set)
    }

    futMeta
  }

  private def iterUploadPhoto(baseName: String, meta: MetaImage): Future[Unit] =
    Util.uploadWithStatus("photo")({
      val fOut = (File.tempDir / baseName).replaceExt("jpg")
//      ImageIO.write(meta.img, "jpg", fOut)
      Util.writeJPEG(meta.img, fOut)
      fOut
    })(_.delete())

  private def iterUploadMeta(baseName: String, settings: PhotoSettings): Future[Unit] =
    Util.uploadWithStatus("meta data")({
      val fOut = (File.tempDir / baseName).replaceExt("properties")
      settings.saveAs(fOut)
      fOut
    })(_.delete())
}