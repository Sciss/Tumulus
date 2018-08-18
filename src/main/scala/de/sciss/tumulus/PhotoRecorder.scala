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

import de.sciss.model.Model

import scala.util.Try

object PhotoRecorder {
  sealed trait Update
  case object Booted extends Update
  case class Preview(img: MetaImage) extends Update

  def apply()(implicit config: Config): PhotoRecorder = {
    val set0  = PhotoSettings()
    val set   = Try(set0.load()).getOrElse(set0)
//    if (tr.isFailure) println(tr)
    if (config.isLaptop)  new LaptopPhotoRecorder (set)
    else                  new PiPhotoRecorder     (set)
  }
}
trait PhotoRecorder extends Model[PhotoRecorder.Update] {
  var settings: PhotoSettings

  def boot(): Unit

  def booted: Boolean

  def gainsSupported: Boolean
}
