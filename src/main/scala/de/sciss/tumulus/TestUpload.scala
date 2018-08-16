/*
 *  TestUpload.scala
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

import de.sciss.file._

import scala.sys.process._
import scala.util.Try

object TestUpload {
  def apply()(implicit config: Config): Unit = {
    val fOut = File.createTemp(suffix = ".jpg")
    val cmdPhoto = Seq(
      "raspistill", "-o", fOut.path, "-t", "1", "-q", "100", "-awb", "off", "-drc", "off", "-ISO", "400", "-awbg",
      "1.5,1,1.2", "-ss", "4000", "--rotation", "180"
    )
    val codePhoto = Try(cmdPhoto.!).getOrElse(-1)
    if (codePhoto == 0) {
      Main.setStatus("photo taken")
    } else {
      Main.setStatus(s"raspistill failed ($codePhoto)")
    }

    val cmdUpload = Seq(
      "scp",
      fOut.path,
      s"${config.sftpUser}@${config.sftpHost}:"
    )
    val codeUpload = Try(cmdUpload.!).getOrElse(-1)
    if (codeUpload == 0) {
      Main.setStatus("photo uploaded")
    } else {
      Main.setStatus(s"scp failed ($codeUpload)")
    }
  }
}
