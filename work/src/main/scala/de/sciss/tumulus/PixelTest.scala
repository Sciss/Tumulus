package de.sciss.tumulus

import de.sciss.file._
import de.sciss.fscape.graph._
import de.sciss.fscape.{GE, Graph, stream}
import de.sciss.synth.io.AudioFileSpec

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object PixelTest extends App {
  def any2stringadd: Any = ()

//  val fIn       = file("/data/projects/Tumulus/data/rec180912_142157.jpg")
  val fIn       = file("/data/projects/Tumulus/data/rec180912_181311.jpg")
//  val fIn       = file("/data/projects/Tumulus/data/rec180821_081752.jpg")
  val fProp     = fIn.replaceExt("properties")
  val set       = PhotoSettings().loadFrom(fProp)
  val fOutImg   = file("/data/temp") / s"${fIn.base}-crop.png"
  val fOutColor = file("/data/temp") / s"${fIn.base}-colors.aif"

  val writeCrop = true
  val widthIn   = 2592
  val heightIn  = 1944
  val widthOut  = widthIn   - (set.cropLeft + set.cropRight )
  val heightOut = heightIn  - (set.cropTop  + set.cropBottom)
  val imageSizeOut  = widthOut * heightOut

  val g = Graph {
    val in      = ImageFileIn(fIn, numChannels = 3)
    val crop    = AffineTransform2D.translate(in,
      widthIn = widthIn, heightIn = heightIn, widthOut = widthOut, heightOut = heightOut,
      tx = -set.cropLeft, ty = -set.cropTop, zeroCrossings = 0)

    val red     = crop.out(0)
    val green   = crop.out(1)
    val blue    = crop.out(2)
    val lum     = red * 0.2126 + green * 0.7152 + blue * 0.0722
    val sorted  = SortWindow(lum, lum, imageSizeOut)
//    val perc    = 0.3
//    val thresh  = WindowApply(sorted, size = imageSizeOut, index = imageSizeOut * perc)
    val thresh0 = WindowApply(sorted, size = imageSizeOut, index = imageSizeOut * 0.7)
//    thresh0.poll(0, "thresh0")
    val thresh  = thresh0 * 0.7
    val above   = BufferMemory(lum, imageSizeOut) > thresh

//    val filter  = Latch(BufferMemory(crop, imageSizeOut), above)
    val specOut = ImageFile.Spec(width = widthOut, height = heightOut, numChannels = 3)
//    ImageFileOut(filter /* crop */, fOut, specOut)

    val filter  = FilterSeq(BufferMemory(crop, imageSizeOut), above)
//    ImageFileOut(filter /* crop */, fOut, specOut)

    val filterLen = Length(filter.out(0)).max(76 * 2)
//    filterLen.poll(0, "filterLen")
    val filterPad = BufferMemory(filter, imageSizeOut) ++ DC(Seq[GE](0.5, 0.5, 0.5)).take(76 * 2)
    if (writeCrop) {
      ImageFileOut(filterPad /* crop */, fOutImg, specOut)
    }

    val period  = (filterLen / 76).floor
    val slid    = SlidingPercentile(filterPad, len = period)

//    slid.out(0).poll(Metro(period), "red   ")
//    slid.out(1).poll(Metro(period), "green ")
//    slid.out(2).poll(Metro(period), "blue  ")

    val samples = WindowApply(slid, size = period, index = period/2)
    AudioFileOut(samples, fOutColor, AudioFileSpec(numChannels = 3, sampleRate = 1.0))
  }

  val config = stream.Control.Config()
  config.useAsync = false
  implicit val ctrl: stream.Control = stream.Control(config)
  ctrl.run(g)
  Await.result(ctrl.status, Duration.Inf)
  println("Done.")
  sys.exit()
}
