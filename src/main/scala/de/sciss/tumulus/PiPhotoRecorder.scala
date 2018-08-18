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

  def settings: PhotoSettings = synchronized(_settings)

  def settings_=(s: PhotoSettings): Unit = synchronized {
    _settings = s
  }

  private def updateCam(set: PhotoSettings): Unit = {
    cam.setISO(set.iso)
    cam.setShutter(1000000 / set.shutterHz)
    cam.setAWBGains(set.redGain, set.blueGain)
  }

  protected def takePhoto(): MetaImage = {
    val set = settings
    updateCam(set)
    val img = cam.takeBufferedStill()
    MetaImage(img, set)
  }
}
