val rcvCfg = osc.UDP.Config()
rcvCfg.localSocketAddress = "192.168.0.77" -> 0x4C69
val rcv = osc.UDP.Receiver(rcvCfg)
rcv.dump()
rcv.connect()
