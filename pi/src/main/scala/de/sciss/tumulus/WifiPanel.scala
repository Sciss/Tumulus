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

import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

import de.sciss.equal.Implicits._
import de.sciss.file._
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

object WifiPanel {
  def interface(implicit config: Config): String = if (config.isLaptop) "wlp1s0" else "wlan0"
}
class WifiPanel(w: MainWindow)(implicit config: Config)
  extends BorderPanel {

  import WifiPanel.interface

  /** @param ssid       network id
    * @param strength   1 (very weak) to 8 (very strong), -1 for unknown
    */
  case class Network(ssid: String, strength: Int) {
    override def toString: String = {
      val strengthS = if (strength < 1 || strength > 8) '\u2581' else (0x2590 - strength).toChar
      s"$strengthS $ssid"
    }
  }

  case class NetworkEntry(ssid: String, priority: Int, lines: List[String]) {
    def format: String =
      lines.mkString(
        s"""network={
           |	ssid="$ssid"
           |	priority=$priority
           |	""".stripMargin, "\n\t", "\n}\n\n")
  }

  object Supplicant {
    private val path = "/etc/wpa_supplicant/wpa_supplicant.conf"

    def read(): Supplicant = {
      val (code, s) = IO.sudo("cat", path :: Nil)
      if (code == 0) parse(s) else sys.error("Could not read configuration")
    }

    def remove(ssid: String): Unit = {
      val before  = read()
      val now     = before.copy(entries = before.entries.filterNot(_.ssid === ssid))
      if (now != before) {
        now.write()
      }
    }

    def add(ssid: String, password: String): Unit = {
      // XXX TODO: how should these be escaped?
      require (!password.contains("\""), sys.error("Password cannot contain quotation marks"))
      val before  = read()
      val rem     = before.copy(entries = before.entries.filterNot(_.ssid === ssid))
      val pri     = if (rem.entries.isEmpty) 0 else rem.entries.map(_.priority).max + 1
      val lines   = if (password.isEmpty) {
        "key_mgmt=NONE" :: Nil
      } else {
        s"""psk="$password"""" :: s"""password="$password"""" :: Nil
      }
      val e       = NetworkEntry(ssid = ssid, priority = pri, lines = lines)
      val now     = rem.copy(entries = e :: rem.entries)
      if (now != before) {
        now.write()
      }
    }

    def parse(s: String, clipPriority: Boolean = true): Supplicant = {
      val linesB    = List.newBuilder[String]
      val entriesB  = List.newBuilder[NetworkEntry]
      var eLinesB   = List.newBuilder[String]

      var wasEmpty  = false
      var inEntry   = false
      var ssid      = null: String
      var priority  = 0

      s.split("\n").foreach { ln =>
        val t       = ln.trim
        val isEmpty = t.isEmpty
        if (!(isEmpty && wasEmpty)) {
          if (inEntry) {
            if (t.startsWith("}")) {
              if (ssid != null) {
                val eLines  = eLinesB.result()
                val e       = NetworkEntry(ssid, priority, eLines)
                entriesB += e
              }
              inEntry     = false
            } else if (t.startsWith("ssid=")) {
              val i = t.indexOf("\"") + 1
              val j = t.lastIndexOf("\"")
              if (j >= i) {
                ssid = t.substring(i, j)
              }

            } else if (t.startsWith("priority=")) {
              Try {
                priority = t.substring(9).toInt
                if (clipPriority && priority > 20) priority = 20
              }

            } else {
              eLinesB += t
            }

          } else {
            if (t.startsWith("network={")) {
              inEntry   = true
              eLinesB   = List.newBuilder[String]
              ssid      = null
              priority  = 0
            } else {
              linesB += ln
            }
          }
          wasEmpty = isEmpty
        }
      }

      val lines   = linesB  .result()
      val entries = entriesB.result()
      Supplicant(lines, entries)
    }
  }
  case class Supplicant(lines: List[String], entries: List[NetworkEntry]) {
    def format: String =
      lines.mkString("", "\n", entries.map(_.format).mkString("\n\n", "", ""))

    def write(): Unit = {
      val fTmp = File.createTemp()
      val os = new FileOutputStream(fTmp)
      try {
        os.write(format.getBytes("UTF-8"))
      } finally {
        os.close()
      }
      IO.sudo("cp", fTmp.path :: Supplicant.path :: Nil)
    }
  }

  private[this] var _scanning     = false
  private[this] var _prepareCon   = false
  private[this] var _prepareSSID  = ""
  private[this] var hasScanned    = false

  private[this] var currentSSID   = Option.empty[String]
  private[this] var contacted     = false
  private[this] var available     = List.empty[Network]

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
    if (_prepareCon) {
      leaveConnect()
      Main.setStatus("")
    } else {
      disconnect()
    }
  }

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

  private def tryReconfigureWPA(): Boolean = {
    import sys.process._
    val code  = Try(Seq("wpa_cli", "-i", interface, "reconfigure").!).getOrElse(-1)
    val ok    = code === 0
    if (!ok) {
      Main.setStatus("Could not reconfigure WPA client!")
    }
    ok
  }

  private def connect(): Unit = {
    if (!_prepareCon || _scanning) return
    if (config.isLaptop) {
      Main.setStatus("Not implemented on laptop: Connect")
      leaveConnect()
    } else {
      val pass  = ggPass.text
      val tr    = Try(Supplicant.add(ssid = _prepareSSID, password = pass))
      val ok    = tr match {
        case Success(_) =>
          tryReconfigureWPA() && {
            val codeCon = Try {
              IO.sudo("iw", List("dev", interface, "connect", _prepareSSID))._1
            } .getOrElse(-1)
            val okCon = codeCon === 0
            if (!okCon) {
              Main.setStatus("Could not connect Wi-Fi!")
            }
            okCon
          }

        case Failure(ex) =>
          Main.setStatus(s"Could not add network! ${ex.getMessage}")
          false
      }
      leaveConnect()
      if (ok) scan(delay = 8)
    }
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

  private def leaveConnect(): Unit = {
    _prepareCon = false
    updateAll()
  }

  private def disconnect(): Unit = {
    if (_scanning) return

    if (config.isLaptop) {
      Main.setStatus("Not implemented on laptop: Disconnect")
    } else {
      currentSSID.foreach { ssid =>
        val tr = Try(Supplicant.remove(ssid))
        val ok = tr match {
          case Success(_) =>
            tryReconfigureWPA() && {
              val codeCon = Try {
                IO.sudo("iw", List("dev", interface, "disconnect"))._1
              } .getOrElse(-1)
              val okCon = codeCon === 0
              if (!okCon) {
                Main.setStatus("Could not disconnect Wi-Fi!")
              }
              okCon
            }

          case Failure(ex) =>
            Main.setStatus(s"Could not remove network! ${ex.getMessage}")
            false
        }
        if (ok) scan(delay = 4)
      }
    }
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
          lbConnected.text  = s" $id${if (contacted) "" else " (no route!)"}"
          lbConnected.icon  = iconWifi

        case None =>
          lbConnected.icon  = null
          lbConnected.text  = "Not connected."
      }
    }
  }

  private def updateButtons(): Unit = {
    updateCanScan()
    updateCanConnect()
    updateCanDisconnect()
  }

  def scan(delay: Int = 0): Unit = {
    UI.requireEDT()
    if (_scanning || _prepareCon) return

    hasScanned      = true
    _scanning       = true
    updateButtons()
    Main.setStatus("Scanning for Wi-Fi networks...")

    import Main.ec
    val fut: Future[(List[Network], Option[String], Boolean)] = Future {
      val peer = Future {
        blocking {
          if (delay > 0) Thread.sleep(delay * 1000)
          val _avail    = findAvailableNetworks()
          val _current  = findCurrentSSID()
          val _contact  = _current.isDefined && contactSFTP()
          (_avail, _current, _contact)
        }
      }
      Await.result(peer, Duration(1, TimeUnit.MINUTES))
    }
    fut.onComplete { tr =>
      Swing.onEDT {
        _scanning = false

        tr match {
          case Success((_avail, _current, _contact)) =>
            available         = _avail.sortBy(n => (-n.strength, n.ssid.toUpperCase))
            currentSSID       = _current
            contacted         = _contact
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

  def contactSFTP(): Boolean = {
    import sys.process._
//    println("---1")
    val code = Try(Process("ping", List("-c", "1", "-w", "8", "-I", interface, "-q", config.sftpHost)).!).getOrElse(-1)
//    println("---2")
    code == 0
  }
}
