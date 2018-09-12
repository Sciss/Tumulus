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
val gainGreen = 0.7 // 0.7267
val gainBlue  = 0.5 // 0.4851

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
