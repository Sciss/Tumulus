/*
 *  CmdLinePhotoRecorder.scala
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
package impl

import de.sciss.file._
import de.sciss.model.impl.ModelImpl
import javax.imageio.ImageIO

import scala.concurrent.blocking
import scala.swing.Swing
import scala.util.{Failure, Success, Try}

abstract class CmdLinePhotoRecorder (implicit config: Config)
  extends PhotoRecorder with ModelImpl[PhotoRecorder.Update] { rec =>

  private[this] var _booting  = false
  private[this] var _booted   = false

  def booted: Boolean = _booting

  protected def takePhoto(fOut: File): Boolean

  def boot(): Unit = {
    UI.requireEDT()
    if (_booting || _booted) return
    _booting = true

    val p = new ProcImpl[Unit] {
      protected def body(): Unit = {
        Swing.onEDT {
          _booted = true
          rec.dispatch(PhotoRecorder.Booted)
        }
        val previewDlyMS = config.photoPreviewDly * 1000L
        while (true) {
          blocking {
            val fTmp = File.createTemp(suffix = ".jpg")
            val tr = Try(takePhoto(fTmp))
            tr match {
              case Success(ok) =>
                if (ok) {
                  Try {
                    val img = ImageIO.read(fTmp)
                    rec.dispatch(PhotoRecorder.Preview(img))
                  }
                } else {
                  Console.err.println("Photo failed (code != 0)")
                }

              case Failure(ex) =>
                Console.err.println(s"Photo failed: ${ex.getMessage}")
            }
            fTmp.delete()
            Thread.sleep(previewDlyMS)
          }
        }
      }
    }

    import Main.ec
    p.start()
  }
}
