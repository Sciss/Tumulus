package de.sciss.tumulus

import java.awt.CardLayout

import scala.swing.{Component, Panel}

// XXX TODO submit this to scala-swing
class CardPanel extends Panel {
  private[this] lazy val layoutManager = new CardLayout

  override lazy val peer = new javax.swing.JPanel(layoutManager) with SuperMixin

  def vGap: Int = layoutManager.getVgap
  def vGap_=(n: Int): Unit = layoutManager.setVgap(n)
  def hGap: Int = layoutManager.getHgap
  def hGap_=(n: Int): Unit = layoutManager.setHgap(n)

  def add(name: String, c: Component): Unit =
    peer.add(name, c.peer)

  def first   (): Unit = layoutManager.first   (peer)
  def next    (): Unit = layoutManager.next    (peer)
  def previous(): Unit = layoutManager.previous(peer)
  def last    (): Unit = layoutManager.last    (peer)

  def show(name: String): Unit = layoutManager.show(peer, name)
}

