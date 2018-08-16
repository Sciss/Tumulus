package de.sciss.tumulus

import java.awt.EventQueue

import scala.swing.Button

object UI {
  def mkButton(name: String)(action: => Unit): Button = {
    val b = Button(name)(action)
    val d = b.preferredSize
    d.height = math.max(d.height, 48)
    b.preferredSize = d
    b
  }

  def requireEDT(): Unit =
    require(EventQueue.isDispatchThread)
}
