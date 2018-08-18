/*
 *  PhotoSettings.scala
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

import java.io.{FileInputStream, FileOutputStream}
import java.util.Properties

import de.sciss.file._
import de.sciss.tumulus.Main.settingsDir

import scala.util.Try

object PhotoSettings {
  private def f = settingsDir / "photo.properties"

  private final val KeyShutter  = "shutter"
  private final val KeyIso      = "iso"
  private final val KeyGain     = "gain"
  private final val KeyCrop     = "crop"

  /** From slow to fast */
  val StandardShutter: List[Int] = List(
    4, 8, 16, 32, 64, 128, 256
  )

  val StandardIso: List[Int] = List(100, 200, 400, 800)
}
/** @param shutterHz  shutter speed in 1/s
  * @param iso        ISO value (valid are 100, 200, 400, 800)
  * @param redGain    relative red gain (green is 1.0)
  * @param blueGain   relative blue gain (green is 1.0)
  */
case class PhotoSettings(shutterHz: Int = 16, iso: Int = 400, redGain: Float = 1.5f, blueGain: Float = 1.2f,
                         cropTop: Int = 0, cropLeft: Int = 0, cropBottom: Int = 0, cropRight: Int = 0) {
  import PhotoSettings._

  def save(): Unit = {
    val p = new Properties
    p.put(KeyShutter, shutterHz .toString)
    p.put(KeyIso    , iso       .toString)
    val sGain = f"$redGain%g,$blueGain%g"
    p.put(KeyGain, sGain)
    val sCrop = s"$cropTop,$cropLeft,$cropBottom,$cropRight"
    p.put(KeyCrop, sCrop)
    f.parent.mkdirs()
    val fos = new FileOutputStream(f)
    try {
      p.store(fos, null)
    } finally {
      fos.close()
    }
  }

  def load(): PhotoSettings = {
    val p = new Properties
    val fis = new FileInputStream(f)
    try {
      p.load(fis)
    } finally {
      fis.close()
    }

    val shutTr      = Try(p.getProperty(KeyShutter).toInt)
    val newShutter  = shutTr.getOrElse(shutterHz)
    val isoTr       = Try(p.getProperty(KeyIso    ).toInt)
    val newIso      = isoTr .getOrElse(iso)
    val gainTr = Try {
      val s = p.getProperty(KeyGain)
      val a = s.split(",")
      (a(0).toFloat, a(1).toFloat)
    }
    val (newRed, newBlue) = gainTr.getOrElse((redGain, blueGain))
    val cropTr = Try {
      val s = p.getProperty(KeyCrop)
      val a = s.split(",")
      (a(0).toInt, a(1).toInt, a(2).toInt, a(3).toInt)
    }
    val (newCT, newCL, newCB, newCR) = cropTr.getOrElse((cropTop, cropLeft, cropBottom, cropRight))

    copy(shutterHz = newShutter, iso = newIso, redGain = newRed, blueGain = newBlue,
      cropTop = newCT, cropLeft = newCL, cropBottom = newCB, cropRight = newCR)
  }

  def resetCrop: PhotoSettings = copy(cropTop = 0, cropLeft = 0, cropBottom = 0, cropRight = 0)
}
