/*
 *  ScreenLight.scala
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

import java.awt.{Color, EventQueue}

import de.sciss.kollflitz.Vec
import javax.swing.WindowConstants

import scala.swing.{Component, Dimension, Frame, Graphics2D, GridPanel, Swing}

object ScreenLight {
  def component()(implicit config: ConfigLike): ScreenLight =
    new ComponentImpl(config)

  def apply()(implicit config: ConfigLike): Light /* ScreenLight */ = new Impl(config)

  private final class LED extends Component {
    preferredSize = new Dimension(20, 20)
    minimumSize   = preferredSize
    maximumSize   = preferredSize
    background    = Color.black
    foreground    = Color.gray

    override protected def paintComponent(g: Graphics2D): Unit = {
      val p = peer
      val w = p.getWidth
      val h = p.getHeight
      g.setColor(p.getBackground)
      g.fillRect(0, 0, w, h)
      g.setColor(p.getForeground)
      g.fillRect(2, 2, w - 4, h - 4)
    }

    def setRGB(i: Int): Unit =
      foreground = new Color(i)
  }

  private final class ComponentImpl(config: ConfigLike) extends GridPanel(config.ledGroups, config.ledPerGroup)
    with ScreenLight {

    def component: Component = this

    private[this] val leds: Vec[LED] = Vector.fill(config.ledCount)(new LED)

    hGap        = 4
    vGap        = 4
    border      = Swing.EmptyBorder(4)
    contents  ++= leds

    private def setRGBNow(xs: Vec[Int]): Unit =
      (leds.iterator zip xs.iterator).foreach {
        case (led, rgb) =>
          led.setRGB(rgb)
      }

    def setRGB(xs: Vec[Int]): Unit =
      if (EventQueue.isDispatchThread) setRGBNow(xs) else Swing.onEDT(setRGBNow(xs))
  }

  private final class Impl(config: ConfigLike) extends Light /* ScreenLight */ {
    private[this] var c: ComponentImpl = _

    Swing.onEDT {
      c = new ComponentImpl(config)
      new Frame {
        title     = "LEDs"
        contents  = c
        resizable = false
        pack().centerOnScreen()
        open()

        peer.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE /* DO_NOTHING_ON_CLOSE */)
      }
    }

//    def component: Component = c

    def setRGB(xs: Vec[Int]): Unit = c.setRGB(xs)
  }
}
trait ScreenLight extends Light {
  def component: Component
}