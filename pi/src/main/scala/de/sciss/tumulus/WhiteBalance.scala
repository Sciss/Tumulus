/*
 *  WhiteBalance.scala
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

import de.sciss.numbers

/** Automatic white balance estimation based on histogram stretching.
  *
  * cf. https://docs.gimp.org/en/gimp-layer-white-balance.html
  *
  * Original author: Vladimira Hezelova (BSD 3-clause license),
  * see https://github.com/VladimiraHezelova/WhiteBalance/
  *
  * Translated and adapted from Java to Scala, from Android to AWT.
  * We don't calculate the dark bits, but instead return estimated
  * gain factors for red and blue.
  */
object WhiteBalance {
  case class Gains(red: Float, blue: Float) {
    override def toString: String = {
      import numbers.Implicits._
      f"[red: ${red.ampDb}%1.1f dB, blue: ${blue.ampDb}%1.1f dB]"
    }
  }

  /** Estimates the red and blue gains relative to green.
    *
    * @param img        the image to analyze for white balance
    * @param percentile the percentile at which to clip the histogram (default: 5%)
    * @param clip       if `true`, clips the gain values to the interval (0.5, 2.0)
    * @return   gain parameters if input data is valid, `None` if input image is too bright
    */
  def analyze(img: BufferedImage, percentile: Double = 0.05, clip: Boolean = true): Option[Gains] = {
//    val low         = new Array[Int](3)
//    val high        = new Array[Int](3)
    val ratio       = new Array[Float](3)
    val histogram   = mkHistogram(img)
    // 5 percent of the number of pixels
    val p           = (img.getWidth * img.getHeight * percentile).asInstanceOf[Int]
    var intensity   = 0
    var count       = 0
    var ch          = 0
    while (ch < 3) {
//      intensity = 0
//      count     = 0
//      while (count < percentile) {
//        count     += histogram(ch)(intensity)
//        intensity += 1
//      }
//      low(ch)   = intensity - 1
      intensity = 255
      count     = 0
      while (count < p) {
        count     += histogram(ch)(intensity)
        intensity -= 1
      }
      val high = intensity + 1
      ratio(ch) = 255f / high

      ch += 1
    }

    val rr    = ratio(0)
    val rg    = ratio(1)
    val rb    = ratio(2)
    if (rr == 1 || rg == 1 || rb == 1) None else {
      val rrN   = rr / rg
      val rbN   = rb / rg
      import numbers.Implicits._
      val red   = if (clip) rrN.clip(0.5f, 2.0f) else rrN
      val blue  = if (clip) rbN.clip(0.5f, 2.0f) else rbN
      Some(Gains(red = red, blue = blue))
    }
  }

  /* Creates RGB histogram
   *
   * @return histogram [channel][intensity <0.255>] = number of pixels
   */
  private def mkHistogram(img: BufferedImage): Array[Array[Int]] = {
    val histogram = Array.ofDim[Int](3, 256)
    val hr        = histogram(0)
    val hg        = histogram(1)
    val hb        = histogram(2)

    var value     = 0
    val height    = img.getHeight
    val width     = img.getWidth
    var y = 0
    while (y < height) {
      var x = 0
      while (x < width) {
        value = img.getRGB(x, y)
        val r = (value >> 16) & 0xFF
        val g = (value >>  8) & 0xFF
        val b =  value        & 0xFF

        hr(r) += 1
        hg(g) += 1
        hb(b) += 1

        x += 1
      }

      y += 1
    }
    histogram
  }

//  /** Applies white balance correction to a pixel.
//    *
//    * @param pixelData array with three values (channels)
//    */
//  def balance(pixelData: Array[Float]): Unit = {
//    var ch = 0
//    while (ch < 3) {
//      val d = pixelData(ch)
//      if      (d < low (ch)) pixelData(ch) = 0f
//      else if (d > high(ch)) pixelData(ch) = 255f
//      else                   pixelData(ch) = (d - low(ch)) * ratio(ch)
//
//      ch += 1
//    }
//  }
}
