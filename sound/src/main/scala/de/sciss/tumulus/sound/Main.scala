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

package de.sciss.tumulus.sound

import de.sciss.submin.Submin
import de.sciss.tumulus.{MainLike, SFTP}

import scala.swing.Swing

object Main extends MainLike {

  def setStatus(s: String): Unit = ???

  protected def subModule: String = "Sound"

  def main(args: Array[String]): Unit = {
    val default = Config()

    val p = new scopt.OptionParser[Config](s"$name $fullVersion") {
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

      opt[Unit]("laptop")
        .text("Run from laptop")
        .action { (_, c) => c.copy(isLaptop = true) }

      opt[Unit]('v', "verbose")
        .text("Use verbose logging")
        .action { (_, c) => c.copy(verbose = true) }

      opt[String]("jack-name")
        .text(s"Jack client name (default: ${default.jackName})")
        .action { (v, c) => c.copy(jackName = v) }

      opt[Unit]("no-qjackctl")
        .text("Do not start QJackCtl upon start")
        .action { (_, c) => c.copy(qJackCtl = false) }

      opt[Int]("qjackctl-delay")
        .text(s"Delay in seconds to wait after starting qJackCtl (default: ${default.qJackCtlDly})")
        .validate { v => if (v >= 0) success else failure("qjackctl-delay must be >= 0") }
        .action { (v, c) => c.copy(qJackCtlDly = v) }

      opt[Int]("error-interval")
        .text(s"Interval between attempts when having download errors, in seconds (default: ${default.errorInterval})")
        .validate { v => if (v >= 10) success else failure("errorInterval-interval must be >= 10") }
        .action { (v, c) => c.copy(errorInterval = v) }
    }
    p.parse(args, default).fold(sys.exit(1)) { config0 =>
      val config = SFTP.resolveConfig(config0)((u, p) => config0.copy(sftpUser = u, sftpPass = p))

//      if (config.disableEnergySaving && !config.isLaptop) {
//        import sys.process._
//        try {
//          Seq("xset", "s", "off").!
//          Seq("xset", "-dpms").!
//        } catch {
//          case NonFatal(ex) =>
//            Console.err.println("Cannot disable energy settings")
//            ex.printStackTrace()
//        }
//      }

//      if (!config.verbose) {
//        sys.props.put("org.slf4j.simpleLogger.defaultLogLevel", "error")
//      }

      run()(config)
    }
  }

  def run()(implicit config: Config): Unit = {
    Submin.install(true)
    Swing.onEDT {
//      if (config.isLaptop) launch(None)
//      else prelude()
    }
  }
}
