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

import java.awt.image.BufferedImage

import com.hopding.jrpicam.RPiCamera
import com.hopding.jrpicam.enums.AWB
import de.sciss.tumulus.impl.CmdLinePhotoRecorder

class PiPhotoRecorder(private[this] var _settings: PhotoSettings)(implicit config: Config)
  extends CmdLinePhotoRecorder {

  def gainsSupported = true

  private[this] val cam = {
    val c = new RPiCamera
    c.setWidth    (2592)
    c.setHeight   (1944)
    c.setAWB      (AWB.OFF)
    c.setQuality  ( 100)
    c.setRotation ( 180)
    c.setTimeout  (1)
    c.turnOffPreview()
    c
  }

  def settings: PhotoSettings = _settings

  def settings_=(s: PhotoSettings): Unit = {
    _settings = s
    updateCam()
  }

  updateCam()

  private def updateCam(): Unit = {
    cam.setISO(_settings.iso)
    cam.setShutter(1000000 / _settings.shutterHz)
    cam.setAWBGains(_settings.redGain, _settings.blueGain)
  }

  protected def takePhoto(): BufferedImage = {
//    val cmd = "raspistill"
//    val args = List("-n", "-awb", "off", /* "-drc", "off", */ "-ISO", "800", "-awbg", "1.5,1.2", "-ss", "100000",
//      "--rotation", "180", "-q", "100", "-t", "1",
//      "-o", fOut.path)
//    if (config.verbose) println(args.mkString(s"$cmd ", " ", ""))
//    val code = Process(cmd, args).!
//    code == 0

    cam.takeBufferedStill()
  }
}
