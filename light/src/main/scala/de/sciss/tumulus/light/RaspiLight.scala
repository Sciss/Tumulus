/*
 *  RaspiLight.scala
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

import de.cacodaemon.rpiws28114j.{WS2811, WS2811Channel}
import de.sciss.kollflitz.Vec

object RaspiLight {
  def apply()(implicit config: Config): Light = new Impl(config)

  private final class Impl(config: Config) extends Light {
    private[this] val ok: Boolean = try {
      if (config.verbose) {
        println(s"WS2811Channel(/* GPIO = */ ${config.ledGPIO}, /* LED count = */ ${config.ledCount}, ${config.ledStripType}, /* invert = */ ${config.ledInvertSignal}, /* brightness = */ ${config.ledBrightness})")
      }
      WS2811.init(new WS2811Channel(/* GPIO */ config.ledGPIO, /* LED count */ config.ledCount,
        config.ledStripType, /* invert */ config.ledInvertSignal, /* brightness */ config.ledBrightness))
      println("Light initialized.")
      true
    } catch {
      case e: Exception =>
        println("!! Failed to initialize WS2811:")
        e.printStackTrace()
        false
    }

    def setRGB(xs: Vec[Int]): Unit =
      if (ok) try {
//        if (config.verbose) {
//          println(xs.iterator.map(_.toHexString).mkString(s"setPixel(0, 0x", ", 0x", ")"))
//        }
        WS2811.setPixel(0, xs: _*)
        WS2811.render()
      } catch {
        case _: Exception =>
          println("!! Failed to set RGB.")
      }
  }
}
