/*
 *  PhotoComponent.scala
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

import java.awt.Color
import java.awt.image.BufferedImage

import scala.swing.{Component, Dimension, Graphics2D}

class PhotoComponent(rec: PhotoRecorder) extends Component {
  private[this] var _image = Option.empty[BufferedImage]

  preferredSize = new Dimension(320, 240)
  opaque        = true

  rec.addListener {
    case PhotoRecorder.Preview(img) =>
      _image = Some(img)
      if (showing) repaint()
  }

  def image: Option[BufferedImage] = _image

  override protected def paintComponent(g: Graphics2D): Unit = {
    g.setColor(Color.black)
    val p   = peer
    val w   = p.getWidth
    val h   = p.getHeight
    g.fillRect(0, 0, w, h)
    _image.foreach { img =>
      val w1  = h * 4/3
      val h1  = w * 3/4
      val wi  = math.min(w, w1)
      val hi  = math.min(h, h1)
      val x   = (w - wi)/2
      val y   = (h - hi)/2
      g.drawImage(img, x, y, wi, hi, p)
    }
  }
}
