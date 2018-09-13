package de.sciss.tumulus

import java.awt.image.BufferedImage
import java.awt.{Color, EventQueue, Font}

import de.sciss.swingplus.GridPanel
import javax.imageio.ImageIO
import javax.swing.{ImageIcon, Timer, WindowConstants}

import scala.swing.event.{ButtonClicked, UIElementHidden, UIElementShown}
import scala.swing.{Button, Component, Frame, Label, Swing, TextField, ToggleButton}
import scala.util.Try

object UI {
  def launchUIWithJack(mkWindow: => Unit)(implicit config: ConfigLike, main: MainLike): Unit = {
    //    Submin.install(config.dark)
    Swing.onEDT {
      if (config.isLaptop) launch(mkWindow, None)
      else prelude(mkWindow)
    }
  }

  private def launch(mkWindow: => Unit, toDispose: Option[Frame])(implicit config: ConfigLike): Unit = {
    def openWindow()(implicit config: ConfigLike): Unit = {
      toDispose.foreach(_.dispose())
      mkWindow
    }

    if (config.qJackCtl) {
      import scala.sys.process._
      Try(Process("qjackctl", Nil).run())
      val hasDly = config.qJackCtlDly > 0
      if (hasDly) {
        println(s"Waiting ${config.qJackCtlDly}s for qJackCtl to launch...")
        val th = new Thread {
          override def run(): Unit = synchronized(wait())
        }
        th.start()
        val t = new Timer(config.qJackCtlDly * 1000, Swing.ActionListener { _ =>
          openWindow()
          th.synchronized(th.notify())
        })
        t.setRepeats(false)
        t.start()

      } else {
        openWindow()
      }
    } else {
      openWindow()
    }
  }

  private def prelude(mkWindow: => Unit)(implicit config: ConfigLike, main: MainLike): Unit = {
    var remain = 5
    val lb = new Label

    def updateLb(): Unit =
      lb.text = s"Launching in ${remain}s..."

    updateLb()

    def closeFrame(): Unit = {
      remainT.stop()
      // do not call 'dispose' because the JVM will exit
      // when the swing timer is started
      f.visible = false // f.dispose()
    }

    lazy val remainT: Timer = new Timer(1000, Swing.ActionListener { _ =>
      remain -= 1
      if (remain == 0) {
        closeFrame()
        launch(mkWindow, Some(f))
      } else {
        updateLb()
      }
    })

    lazy val ggAbort: Button = UI.mkButton("Abort") {
      closeFrame()
      sys.exit()
    }

    lazy val f: Frame = new Frame {
      title = main.name
      contents = new GridPanel(2, 1) {
        contents += lb
        contents += ggAbort
        border = Swing.EmptyBorder(16, 64, 16, 64)
      }
      peer.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE)
    }

    f.pack().centerOnScreen()
    f.open()

    remainT.setRepeats(true)
    remainT.start()
  }

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

  def deferIfNeeded(thunk: => Unit): Unit =
    if (EventQueue.isDispatchThread) thunk
    else Swing.onEDT(thunk)

  private def getImageResource(name: String): BufferedImage = {
    val is = UI.getClass.getResourceAsStream(s"/$name")
    val image = if (is != null) {
      val res = ImageIO.read(is)
      is.close()
      res
    } else {
      val res = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
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
