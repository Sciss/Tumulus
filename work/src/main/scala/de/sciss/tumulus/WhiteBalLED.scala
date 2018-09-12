package de.sciss.tumulus

import de.sciss.file._
import de.sciss.fscape.graph._
import de.sciss.fscape.{GE, Graph, stream}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object WhiteBalLED extends App {
  val fIn = file("/data/projects/Tumulus/materials/leds_grayscale.png")
  val w   = 3416
  val h   = 152
  val frameSize = w * h

  val g = Graph {
    val in        = ImageFileIn(fIn, numChannels = 3)
    val red       = in.out(0)
    val green     = in.out(1)
    val blue      = in.out(2)
//    val redSum    = RunningSum(red  ).last
//    val greenSum  = RunningSum(green).last
//    val blueSum   = RunningSum(blue ).last
//
//    redSum  .poll(0, "red  ") // 31274.92941180001 .reciprocal * 31274.92941180001 = 1.0
//    greenSum.poll(0, "green") // 43036.89803912794 .reciprocal * 31274.92941180001 = 0.7267
//    blueSum .poll(0, "blue ") // 64465.53333347615 .reciprocal * 31274.92941180001 = 0.4851

    def report(sig: GE, name: String) =
      SlidingPercentile(sig, len = frameSize, frac = 0.95).drop(frameSize - 1).poll(0, name)

    report(red  , "red  ") // 0.43137 .reciprocal * 0.43137 = 1.0
    report(green, "green") // 0.57647 .reciprocal * 0.43137 = 0.75
    report(blue , "blue ") // 0.77647 .reciprocal * 0.43137 = 0.56
  }

  val ctrl  = stream.Control()
  ctrl.run(g)
  Await.result(ctrl.status, Duration.Inf)
  println("Done.")
  sys.exit()
}
