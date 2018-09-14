/*
 *  Player.scala
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
import de.sciss.kollflitz.Vec
import de.sciss.lucre.synth.{Server, Txn}
import de.sciss.lucre.stm.TxnLike.peer
import de.sciss.synth.proc.AuralSystem
import de.sciss.tumulus.sound.Main.{atomic, renderDir}

import scala.concurrent.stm.Ref

object Player {
  def resonanceFile(base: String): File = renderDir / s"$base-resonance.aif"
  def colorsFile   (base: String): File = renderDir / s"$base-colors.aif"
  def okFile       (base: String): File = renderDir / s"$base.ok"

  final case class Entry(base: String, fResonance: File, fColors: File)

  def scan(): Vec[Entry] = {
    renderDir.children(_.ext == "ok").flatMap { fOk =>
      val base        = fOk.base
      val fResonance  = resonanceFile (base)
      val fColors     = colorsFile    (base)
      if (fResonance.isFile && fColors.isFile) {
        Some(Entry(base, fResonance = fResonance, fColors = fColors))
      } else {
        None
      }
    }
  }

  def apply(as: AuralSystem)(implicit config: Config): Player = {
    val list0 = scan()
    val res   = new Impl(list0, config)
    atomic { implicit tx =>
      as.addClientNow(new AuralSystem.Client {
        def auralStarted(s: Server)(implicit tx: Txn): Unit = res.start(s)

        def auralStopped()(implicit tx: Txn): Unit = ()
      })
    }
    res
  }

  private final class Impl(list0: Vec[Entry], config: Config) extends Player {
    private[this] val listRef = Ref(list0)

    def poolSize(implicit tx: Txn): Int = listRef().size

    def inject(e: Entry)(implicit tx: Txn): Unit = {
      val newList = listRef.transformAndGet(_ :+ e)
    }

    def start(s: Server)(implicit tx: Txn): Unit = {
      ???
    }
  }
}
trait Player {
  def poolSize(implicit tx: Txn): Int

  def inject(e: Player.Entry)(implicit tx: Txn): Unit
}
