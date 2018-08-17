package de.sciss.tumulus

import java.awt.EventQueue

import de.sciss.swingplus.GridPanel

import scala.swing.event.UIElementShown
import scala.swing.{Button, Component, Label, TextField}

object UI {
  def mkBackPane(card: String)(action: => Unit): Component = {
    val b     = mkButton("Back")(action)
    val title = new Label(s"<html><body><b>$card</b></body>")
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

  def mkButton(text: String)(action: => Unit): Button = {
    val b = Button(text)(action)
    val d = b.preferredSize
    d.height = math.max(d.height, 64)
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
}
