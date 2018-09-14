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

import java.net.InetSocketAddress

import de.sciss.file._
import de.sciss.lucre.swing.defer
import de.sciss.lucre.synth.{Server, Txn}
import de.sciss.osc
import de.sciss.submin.Submin
import de.sciss.synth.proc.AuralSystem
import de.sciss.tumulus.{MainLike, Network, SFTP, UI}

import scala.concurrent.ExecutionContext
import scala.concurrent.stm.TxnExecutor
import scala.util.control.NonFatal

object Main extends MainLike {
  implicit def ec: ExecutionContext = ExecutionContext.global

  @volatile
  private[this] var mainWindow: MainWindow = _

  def setStatus(s: String): Unit = {
    val w = mainWindow
    if (w == null) println(s"[status] $s")
    else defer {
      w.setStatus(s)
    }
  }

  protected def subModule: String = "Sound"

  def mkDirs(): Unit = {
    downloadDir.mkdirs()
    renderDir  .mkdirs()
    backupDir  .mkdirs()
  }

  def atomic[A](fun: Txn => A): A = {
    TxnExecutor.defaultAtomic { itx =>
      val tx = Txn.wrap(itx)
      fun(tx)
    }
  }

  lazy val baseDir: File = {
    val tmp     = file("/data") / "projects"
    val parent  = if (tmp.isDirectory) tmp else userHome / "Documents" / "projects"
    val res     = parent / "Tumulus"
    require (res.exists(), s"Directory does not exist: $res")
    res
  }

  lazy val downloadDir: File = baseDir / "download"   // as they come from the SFTP server
  lazy val renderDir  : File = baseDir / "render"     // as they come out of fsc
  lazy val backupDir  : File = baseDir / "bak"        // as they are "wiped" from the previous two dirs

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

      opt[Int]("num-channels")
        .text(s"Number of sound channels (default: ${default.numChannels})")
        .validate { v => if (v >= 2) success else failure("num-channels must be >= 2") }
        .action { (v, c) => c.copy(numChannels = v) }

      opt[Int]("led-groups")
        .text(s"Number of logical LED groupings (default: ${default.ledGroups})")
        .validate { v => if (v >= 1 && v <= 255) success else failure("Must be >= 1 and <= 255") }
        .action { (v, c) => c.copy(ledGroups = v) }

      opt[Int]("led-per-group")
        .text(s"Number of LEDs per logical group (default: ${default.ledPerGroup})")
        .validate { v => if (v >= 1 && v <= 255) success else failure("Must be >= 1 and <= 255") }
        .action { (v, c) => c.copy(ledPerGroup = v) }

      opt[String] ("own-socket")
        .text (s"Override own IP address and port; must be <host>:<port> ")
        .validate { v =>
          Network.parseSocket(v).map(_ => ())
        }
        .action { (v, c) =>
          val addr = Network.parseSocket(v).right.get
          c.copy(ownSocket = Some(addr))
        }
    }
    p.parse(args, default).fold(sys.exit(1)) { config0 =>
      implicit val config: Config =
        SFTP.resolveConfig(config0)((u, p) => config0.copy(sftpUser = u, sftpPass = p))

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

      val localSocketAddress = Network.mkOwnSocket(0)
      run(localSocketAddress)
    }
  }

  def booted(s: Server)(implicit tx: Txn, config: Config): Unit = {
    println("TODO: booted")
  }

  def run(localSocketAddress: InetSocketAddress)(implicit config: Config): Unit = {
    Submin.install(true)
    UI.launchUIWithJack(init(localSocketAddress))
  }

  def init(localSocketAddress: InetSocketAddress)(implicit config: Config): Unit = {
    val as                  = AuralSystem()
    val sCfg                = Server.Config()
    sCfg.deviceName         = Some(config.jackName)
    sCfg.inputBusChannels   = 0
    sCfg.outputBusChannels  = config.numChannels

    atomic { implicit tx =>
      as.whenStarted(booted(_))
      as.start(sCfg)
    }

    try {
      DownloadRender()
    } catch {
      case NonFatal(ex) =>
        println("!! Could not launch download-render:")
        ex.printStackTrace()
    }

    val oscTCfg                 = osc.UDP.Config()
    oscTCfg.localSocketAddress  = localSocketAddress
    val oscT                    = osc.UDP.Transmitter(oscTCfg)
    try {
      oscT.connect()
    } catch {
      case NonFatal(ex) =>
        println("!! Could not connect OSC transmitter:")
        ex.printStackTrace()
    }

    mainWindow = new MainWindow(as, oscT)
  }
}
