/*
 *  PixelRenderer.scala
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

import de.sciss.file._
import de.sciss.fscape.graph._
import de.sciss.fscape.{GE, Graph, stream}
import de.sciss.kollflitz.Vec
import de.sciss.synth.io.{AudioFile, AudioFileSpec}
import de.sciss.tumulus.PhotoSettings

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

object PixelRenderer {
  def main(args: Array[String]): Unit = {
    test()
  }

  def test(): Unit = {
    //  val fIn       = file("/data/projects/Tumulus/data/rec180912_142157.jpg")
    //  val fIn       = file("/data/projects/Tumulus/data/rec180912_181311.jpg")
    val fIn       = file("/data/projects/Tumulus/data/rec180913_102141.jpg")
    //  val fIn       = file("/data/projects/Tumulus/data/rec180821_081752.jpg")
    val fProp     = fIn.replaceExt("properties")
    val set       = PhotoSettings().loadFrom(fProp)
    val fOutImg   = file("/data/temp") / s"${fIn.base}-crop.png"
    val fOutColor = file("/data/temp") / s"${fIn.base}-colors.aif"
    val specIn    = ImageFile.readSpec(fIn)

    implicit val cfg: Config = Config()

    val fut = run(fIn = fIn, specIn = specIn, fOutColor = fOutColor, photoSettings = set, fOutCrop = Some(fOutImg))
    Await.result(fut, Duration.Inf)
    println("Done.")
    sys.exit()
  }

  def any2stringadd: Any = ()

  def readColors(f: File)(implicit config: Config): Vec[Int] = {
    val vec0 = try {
      val afIn = AudioFile.openRead(f)
      try {
        val n   = math.min(config.ledCount, afIn.numFrames).toInt
        val buf = afIn.buffer(n)
        afIn.read(buf)
        Vector.tabulate(n) { i =>
          val red   = (buf(0)(i) * 255 + 0.5).toInt
          val green = (buf(1)(i) * 255 + 0.5).toInt
          val blue  = (buf(2)(i) * 255 + 0.5).toInt
          (red << 16) | (green << 8) | blue
        }
      } finally {
        afIn.close()
      }

    } catch {
      case NonFatal(ex) =>
        println(s"!! Could not read colors from $f")
        ex.printStackTrace()
        Vector.empty
    }

    if (vec0.size == config.ledCount) vec0 else vec0.padTo(config.ledCount, 0x000000)
  }

  def run(fIn: File, specIn: ImageFile.Spec, photoSettings: PhotoSettings, fOutColor: File,
          fOutCrop: Option[File] = None)(implicit config: Config): Future[Unit] = {

    val widthIn       = specIn.width  // 2592
    val heightIn      = specIn.height // 1944
    val widthOut      = widthIn   - (photoSettings.cropLeft + photoSettings.cropRight )
    val heightOut     = heightIn  - (photoSettings.cropTop  + photoSettings.cropBottom)
    val imageSizeOut  = widthOut * heightOut
    val threshPerc    = config.photoThreshPerc
    val threshFactor  = config.photoThreshFactor
    val ledCount      = config.ledCount

    val g = Graph {
      val in      = ImageFileIn(fIn, numChannels = 3)
      val crop    = AffineTransform2D.translate(in,
        widthIn = widthIn, heightIn = heightIn, widthOut = widthOut, heightOut = heightOut,
        tx = -photoSettings.cropLeft, ty = -photoSettings.cropTop, zeroCrossings = 0)

      val red     = crop.out(0)
      val green   = crop.out(1)
      val blue    = crop.out(2)
      val lum     = red * 0.2126 + green * 0.7152 + blue * 0.0722
      val sorted  = SortWindow(lum, lum, imageSizeOut)
  //    val perc    = 0.3
  //    val thresh  = WindowApply(sorted, size = imageSizeOut, index = imageSizeOut * perc)
      val thresh0 = WindowApply(sorted, size = imageSizeOut, index = imageSizeOut * threshPerc)
  //    thresh0.poll(0, "thresh0")
      val thresh  = thresh0 * threshFactor
      val above   = BufferMemory(lum, imageSizeOut) > thresh

  //    val filter  = Latch(BufferMemory(crop, imageSizeOut), above)
      val specOut = ImageFile.Spec(width = widthOut, height = heightOut, numChannels = 3)
  //    ImageFileOut(filter /* crop */, fOut, specOut)

      val filter  = FilterSeq(BufferMemory(crop, imageSizeOut), above)
  //    ImageFileOut(filter /* crop */, fOut, specOut)

      val filterLen = Length(filter.out(0)).max(ledCount * 2)
  //    filterLen.poll(0, "filterLen")
      val filterPad = BufferMemory(filter, imageSizeOut) ++ DC(Seq[GE](0.5, 0.5, 0.5)).take(ledCount * 2)

      fOutCrop.foreach {fOutImg =>
        ImageFileOut(filterPad /* crop */, fOutImg, specOut)
      }

      val period  = (filterLen / ledCount).floor
      val slid    = SlidingPercentile(filterPad, len = period)

  //    slid.out(0).poll(Metro(period), "red   ")
  //    slid.out(1).poll(Metro(period), "green ")
  //    slid.out(2).poll(Metro(period), "blue  ")

      val samples = WindowApply(slid, size = period, index = period/2)
      AudioFileOut(samples, fOutColor, AudioFileSpec(numChannels = 3, sampleRate = 1.0))
    }

    val fscCfg = stream.Control.Config()
    fscCfg.useAsync = false
    val ctrl = stream.Control(fscCfg)
    ctrl.run(g)
    ctrl.status
  }
}
