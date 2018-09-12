/*
 *  LaptopLight.scala
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

import java.awt.Color

import de.sciss.kollflitz.Vec
import javax.swing.WindowConstants

import scala.swing.{Component, Dimension, Frame, Graphics2D, GridPanel, Swing}

object LaptopLight {
  def apply()(implicit config: Config): Light = new Impl(config)

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

  private final class Impl(config: Config) extends Light {
    private[this] var leds: Vec[LED] = _

    Swing.onEDT {
      leds = Vector.fill(config.ledCount)(new LED)
      new Frame {
        title = "LEDs"
        contents = new GridPanel(config.ledGroups, config.ledPerGroup) {
          hGap        = 4
          vGap        = 4
          border      = Swing.EmptyBorder(4)
          contents  ++= leds
        }
        resizable = false
        pack().centerOnScreen()
        open()

        peer.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE /* DO_NOTHING_ON_CLOSE */)
      }
    }

    def setRGB(xs: Vec[Int]): Unit = Swing.onEDT {
      (leds.iterator zip xs.iterator).foreach {
        case (led, rgb) =>
          led.setRGB(rgb)
      }
    }
  }
}
