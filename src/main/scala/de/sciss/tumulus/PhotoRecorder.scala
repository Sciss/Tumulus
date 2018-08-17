/*
 *  PhotoRecorder.scala
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

import java.awt.image.BufferedImage

import de.sciss.model.Model

object PhotoRecorder {
  sealed trait Update
  case object Booted extends Update
  case class Preview(img: BufferedImage) extends Update

  def apply()(implicit config: Config): PhotoRecorder =
    if (config.isLaptop)  new LaptopPhotoRecorder
    else                  new PiPhotoRecorder
}
trait PhotoRecorder extends Model[PhotoRecorder.Update] {
  def boot(): Unit

  def booted: Boolean
}
