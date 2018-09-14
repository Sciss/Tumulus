/*
 *  MainWindow.scala
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

package de.sciss.tumulus.sound

import de.sciss.kollflitz.Vec
import de.sciss.osc
import de.sciss.tumulus.Light

import scala.util.control.NonFatal

class LightDispatch(oscT: osc.UDP.Transmitter.Undirected)(implicit config: Config)
  extends Light {

  @volatile
  private[this] var _view: Light = _

  def setView(view: Light): Unit =
    _view = view

  private def normalize(in: Vec[Int]): Vec[Int] = {
    var maxR  = 0
    var maxG  = 0
    var maxB  = 0
    in.foreach { i =>
      val r = (i >> 16) & 0xFF
      val g = (i >>  8) & 0xFF
      val b =  i        & 0xFF
      if (r > maxR) maxR = r
      if (g > maxG) maxG = g
      if (b > maxB) maxB = b
    }

    val max = math.max(maxR, math.max(maxG, maxB))
    if (max == 0xFF || max == 0x00) in else {
      val gain0 = 255.0 / max
      val gain  = if (config.ledNormPow == 1.0) gain0 else math.pow(gain0, config.ledNormPow)
      in.map { i  =>
        val r = (((i >> 16) & 0xFF) * gain + 0.5).toInt
        val g = (((i >>  8) & 0xFF) * gain + 0.5).toInt
        val b = (((i >>  0) & 0xFF) * gain + 0.5).toInt
        (r << 16) | (g << 8) | b
      }
    }
  }

  def setRGB(xs: Vec[Int]): Unit = {
    val xsN = normalize(xs)
    val m   = osc.Message("/led", xsN: _*)
    try {
      oscT.send(m, config.lightSocket)
      val v = _view
      if (v != null) v.setRGB(xsN)

    } catch {
      case NonFatal(ex) =>
        println("!! Light dispatch failed:")
        ex.printStackTrace()
    }
  }
}
