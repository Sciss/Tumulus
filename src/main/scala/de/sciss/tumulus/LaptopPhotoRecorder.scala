/*
 *  LaptopPhotoRecorder.scala
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

class LaptopPhotoRecorder(implicit config: Config)
  extends CmdLinePhotoRecorder { rec =>

  def takePhoto(fOut: File): Boolean = {
    val cmd = "fswebcam"
    val args = List("-q", "-r", "1280x720", "--crop", "960x720", "--no-banner", "--jpeg", "100", "-D", "1",
      fOut.path)
    val code = Process(cmd, args).!
    code == 0
  }
}
