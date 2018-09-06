package de.sciss.tumulus

import de.sciss.file._
import javax.imageio.ImageIO

object WBTest {
  def main(args: Array[String]): Unit = {
    val img   = ImageIO.read(file("/data/temp/overexposed.jpg"))
    for (i <- 0 until 2) {
      val t0    = System.currentTimeMillis()
      val gains = WhiteBalance.analyze(img)
      val t1    = System.currentTimeMillis()
      if (i == 1) {
        println(gains)
        println(s"Took ${t1-t0}ms.")
      }
    }
  }
}
