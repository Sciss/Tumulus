/*
 *  PiPhotoRecorder.scala
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
import de.sciss.tumulus.impl.CmdLinePhotoRecorder

import scala.sys.process.Process

class PiPhotoRecorder(implicit config: Config) extends CmdLinePhotoRecorder {
  protected def takePhoto(fOut: File): Boolean = {
    val cmd = "raspistill"
    val args = List("-awb", "off", /* "-drc", "off", */ "-ISO", "800", "-awbg", "1.5,1,1.2", "-ss", "100000",
      "--rotation", "180", "-q", "100", "-t", "1",
      "-o", fOut.path)
    if (config.verbose) println(args.mkString(s"$cmd ", " ", ""))
    val code = Process(cmd, args).!
    code == 0
  }
}
