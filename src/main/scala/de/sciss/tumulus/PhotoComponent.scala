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

import java.awt.event.MouseEvent
import java.awt.{Color, Point, RenderingHints}

import javax.swing.event.MouseInputAdapter

import scala.swing.{Component, Dimension, Graphics2D, Insets}

class PhotoComponent(rec: PhotoRecorder) extends Component {
  private[this] var _meta = Option.empty[MetaImage]

  private[this] val _crop = new Insets(0, 0, 0, 0)

  private[this] var _editable = false
  private[this] var _editing  = false

  private[this] var cropEditor: Insets => Unit = _

  private[this] val colrCrop = new Color(0xFF, 0x00, 0x60)

  preferredSize = new Dimension(320, 240)
  opaque        = true

  rec.addListener {
    case PhotoRecorder.Preview(meta) =>
      _meta = Some(meta)
      if (!_editing) {
        val set       = meta.settings
        _crop.top     = set.cropTop
        _crop.left    = set.cropLeft
        _crop.bottom  = set.cropBottom
        _crop.right   = set.cropRight
      }
      if (showing) repaint()
  }

  def image: Option[MetaImage] = _meta

  private[this] object mouseListener extends MouseInputAdapter {
    private[this] val startPoint  = new Point
    private[this] val endPoint    = new Point
    private[this] var hasStarted  = false
    private[this] var hasDragged  = false

    private def setPoint(e: MouseEvent, out: Point): Unit = {
      out.x = e.getX
      out.y = e.getY
    }

    private def updateCrop(): Boolean = {
      val p   = peer
      val w   = p.getWidth
      val h   = p.getHeight
      _meta.exists { meta =>
        val w1        = h * 4/3
        val h1        = w * 3/4
        val wi        = math.min(w, w1)
        val hi        = math.min(h, h1)
        val x         = (w - wi)/2
        val y         = (h - hi)/2
        val img       = meta.img
        val imgW      = img.getWidth
        val imgH      = img.getHeight
        val scale     = math.min(wi.toFloat / imgW, hi.toFloat / imgH)

        val cx1       = math.min(startPoint.x, endPoint.x)
        val cy1       = math.min(startPoint.y, endPoint.y)
        val cx2       = math.max(startPoint.x, endPoint.x)
        val cy2       = math.max(startPoint.y, endPoint.y)

        val cl        = math.max(0, cx1 - x)
        val ct        = math.max(0, cy1 - y)
        val cr        = math.max(0, wi - (cx2 - x))
        val cb        = math.max(0, hi - (cy2 - y))

        _crop.left    = (cl / scale + 0.5f).toInt
        _crop.top     = (ct / scale + 0.5f).toInt
        _crop.right   = (cr / scale + 0.5f).toInt
        _crop.bottom  = (cb / scale + 0.5f).toInt

        true
      }
    }

    override def mousePressed(e: MouseEvent): Unit = {
      setPoint(e, startPoint)
      hasStarted  = true
      hasDragged  = false
    }

    override def mouseReleased(e: MouseEvent): Unit =
      if (hasDragged) {
        hasStarted  = false
        hasDragged  = false
        cropEditor(_crop)
      }

    override def mouseDragged(e: MouseEvent): Unit =
      if (hasStarted) {
        setPoint(e, endPoint)
        if (updateCrop()) {
          hasDragged = true
          repaint()
        }
      }
  }

  def enableCropEditing(fun: Insets => Unit): Unit = {
    cropEditor = fun
    if (!_editable) {
      _editable = true
      peer.addMouseListener      (mouseListener)
      peer.addMouseMotionListener(mouseListener)
    }
  }

  def disableCropEditing(): Unit = if (_editable) {
    _editable = false
    _editing  = false
    peer.removeMouseListener      (mouseListener)
    peer.removeMouseMotionListener(mouseListener)
  }

  override protected def paintComponent(g: Graphics2D): Unit = {
    g.setColor(Color.black)
    val p   = peer
    val w   = p.getWidth
    val h   = p.getHeight
    g.fillRect(0, 0, w, h)
    _meta.foreach { meta =>
      val w1  = h * 4/3
      val h1  = w * 3/4
      val wi  = math.min(w, w1)
      val hi  = math.min(h, h1)
      val x   = (w - wi)/2
      val y   = (h - hi)/2
      val img = meta.img
      g.drawImage(img, x, y, wi, hi, p)

      g.setColor(colrCrop)
      val imgW = img.getWidth
      val imgH = img.getHeight
      val scale = math.min(wi.toFloat / imgW, hi.toFloat / imgH)
      val set = _crop
      val cl = (set.left * scale).toInt
      val ct = (set.top  * scale).toInt
      val cr = ((imgW - set.right ) * scale).toInt
      val cb = ((imgH - set.bottom) * scale).toInt
      val cx = cl + x
      val cy = ct + y
      val cw = cr - cl
      val ch = cb - ct
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.drawRoundRect(cx, cy, cw - 1, ch - 1, 8, 8)
    }
  }
}
