/*
 *  MainWindow.scala
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

import java.awt.Toolkit
import java.awt.event.{ActionEvent, InputEvent, KeyEvent}

import javax.swing.{AbstractAction, JComponent, KeyStroke}

import scala.swing.{BorderPanel, Button, Component, Dimension, Frame, GridPanel, Label, Swing}
import scala.util.Try
import scala.util.control.NonFatal

object MainWindow {
  final val CardHome      = "Home"
  final val CardUpdate    = "Update"
  final val CardWifi      = "Wi-Fi Settings"
  final val CardRecorder  = "Recorder"
  final val CardCalibrate = "Calibrate"
}
class MainWindow(implicit config: Config) extends Frame { win =>
  import MainWindow._

  private[this] var fsState = false

  private[this] val lbVersion = UI.mkBoldLabel(Main.name)

  private[this] val ggStatus  = UI.mkInfoLabel("Ready.")

  private[this] val pUpdate = new UpdatePanel(win)

  private def tryMkPanel(id: String, p: => Component): Component =
    try {
      p
    } catch {
      case NonFatal(ex) =>
        new GridPanel(0, 1) {
          contents += new Label(s"Failed to create $id")
          contents += new Label(ex.getClass.getName)
          contents += new Label(ex.getMessage)
        }
    }

  private[this] val photoRecorderOpt = Try(PhotoRecorder()).toOption

  private[this] val pRecorder   = tryMkPanel(CardRecorder , new RecorderPanel (win, photoRecorderOpt.get))
  private[this] val pCalibrate  = tryMkPanel(CardCalibrate, new CalibratePanel(win, photoRecorderOpt.get))
  private[this] val pWifi       = tryMkPanel(CardWifi     , new WifiPanel     (win))

  private[this] val pHome = new GridPanel(0, 1)

  private[this] val cards = new CardPanel {
    add(CardHome, pHome)
  }

  private def mkCardButton(id: String, panel: Component) = {
    cards.add(id, panel)
    Button(id)(cards.show(id))
  }

  private[this] val ggUpdate    = mkCardButton(CardUpdate   , pUpdate   )
  private[this] val ggWifi      = mkCardButton(CardWifi     , pWifi     )
  private[this] val ggRecorder  = mkCardButton(CardRecorder , pRecorder )
  private[this] val ggCalibrate = mkCardButton(CardCalibrate, pCalibrate)

  private[this] val ggShutdown = Button("Shutdown") {
    Main.shutdown()
  }

  title     = Main.name

  // leave it resizable because Pi might make window 320x480 including
  // title bar, so when going full-screen, it won't extend the contents
  // panel to that size
//  resizable = false

  pHome.contents ++= Seq(
    lbVersion,
    ggRecorder,
    ggCalibrate,
    new Label,
    ggUpdate,
    ggWifi,
    ggShutdown
  )

  contents = new BorderPanel {
    add(cards   , BorderPanel.Position.Center)
    add(ggStatus, BorderPanel.Position.South)
    preferredSize = new Dimension(320, 480)
  }

  installFullscreenKey()

  Main.status.addListener {
    case u => Swing.onEDT(ggStatus.text = u)
  }

  override def closeOperation(): Unit =
    Main.exit()

  def home(): Unit = cards.first()

  private def installFullscreenKey(): Unit = {
    val display = peer.getRootPane
    val iMap    = display.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
    val aMap    = display.getActionMap

    val fsName  = "fullscreen"
    iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit.getMenuShortcutKeyMask |
      InputEvent.SHIFT_MASK), fsName)
    aMap.put(fsName, new AbstractAction(fsName) {
      def actionPerformed(e: ActionEvent): Unit = {
        fullscreen = !fullscreen
      }
    })
  }

  def fullscreen: Boolean = fsState

  def fullscreen_=(value: Boolean): Unit = if (fsState != value) {
    fsState = value
    val frame = peer
    val gc = frame.getGraphicsConfiguration
    val sd = gc.getDevice
    sd.setFullScreenWindow(if (value) frame else null)
  }
}
