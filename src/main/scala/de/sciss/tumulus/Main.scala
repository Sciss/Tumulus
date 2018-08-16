/*
 *  Main.scala
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

import de.sciss.model.Model
import de.sciss.model.impl.ModelImpl
import de.sciss.submin.Submin

import scala.swing.Swing
import scala.util.control.NonFatal

object Main  {
  private object _status extends ModelImpl[String] {
    def fire(s: String): Unit = dispatch(s)
  }

  def status: Model[String] = _status

  def setStatus(s: String): Unit = _status.fire(s)

  private def buildInfString(key: String): String = try {
    val clazz = Class.forName("de.sciss.tumulus.BuildInfo")
    val m     = clazz.getMethod(key)
    m.invoke(null).toString
  } catch {
    case NonFatal(_) => "?"
  }

  final def name        : String = "Tumulus"
  final def version     : String = buildInfString("version")
  final def builtAt     : String = buildInfString("builtAtString")
  final def fullVersion : String = s"v$version, built $builtAt"

  def main(args: Array[String]): Unit = {
    val default = Config()

    val p = new scopt.OptionParser[Config](s"Tumulus $fullVersion") {
      opt[String]('u', "sftp-user")
        .text("SFTP user name")
        .action { (v, c) => c.copy(sftpUser = v) }

      opt[String]('h', "sftp-host")
        .text(s"SFTP host name (default: ${default.sftpHost})")
        .action { (v, c) => c.copy(sftpHost = v) }

//      opt[Seq[String]]('s', "start")
//        .text("List of names of start objects in workspace's root directory")
//        .action { (v, c) => c.copy(startObjects = v.toList) }

      opt[Unit]("bright-ui")
        .text("Use a bright UI")
        .action { (_, c) => c.copy(dark = false) }

      opt[Unit]("no-fullscreen")
        .text("Start in non-fullscreen mode")
        .action { (_, c) => c.copy(fullScreen = false) }
    }
    p.parse(args, default).fold(sys.exit(1)) { implicit config =>
      run()
    }
  }

  def run()(implicit config: Config): Unit = {
    Submin.install(config.dark)
    Swing.onEDT {
      val w = new MainWindow
      if (config.fullScreen) {
        w.fullscreen = true
      } else {
        w.centerOnScreen()
        w.open()
      }
    }
  }

  def exit(): Unit =
    sys.exit()
}
