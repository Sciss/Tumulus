/*
 *  Config.scala
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

import de.cacodaemon.rpiws28114j.StripType
import de.sciss.tumulus.ConfigLike

object Config {
  import StripType._

  val StripTypeList = "RGB, RBG, GRB, GBR, BRG, BGR"

  def stripTypeToString(s: StripType): String = s match {
    case WS2811_STRIP_RGB => "RGB"
    case WS2811_STRIP_RBG => "RBG"
    case WS2811_STRIP_GRB => "GRB"
    case WS2811_STRIP_GBR => "GBR"
    case WS2811_STRIP_BRG => "BRG"
    case WS2811_STRIP_BGR => "BGR"
  }

  def stringToStripType(s: String): StripType = s.toUpperCase match {
    case "RGB" => WS2811_STRIP_RGB
    case "RBG" => WS2811_STRIP_RBG
    case "GRB" => WS2811_STRIP_GRB
    case "GBR" => WS2811_STRIP_GBR
    case "BRG" => WS2811_STRIP_BRG
    case "BGR" => WS2811_STRIP_BGR
  }

  val ClientPort: Int = 0x4C69
}
/** N.B. light does not use SFTP; this is only for compatibility with ConfigLike. */
final case class Config(isLaptop            : Boolean   = false,
                        disableEnergySaving : Boolean   = true,
                        verbose             : Boolean   = false,
                        ownSocket           : Option[InetSocketAddress] = None,
                        oscDump             : Boolean   = false,
                        ledGPIO             : Int       = 18,
                        ledGroups           : Int       = 4,
                        ledPerGroup         : Int       = 19,
                        ledStripType        : StripType = StripType.WS2811_STRIP_GRB,
                        ledInvertSignal     : Boolean   = false,
                        ledBrightness       : Int       = 255,
                        sftpUser            : String    = "",
                        sftpPass            : String    = "",
                        sftpHost            : String    = "ssh.strato.de",
                        sftpFinger          : String    = "70:87:7d:47:68:6d:b6:b3:bc:1c:3f:1d:d8:a5:d2:2d",
                       ) extends ConfigLike {

  val ledCount: Int = ledGroups * ledPerGroup
}