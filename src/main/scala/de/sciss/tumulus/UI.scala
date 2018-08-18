/*
 *  UI.scala
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

import java.awt.{Color, EventQueue, Font}
import java.awt.image.BufferedImage

import de.sciss.swingplus.GridPanel
import javax.imageio.ImageIO
import javax.swing.ImageIcon

import scala.swing.event.{ButtonClicked, UIElementHidden, UIElementShown}
import scala.swing.{Button, Component, Label, TextField, ToggleButton}

object UI {
  def mkBoldLabel(text: String): Component =
    new Label(s"<html><body><b>$text</b></body>")

  def mkBackPane(card: String)(action: => Unit): Component = {
    val b     = mkButton("Back")(action)
    val title = mkBoldLabel(card)
    new GridPanel(1, 2) {
      contents += b
      contents += title
    }
  }

  def whenShown(c: Component, listen: Boolean = true)(action: => Unit): Unit = {
    if (listen) c.listenTo(c)
    c.reactions += {
      case UIElementShown(_) => action
    }
  }

  def whenShownAndHidden(c: Component, listen: Boolean = true)(shown: => Unit)(hidden: => Unit): Unit = {
    if (listen) c.listenTo(c)
    c.reactions += {
      case UIElementShown (_) => shown
      case UIElementHidden(_) => hidden
    }
  }

  final val RowHeight = 64

  def mkButton(text: String)(action: => Unit): Button = {
    val b = Button(text)(action)
    val d = b.preferredSize
    d.height = math.max(d.height, RowHeight)
    b.preferredSize = d
    b
  }

  def mkToggleButton(text: String)(action: Boolean => Unit): ToggleButton = {
    val b = new ToggleButton(text)
    b.listenTo(b)
    b.reactions += {
      case ButtonClicked(_) => action(b.selected)
    }
    val d = b.preferredSize
    d.height = math.max(d.height, RowHeight)
    b.preferredSize = d
    b
  }

  def mkInfoLabel(text: String): TextField = {
    val c = new TextField(text, 12)
    c.editable  = false
    c.focusable = false
    c
  }

  def requireEDT(): Unit =
    require(EventQueue.isDispatchThread)

  private def getImageResource(name: String): BufferedImage = {
    val is = UI.getClass.getResourceAsStream(s"/$name")
    val image = if (is != null) {
      val res = ImageIO.read(is)
      is.close()
      res
    } else {
      val res = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB)
      val g2  = res.createGraphics()
      g2.setColor(Color.white)
      g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18))
      g2.drawString("?", 4, 16)
      g2.dispose()
      res
    }
    image
  }

  def getIconResource(name: String): ImageIcon = {
    val image = getImageResource(name)
    new ImageIcon(image)
  }
}
