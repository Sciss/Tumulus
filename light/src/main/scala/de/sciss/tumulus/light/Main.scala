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

import java.net.InetSocketAddress

import de.sciss.osc
import de.sciss.tumulus.{IO, Light, MainLike, Network, ScreenLight}

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

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
          Network.parseSocket(v).map(_ => ())
        }
        .action { (v, c) =>
          val addr = Network.parseSocket(v).right.get
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

      val localSocketAddress = Network.mkOwnSocket(IO.defaultLightPort)
      run(localSocketAddress)
    }
  }

  def run(localSocketAddress: InetSocketAddress)(implicit config: Config): Unit = {
    val oscCfg        = osc.UDP.Config()
//    oscCfg.localPort  = config.oscPort
    oscCfg.localSocketAddress = localSocketAddress
    val rcv           = osc.UDP.Receiver(oscCfg)
    val light: Light  = {
      if (config.isLaptop)  ScreenLight ()
      else                  RaspiLight  ()
    }

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
}
