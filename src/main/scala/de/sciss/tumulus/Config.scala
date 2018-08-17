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

package de.sciss.tumulus

/** @param isLaptop     if `true`, assume we launch from laptop not raspberry pi
  * @param sftpUser     user name for SFTP. If empty, will be tried to retrieve via
  *                     `~/.tumulus/sftp.properties`.
  * @param sftpPass     password (clear) for SFTP. If empty, will be tried to retrieve via
  *                     `~/.tumulus/sftp.properties`.
  * @param sftpHost     host name of SFTP server
  * @param sftpFinger   finger print of SFTP server. if empty, accepts any
  * @param sftpDebDir   Debian package sub-directory of SFTP server
  * @param dark         if `true`, use dark colour scheme for UI
  * @param fullScreen   if `true`, put UI in full-screen mode
  * @param verbose      if `true`, print additional debug logging messages
  * @param jackName     jack audio client name
  * @param audioDur     audio chunk duration in seconds
  * @param hpf          audio high pass filter frequency in Hz
  * @param audioMonitor if `true`, pass audio mic input to output for monitoring
  * @param qJackCtl     if `true`, launch qJackCtl upon start
  * @param qJackCtlDly  delay in seconds to wait after starting qJackCtl
  */
final case class Config(isLaptop    : Boolean = false,
                        sftpUser    : String  = "",
                        sftpPass    : String  = "",
                        sftpHost    : String  = "ssh.strato.de",
                        sftpFinger  : String  = "70:87:7d:47:68:6d:b6:b3:bc:1c:3f:1d:d8:a5:d2:2d",
                        sftpDebDir  : String  = "deb",
                        dark        : Boolean = true,
                        fullScreen  : Boolean = true,
                        verbose     : Boolean = false,
                        jackName    : String  = "Tumulus",
                        audioDur    : Double  = 12.0, //  * 12,
                        hpf         : Double  = 80.0,
                        audioMonitor: Boolean = true,
                        qJackCtl    : Boolean = true,
                        qJackCtlDly : Int     = 6
                       ) {

  def sftpAddress(path: String): String = s"$sftpUser@$sftpHost:$path"
}
