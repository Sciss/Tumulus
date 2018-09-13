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
import semverfi.{SemVersion, Version}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

object Main extends MainLike {

  private object _status extends ModelImpl[String] {
    def fire(s: String): Unit = dispatch(s)
  }

  def status: Model[String] = _status

  def setStatus(s: String): Unit = _status.fire(s)

  protected def subModule: String = "Pi"

  override def name     : String      = "Mexican Tumulus"
  final def debPrefix   : String      = "tumulus-pi"
  final def debSuffix   : String      = ".deb"
  final def semVersion  : SemVersion  = Version(version)

  implicit val ec: ExecutionContext = ExecutionContext.global

  def main(args: Array[String]): Unit = {
    val default = Config()

    val p = new scopt.OptionParser[Config](s"Tumulus $fullVersion") {
      opt[String]('u', "sftp-user")
        .text("SFTP user name")
        .action { (v, c) => c.copy(sftpUser = v) }

      opt[String]('p', "sftp-pass")
        .text("SFTP password")
        .action { (v, c) => c.copy(sftpPass = v) }

      opt[String]('h', "sftp-host")
        .text(s"SFTP host name (default: ${default.sftpHost})")
        .action { (v, c) => c.copy(sftpHost = v) }

      opt[String]('f', "sftp-finger")
        .text(s"SFTP server finger print, empty '' to accept all (default ${default.sftpFinger})")
        .action { (v, c) => c.copy(sftpFinger = v) }

      opt[String]('d', "sftp-deb-dir")
        .text(s"SFTP software sub-directory (default: ${default.sftpDebDir})")
        .action { (v, c) => c.copy(sftpDebDir = v) }

      opt[Unit]("bright-ui")
        .text("Use a bright UI")
        .action { (_, c) => c.copy(dark = false) }

      opt[Unit]("no-fullscreen")
        .text("Start in non-fullscreen mode")
        .action { (_, c) => c.copy(fullScreen = false) }

      opt[Unit]("laptop")
        .text("Run from laptop")
        .action { (_, c) => c.copy(isLaptop = true) }

      opt[Unit] ("keep-energy")
        .text ("Do not turn off energy saving")
        .action { (_, c) => c.copy(disableEnergySaving = false) }

      opt[Unit]('v', "verbose")
        .text("Use verbose logging")
        .action { (_, c) => c.copy(verbose = true) }

      opt[String]("jack-name")
        .text(s"Jack client name (default: ${default.jackName})")
        .action { (v, c) => c.copy(jackName = v) }

      opt[Double]("audio-dur")
        .text(s"Audio chunk duration in seconds (default: ${default.audioDur})")
        .validate { v => if (v >= 1) success else failure("audio-dur must be >= 1") }
        .action { (v, c) => c.copy(audioDur = v) }

      opt[Double]("hpf")
        .text(s"Audio high pass filter frequency in Hz (default: ${default.hpf})")
        .validate { v => if (v >= 16 && v <= 1000) success else failure("hpf must be >= 16 and <= 1000") }
        .action { (v, c) => c.copy(hpf = v) }

      opt[Unit]("no-audio-monitor")
        .text("Do not pass audio mic input to output for monitoring")
        .action { (_, c) => c.copy(audioMonitor = false) }

      opt[Unit]("no-qjackctl")
        .text("Do not start QJackCtl upon start")
        .action { (_, c) => c.copy(qJackCtl = false) }

      opt[Int]("qjackctl-delay")
        .text(s"Delay in seconds to wait after starting qJackCtl (default: ${default.qJackCtlDly})")
        .validate { v => if (v >= 0) success else failure("qjackctl-delay must be >= 0") }
        .action { (v, c) => c.copy(qJackCtlDly = v) }

      opt[Int]("photo-preview-delay")
        .text(s"Interval of preview photo updates in seconds (default: ${default.photoPreviewDly})")
        .validate { v => if (v >= 1) success else failure("photo-preview-delay must be >= 1") }
        .action { (v, c) => c.copy(photoPreviewDly = v) }

      opt[Int]("rec-interval")
        .text(s"Interval between recording/upload iterations in seconds (default: ${default.recInterval})")
        .validate { v => if (v >= 30) success else failure("rec-interval must be >= 30") }
        .action { (v, c) => c.copy(recInterval = v) }
    }
    p.parse(args, default).fold(sys.exit(1)) { config0 =>
      val config = SFTP.resolveConfig(config0)((u, p) => config0.copy(sftpUser = u, sftpPass = p))

      if (config.disableEnergySaving && !config.isLaptop) {
        import sys.process._
        try {
          Seq("xset", "s", "off").!
          Seq("xset", "-dpms").!
        } catch {
          case NonFatal(ex) =>
            Console.err.println("Cannot disable energy settings")
            ex.printStackTrace()
        }
      }

      if (!config.verbose) {
        sys.props.put("org.slf4j.simpleLogger.defaultLogLevel", "error")
      }

      run()(config)
    }
  }

  def run()(implicit config: Config): Unit = {
    Submin.install(config.dark)
    UI.launchUIWithJack {
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

  def reboot(): Unit = {
    import scala.sys.process._
    Seq("sudo", "reboot", "now").!
  }

  def shutdown(): Unit = {
    import scala.sys.process._
    Seq("sudo", "shutdown", "now").!
  }
}
