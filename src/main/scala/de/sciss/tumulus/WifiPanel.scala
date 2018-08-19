/*
 *  WifiPanel.scala
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

import java.util.concurrent.TimeUnit

import de.sciss.numbers
import de.sciss.swingplus.ListView
import de.sciss.swingplus.ListView.IntervalMode
import de.sciss.swingplus.event.ListSelectionChanged
import de.sciss.tumulus.UI.{mkBackPane, mkButton}
import de.sciss.virtualkeyboard.VirtualKeyboard

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, blocking}
import scala.swing.ScrollPane.BarPolicy
import scala.swing.{Alignment, BorderPanel, BoxPanel, Button, Component, Dimension, FlowPanel, GridPanel, Label, Orientation, ScrollPane, Swing, TextField}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class WifiPanel(w: MainWindow)(implicit config: Config)
  extends BorderPanel {

  /** @param ssid       network id
    * @param strength   1 (very weak) to 8 (very strong), -1 for unknown
    */
  case class Network(ssid: String, strength: Int) {
    override def toString: String = {
      val strengthS = if (strength < 1 || strength > 8) '\u2581' else (0x2590 - strength).toChar
      s"$strengthS $ssid"
    }
  }

  private[this] var _scanning     = false
  private[this] var _prepareCon   = false
  private[this] var _prepareSSID  = ""
  private[this] var hasScanned    = false

  private[this] var currentSSID = Option.empty[String]
  private[this] var available   = List.empty[Network]

  private[this] val ggBack = mkBackPane("Wi-Fi Settings") {
    w.home()
  }

  private[this] val lbConnected = new Label

  private[this] val ggList = new ListView[Network] {
    visibleRowCount = 4
    selection.intervalMode = IntervalMode.Single
  }

  private[this] val iconWifi = UI.getIconResource("wifi.png")

  private[this] val ggScroll = new ScrollPane(ggList) {
    horizontalScrollBarPolicy = BarPolicy.Never
    peer.putClientProperty("styleId", "undecorated")
  }

  private[this] val ggPass = new TextField(12)

  private[this] val ggScan: Button = mkButton("Scan") {
    scan()
  }

  private[this] val ggConnect: Button = mkButton("Connect") {
    if (_prepareCon) connect() else prepareConnect()
  }

  ggList.listenTo(ggList.selection)
  ggList.reactions += {
    case ListSelectionChanged(_, _, false) => updateCanConnect()
  }

  private[this] val ggDisconnectAbort: Button = mkButton("Disconnect") {
    if (_prepareCon) abortConnect() else disconnect()
  }

  private def interface: String = if (config.isLaptop) "wlp1s0" else "wlan0"

  private[this] val ggKeyboard = Component.wrap(new VirtualKeyboard)
  ggKeyboard.preferredSize = new Dimension(320, 160)

  private[this] val pKeyboard = new BoxPanel(Orientation.Vertical) {
    contents += new FlowPanel(new Label("Enter password:", null, Alignment.Leading))
    contents += ggPass
    contents += ggKeyboard
  }

  add(new GridPanel(0, 1) {
    contents += ggBack
//    contents += new GridPanel(1, 2) {
      contents += lbConnected
//      contents += new Label
//    }
  }, BorderPanel.Position.North)

  add(ggScroll, BorderPanel.Position.Center)

  updateButtons()
  updatePages()

  add(new BoxPanel(Orientation.Vertical) {
    contents += pKeyboard
    contents += new GridPanel(1, 3) {
      contents += ggConnect
      contents += ggDisconnectAbort
      contents += ggScan
    }
  }, BorderPanel.Position.South)

  UI.whenShown(this)(if (!hasScanned) scan())

  private def prepareConnect(): Unit = {
    if (_scanning) return
    ggList.selection.items.headOption.foreach { n =>
      _prepareCon   = true
      _prepareSSID  = n.ssid
      updateAll()
      ggPass.text = ""
      ggPass.requestFocus()
      Main.setStatus("Enter password and press 'connect' again.")
    }
  }

  private def connect(): Unit = {
    if (!_prepareCon || _scanning) return
    abortConnect()
    Main.setStatus("NOT YET IMPLEMENTED: Connect")
  }

  private def updateAll(): Unit = {
    updateButtons()
    updateConnected()
    updatePages()
  }

  private def updatePages(): Unit = {
    ggScroll .visible = !_prepareCon
    pKeyboard.visible = _prepareCon
    revalidate()
    repaint()
  }

  private def abortConnect(): Unit = {
    _prepareCon = false
    updateAll()
    Main.setStatus("")
  }

  private def disconnect(): Unit = {
    if (_scanning) return
    Main.setStatus("NOT YET IMPLEMENTED: Disconnect")
  }

  private def updateCanConnect(): Unit = {
    ggConnect.enabled = ggList.selection.items.nonEmpty && !_scanning
  }

  private def updateCanDisconnect(): Unit = {
    ggDisconnectAbort.text     = if (_prepareCon) "Abort" else "Disconnect"
    ggDisconnectAbort.enabled  = _prepareCon || (currentSSID.isDefined && !_scanning)
  }

  private def updateCanScan(): Unit = {
    ggScan.enabled = !_prepareCon && !_scanning
  }

  private def updateConnected(): Unit = {
    if (_prepareCon) {
      lbConnected.icon      = null
      lbConnected.text      =  _prepareSSID

    } else {
      currentSSID match {
        case Some(id) =>
          lbConnected.text      = id
          lbConnected.icon      = iconWifi

        case None =>
          lbConnected.icon      = null
          lbConnected.text      = "Not connected."
      }
    }
  }

  private def updateButtons(): Unit = {
    updateCanScan()
    updateCanConnect()
    updateCanDisconnect()
  }

  def scan(): Unit = {
    UI.requireEDT()
    if (_scanning || _prepareCon) return

    hasScanned      = true
    _scanning       = true
    updateButtons()
    Main.setStatus("Scanning for Wi-Fi networks...")

    import Main.ec
    val fut: Future[(List[Network], Option[String])] = Future {
      val peer = Future {
        blocking {
          val _avail    = findAvailableNetworks()
          val _current  = findCurrentSSID()
          (_avail, _current)
        }
      }
      Await.result(peer, Duration(1, TimeUnit.MINUTES))
    }
    fut.onComplete { tr =>
      Swing.onEDT {
        _scanning = false

        tr match {
          case Success((_avail, _current)) =>
            available         = _avail.sortBy(n => (-n.strength, n.ssid.toUpperCase))
            currentSSID       = _current
            ggList.items      = available
            Main.setStatus("Scan completed.")

          case Failure(ex) =>
            ex.printStackTrace()
            Main.setStatus(s"Could not scan Wi-Fi! ${ex.getMessage}")
        }

        updateButtons()
        updateConnected()
      }
    }
  }

  def findAvailableNetworks(): List[Network] = {
    Try(IO.sudo("iwlist", interface :: "scan" :: Nil)) match {
      case Success((0, s)) =>
        val lines     = s.split("\n")
        var ssid      = ""
        var strength  = -1

        val b = List.newBuilder[Network]
        val set = mutable.Set.empty[String]

        def flush(): Unit = {
          if (ssid.nonEmpty) {
            if (set.add(ssid)) { // avoid duplicates
              b += Network(ssid, strength)
            }
            ssid      = ""
            strength  = -1
          }
        }

        lines.iterator.filterNot(_.isEmpty).foreach { ln =>
          val t = ln.trim
          if (t.startsWith("Cell ")) {
            flush()
          } else  if (t.startsWith("ESSID:\"") && t.endsWith("\"") ) {
              ssid = t.substring(7, t.length - 1)
          } else if (t.startsWith("Quality=")) {
            val i = t.indexOf(" ")
            if (i > 0) {
              val tt = t.substring(8, i).split('/')
              if (tt.length == 2) {
                try {
                  val num   = tt(0).toInt
                  val den   = tt(1).toInt
                  val frac  = num.toFloat / den
                  import numbers.Implicits._
                  strength = (frac.linLin(0.28f, 1.0f, 1, 8) + 0.5).toInt.clip(1, 8)
//                  println(s"num $num, den $den, frac $frac, strength $strength")
                } catch {
                  case NonFatal(_) =>
                }
              }
//              strength = Try {
//                val db = t.substring(7, t.length - 3).toDouble.dbAmp
//                (db.linLin(-48, -80, 8, 1) + 0.5).toInt.clip(8, 1)
//              } .getOrElse(-1)
            }
          }
        }

        flush()
        b.result()

      case _ => Nil
    }
  }

  def findCurrentSSID(): Option[String] = {
    Try(IO.sudo("iwgetid", "--raw" :: Nil)) match {
      case Success((0, s))  => Some(s.trim)  // remove trailing newline
      case _                => None
    }
  }
}
