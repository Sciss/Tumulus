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

import scala.swing.{Button, Frame, GridPanel, Label, Swing}

class MainWindow(config: Config) extends Frame {
  private[this] var fsState = false

  private[this] val lbStatus = new Label(s"${Main.name} ${Main.fullVersion}")

  private[this] val ggTestUpload = Button("Test Upload") {
    TestUpload(config)
  }

  private[this] val ggQuit = Button("Quit") {
    sys.exit()
  }

  private[this] val ggShutdown = Button("Shutdown") {
    import scala.sys.process._
    Seq("sudo", "shutdown", "now").!
  }

  peer.setUndecorated(true)
  contents = new GridPanel(0, 1) {
    contents ++= Seq(
      lbStatus,
      ggTestUpload,
      ggQuit,
      ggShutdown
    )
  }

  installFullscreenKey()

  Main.status.addListener {
    case u => Swing.onEDT(lbStatus.text = u)
  }

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

//    val closeName = "close"
//    iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, Toolkit.getDefaultToolkit.getMenuShortcutKeyMask), closeName)
//    aMap.put(closeName, new AbstractAction(fsName) {
//      def actionPerformed(e: ActionEvent): Unit = {
//      }
//    })
  }

  def fullscreen: Boolean = fsState

  def fullscreen_=(value: Boolean): Unit = if (fsState != value) {
    fsState = value
    val frame = peer
    val gc = frame.getGraphicsConfiguration
    val sd = gc.getDevice
    sd.setFullScreenWindow(if (value) frame else null)
//    // "hide" cursor
//    val cursor = if (value) {
//      val cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
//      frame.getToolkit.createCustomCursor(cursorImg, new Point(0, 0), "blank")
//    } else null
//    frame.setCursor(cursor)
  }
}
