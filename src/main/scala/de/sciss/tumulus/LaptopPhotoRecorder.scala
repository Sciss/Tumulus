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

import java.awt.image.BufferedImage
import java.io.IOException

import de.sciss.file._
import de.sciss.tumulus.impl.CmdLinePhotoRecorder
import javax.imageio.ImageIO

import scala.sys.process.Process

class LaptopPhotoRecorder(var settings: PhotoSettings)(implicit config: Config)
  extends CmdLinePhotoRecorder { rec =>

  def takePhoto(): BufferedImage = {
    val fTmp = File.createTemp(suffix = ".jpg")
    try {
      val cmd = "fswebcam"
      val args = List("-q", "-r", "1280x720", "--crop", "960x720", "--no-banner", "--jpeg", "100", "-D", "1",
        fTmp.path)
      val code = Process(cmd, args).!
      if (code == 0) {
        ImageIO.read(fTmp)
      } else {
        throw new IOException(s"$cmd returned with code $code")
      }
    } finally {
      fTmp.delete()
    }
  }
}
