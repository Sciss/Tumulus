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

package de.sciss.tumulus.light

import java.net.{InetAddress, InetSocketAddress}

import de.sciss.osc
import de.sciss.tumulus.MainLike

import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

object Main extends MainLike {

  protected def subModule: String = "Light"

  def setStatus(s: String): Unit =
    println(s"[status] $s")

  def main(args: Array[String]): Unit = {
    val default = Config()

    val p = new scopt.OptionParser[Config](s"Tumulus $fullVersion") {
      opt[Unit]("laptop")
        .text("Run from laptop")
        .action { (_, c) => c.copy(isLaptop = true) }

      opt[Unit] ("keep-energy")
        .text ("Do not turn off energy saving")
        .action { (_, c) => c.copy(disableEnergySaving = false) }

      opt[Unit]('v', "verbose")
        .text("Use verbose logging")
        .action { (_, c) => c.copy(verbose = true) }

      opt[String] ("own-socket")
        .text (s"Override own IP address and port; must be <host>:<port> ")
        .validate { v =>
          parseSocket(v).map(_ => ())
        }
        .action { (v, c) =>
          val addr = parseSocket(v).right.get
          c.copy(ownSocket = Some(addr))
        }

//      opt[Int]("osc-port")
//        .text(s"Open sound control port (default: ${default.oscPort})")
//        .action { (v, c) => c.copy(oscPort = v) }

      opt[Unit] ("dump-osc")
        .text ("Dump incoming OSC messages (for debugging)")
        .action { (_, c) => c.copy(oscDump = true) }

      opt[Int]("led-gpio")
        .text(s"GPIO pin number for LED control (default: ${default.ledGPIO})")
        .validate { v => if (v >= 1 && v <= 255) success else failure("Must be >= 1 and <= 255") }
        .action { (v, c) => c.copy(ledGPIO = v) }

      opt[Int]("led-groups")
        .text(s"Number of logical LED groupings (default: ${default.ledGroups})")
        .validate { v => if (v >= 1 && v <= 255) success else failure("Must be >= 1 and <= 255") }
        .action { (v, c) => c.copy(ledGroups = v) }

      opt[Int]("led-per-group")
        .text(s"Number of LEDs per logical group (default: ${default.ledPerGroup})")
        .validate { v => if (v >= 1 && v <= 255) success else failure("Must be >= 1 and <= 255") }
        .action { (v, c) => c.copy(ledPerGroup = v) }

      opt[String]("led-type")
        .text(s"LED strip type, one of ${Config.StripTypeList} (default: ${Config.stripTypeToString(default.ledStripType)})")
        .validate { v => Try(Config.stringToStripType(v)) match {
          case Success(_) => success
          case Failure(_) => failure(s"Must be one of ${Config.StripTypeList}")
        }}
        .action { (v, c) => c.copy(ledStripType = Config.stringToStripType(v)) }

      opt[Unit]("led-invert")
        .text("Invert LED signal")
        .action { (_, c) => c.copy(ledInvertSignal = true) }

      opt[Int]("led-brightness")
        .text(s"Brightness level of LEDs (default: ${default.ledBrightness})")
        .validate { v => if (v >= 1 && v <= 255) success else failure("Must be >= 1 and <= 255") }
        .action { (v, c) => c.copy(ledBrightness = v) }
    }
    p.parse(args, default).fold(sys.exit(1)) { implicit config =>
      println(s"$name - $fullVersion")

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

//      if (!config.verbose) {
//        sys.props.put("org.slf4j.simpleLogger.defaultLogLevel", "error")
//      }

      val localSocketAddress = mkOwnSocket()
      run(localSocketAddress)
    }
  }

  def run(localSocketAddress: InetSocketAddress)(implicit config: Config): Unit = {
    val oscCfg        = osc.UDP.Config()
//    oscCfg.localPort  = config.oscPort
    oscCfg.localSocketAddress = localSocketAddress
    val rcv           = osc.UDP.Receiver(oscCfg)
    val light         = Light()
    rcv.action        = (p, _) => p match {
      case osc.Message("/led", rest @ _*) =>
        val rgbB = Vector.newBuilder[Int]
        rgbB.sizeHint(rest.size)
        rest.iterator.foreach {
          case i: Int =>
            val j = i & 0xFFFFFF
            rgbB += j
          case _ =>
            println("Malformed /led command!")
        }
        val rgb = rgbB.result()
        light.setRGB(rgb)

      case osc.Message("/shutdown") => shutdown()
      case osc.Message("/reboot")   => reboot()
      case other => println(s"!! Ignoring unknown OSC packet ${other.name}")
    }
    if (config.oscDump) rcv.dump()
    println(s"Setting up OSC receiver at $localSocketAddress")
    try {
      rcv.connect()
    } catch {
      case e: Exception =>
        println("!! Failed to set up OSC receiver:")
        e.printStackTrace()
    }
    println("Ok.")
  }

  def shutdown(): Unit = {
    import sys.process._
    println("SHUTDOWN")
    Seq("sudo", "shutdown", "now").run()
  }

  def reboot(): Unit = {
    import sys.process._
    println("REBOOT")
    Seq("sudo", "reboot", "now").run()
  }


  def parseSocket(s: String): Either[String, InetSocketAddress] = {
    val arr = s.split(':')
    if (arr.length != 2) Left(s"Must be of format <host>:<port>")
    else parseSocket(arr)
  }

  private def parseSocket(arr: Array[String]): Either[String, InetSocketAddress] = {
    val host = arr(0)
    val port = arr(1)
    Try(new InetSocketAddress(host, port.toInt)) match {
      case Success(addr)  => Right(addr)
      case Failure(ex)    => Left(s"Invalid socket address: $host:$port - ${ex.getClass.getSimpleName}")
    }
  }

  def mkOwnSocket()(implicit config: Config): InetSocketAddress = {
    val res = config.ownSocket.getOrElse {
      val host = thisIP()
//      if (!config.isLaptop) {
//        Network.compareIP(host)
//      }
      new InetSocketAddress(host, Config.ClientPort)
    }
    res
  }

  def thisIP(): String = {
    import sys.process._
    // cf. https://unix.stackexchange.com/questions/384135/
    val ifConfig    = Seq("ip", "a", "show", "eth0").!!
    val ifConfigPat = "inet "
    val line        = ifConfig.split("\n").map(_.trim).find(_.startsWith(ifConfigPat)).getOrElse("")
    val i0          = line.indexOf(ifConfigPat)
    val i1          = if (i0 < 0) 0 else i0 + ifConfigPat.length
    val i2          = line.indexOf("/", i1)
    if (i0 < 0 || i2 < 0) {
      val local = InetAddress.getLocalHost.getHostAddress
      Console.err.println(s"No assigned IP4 found in eth0! Falling back to $local")
      local
    } else {
      line.substring(i1, i2)
    }
  }
}
