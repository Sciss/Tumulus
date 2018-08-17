package de.sciss.tumulus
package impl

import de.sciss.audiowidgets.PeakMeter
import de.sciss.lucre.stm.TxnLike.peer
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{defer, deferTx}
import de.sciss.lucre.synth.{Bus, Server, Synth, Txn}
import de.sciss.osc.Message
import de.sciss.synth
import de.sciss.synth.Ops.stringToControl
import de.sciss.synth.{SynthGraph, addBefore, message}

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.stm.Ref
import scala.swing.{Component, Orientation}

final class MicMeterImpl extends ComponentHolder[Component] {
  private[this] val ref = Ref(Option.empty[Synth])
  private[this] val meter = {
    val m           = new PeakMeter
    m.orientation   = Orientation.Horizontal
    m.numChannels   = 1
    m.caption       = true
    m.borderVisible = true
    m
  }

  def dispose()(implicit tx: Txn): Unit = {
    ref.swap(None).foreach(_.dispose())
    deferTx {
      meter.dispose()
    }
  }

  @inline private[this] def disposeRef(synths: ISeq[Synth])(implicit tx: Txn): Unit =
    synths.foreach(_.dispose())

  component = meter

  def init(s: Server)(implicit tx: Txn): Unit = {
    val bus       = Bus.soundIn(s, 1)
    val target    = s.defaultGroup
    val addAction = addBefore

    val graph = SynthGraph {
      import synth._
      import ugen._
      val sig   = In.ar("bus".ir, bus.numChannels)
      val tr    = Impulse.kr(20)
      val peak  = Peak.kr(sig, tr)
      val rms   = A2K.kr(Lag.ar(sig.squared, 0.1))
      SendReply.kr(tr, Flatten(Zip(peak, rms)), "/$meter")
    }

    val syn = Synth.play(graph, nameHint = Some("meter"))(target = target, addAction = addAction)
    syn.read(bus -> "bus")

    val SynId = syn.peer.id
    val resp = message.Responder.add(syn.server.peer) {
      case Message("/$meter", SynId, _, vals @ _*) =>
        val pairs = vals.asInstanceOf[Seq[Float]].toIndexedSeq
        val time  = System.currentTimeMillis()
        defer {
          meter.update(pairs, 0, time)
        }
    }
    scala.concurrent.stm.Txn.afterRollback(_ => resp.remove())(tx.peer)
    syn.onEnd(resp.remove())

    ref.swap(Some(syn)).foreach(_.dispose())
  }
}
