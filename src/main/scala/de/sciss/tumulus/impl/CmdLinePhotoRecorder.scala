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

import java.awt.image.BufferedImage

import de.sciss.model.impl.ModelImpl

import scala.concurrent.blocking
import scala.swing.Swing
import scala.util.{Failure, Success, Try}

abstract class CmdLinePhotoRecorder (implicit config: Config)
  extends PhotoRecorder with ModelImpl[PhotoRecorder.Update] { rec =>

  private[this] var _booting  = false
  private[this] var _booted   = false

  def booted: Boolean = _booting

  protected def takePhoto(): BufferedImage

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
            val tr = Try(takePhoto())
            tr match {
              case Success(img) =>
                rec.dispatch(PhotoRecorder.Preview(img))

              case Failure(ex) =>
                Console.err.println(s"Photo failed: ${ex.getMessage}")
            }
            Thread.sleep(previewDlyMS)
          }
        }
      }
    }

    import Main.ec
    p.start()
  }
}
