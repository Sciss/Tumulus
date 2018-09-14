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

package de.sciss.tumulus.sound

import java.net.InetSocketAddress

import de.sciss.kollflitz.Vec
import de.sciss.tumulus.{ConfigLike, IO}

final case class Config(isLaptop          : Boolean   = false,
                        sftpUser          : String    = "",
                        sftpPass          : String    = "",
                        sftpHost          : String    = "ssh.strato.de",
                        sftpFinger        : String    = "70:87:7d:47:68:6d:b6:b3:bc:1c:3f:1d:d8:a5:d2:2d",
                        sftpDebDir        : String    = "deb",
                        verbose           : Boolean   = false,
                        jackName          : String    = "Tumulus",
                        qJackCtl          : Boolean   = true,
                        qJackCtlDly       : Int       = 6,
                        errorInterval     : Int       = 300,
                        numChannels       : Int       = 12,
                        ledGroups         : Int       = 4,
                        ledPerGroup       : Int       = 19,
                        lightHost         : String    = IO.defaultLightHost,
                        lightPort         : Int       = IO.defaultLightPort,
                        ownSocket         : Option[InetSocketAddress] = None,
                        chanAmpsDb        : Vec[Double] = Vec(-24.0, -24.0, -24.0, -24.0, -24.0, -24.0, -20.0, -18.0, -18.0, -20.0, -16.0, -18.0),
                        boostLimDb        : Double    = 18.0,
                        splLoud           : Double    = 55.0,
                        refLoud           : Double    = 42.0,
                        highPassHz        : Double    = 150.0,
                        photoThreshPerc   : Double    = 0.7,
                        photoThreshFactor : Double    = 0.7,
                        maxPoolSize       : Int       = 360, // 180,
                        masterGainDb      : Double    = -10.5,
                        masterLimiterDb   : Double    = -28.0,
                        ledGainRed        : Double    = 1.0,
                        ledGainGreen      : Double    = 0.75,
                        ledGainBlue       : Double    = 0.56,
                        noDownloads       : Boolean   = false,
                        ledNormPow        : Double    = 0.5,
                        soundStopWeekdays : TimeOfDay = TimeOfDay(20, 0),
                        soundStopWeekend  : TimeOfDay = TimeOfDay(20, 0),
                        lightStopWeekdays : TimeOfDay = TimeOfDay(22, 0),
                        lightStopWeekend  : TimeOfDay = TimeOfDay(22, 0),
                        noSchedule        : Boolean   = false,
) extends ConfigLike {

  def sftpAddress(path: String): String = s"$sftpUser@$sftpHost:$path"

  lazy val lightSocket = new InetSocketAddress(lightHost, lightPort)
}
