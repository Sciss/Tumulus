val cfg = osc.UDP.Config()
cfg.localAddress = "192.168.0.77"
val t = osc.UDP.Transmitter(cfg)
t.connect()
t.localPort
t.isConnected
t.dump()
val tgt = new java.net.InetSocketAddress("192.168.0.25", 0x4C69)
t.send(osc.Message("/foo"), tgt)
t.send(osc.Message("/led", 0x080808, 0x080808, 0x080808, 0xFFFFFF), tgt)
t.send(osc.Message("/led", 0x080007, 0x080407, 0x080607, 0x00A0), tgt)

t.send(osc.Message("/led", 0xFF0000, 0x00FF00, 0x0000FF, 0x1F1F1F, 0x3F3F3F, 0x5F5F5F, 0x7F7F7F, 0x9F9F9F, 0xBFBFBF, 0xDFDFDF, 0xFFFFFF), tgt) 
t.send(osc.Message("/led", 0,0,0,0,0,0,0,0,0,0,0), tgt)

val gainRed   = 1.0
val gainGreen = 0.75 // 0.7267
val gainBlue  = 0.56 // 0.4851

def set(rgb: Int*): Unit = {
  val eq = rgb.map { in =>
    val r = (((in & 0xFF0000) >> 16).toDouble * gainRed  ).round.toInt
    val g = (((in & 0x00FF00) >>  8).toDouble * gainGreen).round.toInt
    val b = (((in & 0x0000FF) >>  0).toDouble * gainBlue ).round.toInt
    val out = (r << 16) | (g << 8) | b
    out
  }
  t.send(osc.Message("/led", eq: _*), tgt)
}

set(0xFF0000, 0x00FF00, 0x0000FF, 0x1F1F1F, 0x3F3F3F, 0x5F5F5F, 0x7F7F7F, 0x9F9F9F, 0xBFBFBF, 0xDFDFDF, 0xFFFFFF)

def clear() = set(List.fill(76)(0): _*)

clear()

0.08627 .reciprocal * 0.04313
0.57647 .reciprocal * 0.43137
0.77647 .reciprocal * 0.43137

val test = List(0xaccbe8, 0xafccea, 0xabc9e5, 0xa9cbe7, 0xadcbe7, 0xacc9e7, 0xadc8e3, 0xafccea, 0xa6c7e6,
                0xaccae6, 0xaccae6, 0xafceeb, 0xadcbe7, 0xaac9e6, 0xaccbe8, 0xaccae6, 0xafcde9, 0xacc7e2)

set(Vector.fill(4)(test).flatten: _*)

clear()

///////////

// val afIn = io.AudioFile.openRead("/data/temp/rec180912_142157-colors.aif")
val afIn = io.AudioFile.openRead("/data/temp/rec180912_181311-colors.aif")
val colors = try {
  val buf = afIn.buffer(76)
  afIn.read(buf)
  Vector.tabulate(76) { i =>
    val red   = (buf(0)(i) * 255 + 0.5).toInt
    val green = (buf(1)(i) * 255 + 0.5).toInt
    val blue  = (buf(2)(i) * 255 + 0.5).toInt
    (red << 16) | (green << 8) | blue
  }  
} finally {
  afIn.close()
}

def normalize(in: Vector[Int]): Vector[Int] = {
  val maxRed   = in.iterator.map(i => (i >> 16) & 0xFF).max
  val maxGreen = in.iterator.map(i => (i >>  8) & 0xFF).max
  val maxBlue  = in.iterator.map(i => (i >>  0) & 0xFF).max
  val max = math.max(maxRed, math.max(maxGreen, maxBlue))
  if (max == 0xFF || max == 0x00) in else {
    val gain = 255.0 / max
    in.map { i  =>
      val red   = (((i >> 16) & 0xFF) * gain + 0.5).toInt
      val green = (((i >>  8) & 0xFF) * gain + 0.5).toInt
      val blue  = (((i >>  0) & 0xFF) * gain + 0.5).toInt
      (red << 16) | (green << 8) | blue
    }
  }
}

// normalize(colors).map(_.toHexString)

set(colors: _*)
set(normalize(colors): _*)

def halfNorm(in: Vector[Int]): Vector[Int] = {
  val maxRed   = in.iterator.map(i => (i >> 16) & 0xFF).max
  val maxGreen = in.iterator.map(i => (i >>  8) & 0xFF).max
  val maxBlue  = in.iterator.map(i => (i >>  0) & 0xFF).max
  val max = math.max(maxRed, math.max(maxGreen, maxBlue))
  if (max == 0xFF || max == 0x00) in else {
    val gain = (255.0 / max).sqrt
    in.map { i  =>
      val red   = (((i >> 16) & 0xFF) * gain + 0.5).toInt
      val green = (((i >>  8) & 0xFF) * gain + 0.5).toInt
      val blue  = (((i >>  0) & 0xFF) * gain + 0.5).toInt
      (red << 16) | (green << 8) | blue
    }
  }
}

set(halfNorm(colors): _*)

clear()
