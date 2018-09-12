/*
 *  Light.scala
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

package de.sciss.tumulus.light

import de.sciss.kollflitz.Vec

object Light {
  def apply()(implicit config: Config): Light =
    if (config.isLaptop)  LaptopLight()
    else                  RaspiLight()
}
trait Light {
  def setRGB(xs: Vec[Int]): Unit
}
