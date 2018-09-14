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
import de.sciss.processor.Processor
import de.sciss.submin.Submin
import de.sciss.synth.proc.AuralSystem
import de.sciss.tumulus.{MainLike, Network, SFTP, UI}

import scala.concurrent.ExecutionContext
import scala.concurrent.stm.TxnExecutor
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object Main extends MainLike {
  implicit def ec: ExecutionContext = ExecutionContext.global

  @volatile
  private[this] var mainWindow: MainWindow = _

  @volatile
  private[this] var oscT: osc.UDP.Transmitter.Undirected = _

  def setStatus(s: String): Unit = {
    val w = mainWindow
    if (w == null) println(s"[status] $s")
    else defer {
      try {
        w.setStatus(s)
      } catch {
        case _: Exception =>
      }
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

      opt[Seq[Double]]("chan-amp")
        .text (s"Peak channel amplitudes in decibels, (default: ${default.chanAmpsDb.mkString(",")})")
        .action { (v, c) => c.copy(chanAmpsDb = v.toVector) }

      opt[Double]("limiter-boost")
        .text(s"Limiter boost in decibels (default: ${default.boostLimDb})")
        .action { (v, c) => c.copy(boostLimDb = v) }

      opt[Double]("spl-loudness")
        .text(s"Assumes SPL for loudness calculation (default: ${default.splLoud})")
        .action { (v, c) => c.copy(splLoud = v) }

      opt[Double]("ref-loudness")
        .text(s"Targeted reference loudness for sound rendering (default: ${default.refLoud})")
        .action { (v, c) => c.copy(refLoud = v) }

      opt[Double]("hpf")
        .text(s"High pass filter frequency in Hz (default: ${default.highPassHz})")
        .action { (v, c) => c.copy(highPassHz = v) }

      opt[Double]("photo-percentile")
        .text(s"Photo threshold percentile (0 to 1) (default: ${default.photoThreshPerc})")
        .validate { v => if (v >= 0.0 && v <= 1.0) success else failure("Must be >= 0 and <= 1") }
        .action { (v, c) => c.copy(photoThreshPerc = v) }

      opt[Double]("photo-factor")
        .text(s"Photo threshold comparison factor (default: ${default.photoThreshFactor})")
        .validate { v => if (v > 0.0 && v <= 2.0) success else failure("Must be > 0 and <= 2") }
        .action { (v, c) => c.copy(photoThreshFactor = v) }

      opt[Int]("max-pool-size")
        .text(s"Maximum sound/color pool size (default: ${default.maxPoolSize})")
        .validate { v => if (v >= 1 && v <= 0x7FFFFFFF) success else failure("Must be >= 1") }
        .action { (v, c) => c.copy(maxPoolSize = v) }

      opt[Double]("master-gain")
        .text(s"Master gain setting in decibels (default: ${default.masterGainDb})")
        .validate { v => if (v <= 0.0) success else failure("Must be <= 0") }
        .action { (v, c) => c.copy(masterGainDb = v) }

      opt[Double]("master-limiter")
        .text(s"Master limiter ceiling in dBFS (default: ${default.masterLimiterDb})")
        .validate { v => if (v <= 0.0) success else failure("Must be <= 0") }
        .action { (v, c) => c.copy(masterLimiterDb = v) }

      opt[Double]("led-gain-red")
        .text(s"Linear gain factor for the red LEDs (default: ${default.ledGainRed})")
        .validate { v => if (v > 0.0 && v <= 2.0) success else failure("Must be > 0 and <= 2") }
        .action { (v, c) => c.copy(ledGainRed = v) }

      opt[Double]("led-gain-green")
        .text(s"Linear gain factor for the green LEDs (default: ${default.ledGainGreen})")
        .validate { v => if (v > 0.0 && v <= 2.0) success else failure("Must be > 0 and <= 2") }
        .action { (v, c) => c.copy(ledGainGreen = v) }

      opt[Double]("led-gain-blue")
        .text(s"Linear gain factor for the blue LEDs (default: ${default.ledGainBlue})")
        .validate { v => if (v > 0.0 && v <= 2.0) success else failure("Must be > 0 and <= 2") }
        .action { (v, c) => c.copy(ledGainBlue = v) }

      opt[Unit]("no-downloads")
        .text("Do not start download process")
        .action { (_, c) => c.copy(noDownloads = true) }

      opt[Double]("led-norm-pow")
        .text(s"LED color normalization power factor (default: ${default.ledNormPow})")
        .validate { v => if (v > 0.0 && v <= 1.0) success else failure("Must be > 0 and <= 1") }
        .action { (v, c) => c.copy(ledNormPow = v) }

      opt[TimeOfDay]("stop-sound-weekdays")
        .text(s"Scheduled time to stop sound on weekdays (default: ${default.soundStopWeekdays}")
        .action { (v, c) => c.copy(soundStopWeekdays = v) }

      opt[TimeOfDay]("stop-sound-weekend")
        .text(s"Scheduled time to stop sound on weekdays (default: ${default.soundStopWeekend}")
        .action { (v, c) => c.copy(soundStopWeekend = v) }

      opt[TimeOfDay]("stop-light-weekdays")
        .text(s"Scheduled time to stop light on weekdays (default: ${default.lightStopWeekdays}")
        .action { (v, c) => c.copy(lightStopWeekdays = v) }

      opt[TimeOfDay]("stop-light-weekend")
        .text(s"Scheduled time to stop light on weekdays (default: ${default.lightStopWeekend}")
        .action { (v, c) => c.copy(lightStopWeekend = v) }
    }
    p.parse(args, default).fold(sys.exit(1)) { config0 =>
      implicit val config: Config =
        SFTP.resolveConfig(config0)((u, p) => config0.copy(sftpUser = u, sftpPass = p))

//      if (!config.verbose) {
        sys.props.put("org.slf4j.simpleLogger.defaultLogLevel", "error")
//      }

      val localSocketAddress = Network.mkOwnSocket(0)
      run(localSocketAddress)
    }
  }

  def run(localSocketAddress: InetSocketAddress)(implicit config: Config): Unit = {
    Submin.install(true)
    UI.launchUIWithJack(init(localSocketAddress))
  }

  private def attempt[A](what: String)(thunk: => A): Try[A] =
    try {
      val res = thunk
      Success(res)
    } catch {
      case NonFatal(ex) =>
        println(s"!! Could not $what:")
        ex.printStackTrace()
        Failure(ex)
    }

  def quit(): Unit = sys.exit()

  def shutdownPi()(implicit config: Config): Boolean = sendPiMessage("/shutdown")
  def rebootPi  ()(implicit config: Config): Boolean = sendPiMessage("/reboot")

  def shutdownAll()(implicit config: Config): Unit = {
    shutdownPi()
    shutdownSelf()
  }

  def shutdownSelf(): Unit = {
    import sys.process._
    println("SHUTDOWN")
    Seq("sudo", "shutdown", "now").run()
  }

  def rebootSelf(): Unit = {
    import sys.process._
    println("REBOOT")
    Seq("sudo", "reboot", "now").run()
  }

  def hibernateSelf(): Int = {
    import sys.process._
    println("SUSPEND-HYBRID")
    Seq("sudo", "pm-suspend-hybrid").!
  }

  private def sendPiMessage(cmd: String)(implicit config: Config): Boolean = {
    val _oscT = oscT
    (_oscT != null) && tryPrint({
      _oscT.send(osc.Message(cmd), config.lightSocket)
    }).isSuccess
  }

  def tryPrint[A](body: => A): Try[A] =
    printError(Try(body))

  private def printError[A](tr: Try[A]): Try[A] = {
    tr match {
      case Failure(Processor.Aborted()) =>
      case Failure(ex)                  => ex.printStackTrace()
      case _                            =>
    }
    tr
  }

  def init(localSocketAddress: InetSocketAddress)(implicit config: Config): Unit = {
    val as                  = AuralSystem()
    val sCfg                = Server.Config()
    sCfg.deviceName         = Some(config.jackName)
    sCfg.inputBusChannels   = 0
    sCfg.outputBusChannels  = config.numChannels

    de.sciss.synth.proc.showAuralLog = true

    atomic { implicit tx =>
      as.addClient(new ServerUser("main") {
        def booted(s: Server)(implicit tx: Txn): Unit = {
          tx.afterCommit(println("Server booted (Main)."))
          for (_ <- 0 until 4) s.nextNodeId() // XXX TODO
          Player.startMaster(s)
        }
      })
      as.start(sCfg)
    }

    val oscTCfg                 = osc.UDP.Config()
    oscTCfg.localSocketAddress  = localSocketAddress
    val _oscT                   = osc.UDP.Transmitter(oscTCfg)
    oscT = _oscT

    val light = new LightDispatch(_oscT)

    attempt("connect OSC transmitter")(_oscT.connect())

    val playerTr = attempt("start player")(Player(as, light))

    val downloadOpt = if (config.noDownloads) None else {
      playerTr.flatMap { player =>
        attempt("launch download-render")(DownloadRender(player))
      } .toOption
    }

    val sch = Schedule(as, downloadOpt, playerTr.toOption)

    mainWindow = new MainWindow(as, light, _oscT, sch)
  }
}
