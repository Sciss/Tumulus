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
import de.sciss.lucre.stm.TxnLike.peer
import de.sciss.lucre.synth.{Buffer, Server, Synth, Txn}
import de.sciss.numbers.Implicits._
import de.sciss.synth.proc.AuralSystem
import de.sciss.synth.{SynthGraph, addToHead, addToTail, ugen}
import de.sciss.tumulus.Light
import de.sciss.tumulus.sound.Main.{atomic, backupDir, renderDir}

import scala.concurrent.stm.{Ref, TSet}

object Player {
  def resonanceFile   (base: String): File = renderDir / s"$base-resonance.aif"
  def colorsFile      (base: String): File = renderDir / s"$base-colors.aif"
  def okFile          (base: String): File = renderDir / s"$base.ok"

//  def bakResonanceFile(base: String): File = backupDir / resonanceFile(base).name
//  def bakColorsFile   (base: String): File = backupDir / colorsFile   (base).name
//  def bakOkFile       (base: String): File = backupDir / okFile       (base).name

  def inBackup(f: File): File = backupDir / f.name

  def startMaster(s: Server)(implicit tx: Txn, config: Config): Unit = {
    val g = SynthGraph {
      import de.sciss.synth.Ops.stringToControl
      import ugen._
      val in    = In.ar(0, config.numChannels)
      val amp   = "master-amp".kr(config.masterGainDb.dbAmp)
      val sig0  = in * amp
      val sig1  = if (config.masterHPF < 50) sig0 else HPF.ar(sig0, config.masterHPF)
      val sig   = Limiter.ar(sig1, level = config.masterLimiterDb.dbAmp, dur = 0.1)
      ReplaceOut.ar(0, sig)
    }
    Synth.playOnce(g, nameHint = Some("master"))(target = s.defaultGroup, addAction = addToTail)
  }

  def setMasterVolume(as: AuralSystem, ampLin: Double)(implicit tx: Txn): Unit =
    as.serverOption.foreach { s =>
      s.defaultGroup.set("master-amp" -> ampLin)
    }

  final case class Entry(base: String, fResonance: File, fColors: File, colors: Vec[Int]) {
    def fOk: File = okFile(base)

    private def moveToBackup(f: File): Unit = {
      val ok = f.renameTo(inBackup(f))
      if (!ok) f.delete()
    }

    def moveToBackup(): Unit = {
      moveToBackup(fOk        )
      moveToBackup(fResonance )
      moveToBackup(fColors    )
    }
  }

  def scan()(implicit config: Config): Vec[Entry] = {
    val all = renderDir.children(_.ext == "ok").flatMap { fOk =>
      val base        = fOk.base
      val fResonance  = resonanceFile (base)
      val fColors     = colorsFile    (base)
      if (fResonance.isFile && fColors.isFile) {
        val colors = PixelRenderer.readColors(fColors)
        Some(Entry(base, fResonance = fResonance, fColors = fColors, colors = colors))
      } else {
        None
      }
    }
    all.sortBy(_.base)
  }

  def scanAndClean()(implicit config: Config): Vec[Entry] = {
    val all = scan()
    if (all.size <= config.maxPoolSize) all else {
      val (drop, keep) = all.splitAt(config.maxPoolSize)
      drop.foreach(_.moveToBackup())
      keep
    }
  }

  def apply(as: AuralSystem, light: Light)(implicit config: Config): Player = {
    val list0 = scanAndClean()
    val res   = new Impl(as, light, list0, config)
    atomic { implicit tx =>
      as.addClientNow(new ServerUser("player") {
        def booted(s: Server)(implicit tx: Txn): Unit = {
          tx.afterCommit(println("Server booted (Player)."))
          res.start(s)
        }
      })
    }
    res
  }

  private final class Impl(as: AuralSystem, light: Light, list0: Vec[Entry], config: Config) extends Player {
    private[this] val listRef = Ref(list0)
    private[this] val inUse   = TSet.empty[Entry]
    private[this] val synSet  = TSet.empty[Synth]
    private[this] val index   = Ref((math.random() * list0.size).toInt)
    private[this] val running = Ref(false)

    private[this] val g = SynthGraph {
      import de.sciss.synth.Ops.stringToControl
      import ugen._
      val buf   = "buf".ir
      val disk  = DiskIn.ar(numChannels = 1, buf = buf, loop = 0)
      FreeSelfWhenDone.kr(disk)
      val amp   = "amp".kr
      val sig   = disk * amp
      val out   = "out".kr
      Out.ar(out, sig)
    }

    def poolSize(implicit tx: Txn): Int = listRef().size

    def inject(e: Entry)(implicit tx: Txn): Unit = {
      val newList = listRef.transformAndGet(_ :+ e)
      if (newList.size > config.maxPoolSize) {
        val head +: tail = newList
        listRef() = tail
        index.transform(i => (i + 1) % newList.size)
        if (!inUse.contains(head)) {
          tx.afterCommit(head.moveToBackup())
        }
      }
      if (synSet.isEmpty && running()) as.serverOption.foreach(start)
    }

    def start(s: Server)(implicit tx: Txn): Unit = {
      tx.afterCommit(log("start player"))
      running() = true
      iterate(s)
    }

    def stop()(implicit tx: Txn): Unit = {
      running() = false
      synSet.foreach(_.dispose())
      synSet.clear()
    }

    private def log(what: => String): Unit =
      if (config.verbose) println(s"[log] $what")

    private def iterate(s: Server)(implicit tx: Txn): Unit = {
      val list    = listRef()
      if (list.isEmpty) return

      val idx0    = index()
      val e0      = list(idx0)
      val numSyn  = math.min(config.numChannels, list.size)
      synSet.foreach(_.dispose())
      synSet.clear()

      for (ch <- 0 until numSyn) {
        val idx     = (idx0 + ch) % list.size
//        val idx     = (idx0 + 0) % list.size  // try unison for more volume
        val e       = list(idx)
        val ampDb   = if (ch < config.chanAmpsDb.size) config.chanAmpsDb(ch) else -60.0
        val ampVal  = ampDb.dbAmp
        val buf     = Buffer.diskIn(s)(e.fResonance.path)
        val syn     = Synth.play(g, nameHint = Some("resonance"))(target = s.defaultGroup,
          args = List("buf" -> buf.id, "amp" -> ampVal, "out" -> ch), addAction = addToHead,
          dependencies = buf :: Nil)
        syn.onEndTxn { implicit tx =>
          buf.dispose()
          if (synSet.remove(syn) && synSet.isEmpty && running()) iterate(s)
        }
        synSet.add(syn)
      }

      val idx1  = (idx0 + 1) % list.size
      index()   = idx1

      val msg = s"Iterate $idx0 - ${e0.base}"
      tx.afterCommit {
        val colors = e0.colors
        // println(colors)
        light.setRGB(colors)
        Main.setStatus(msg)
        log(msg)
      }
    }
  }
}
trait Player {
  def poolSize(implicit tx: Txn): Int

  def inject(e: Player.Entry)(implicit tx: Txn): Unit

  def start(s: Server)(implicit tx: Txn): Unit

  def stop()(implicit tx: Txn): Unit
}
