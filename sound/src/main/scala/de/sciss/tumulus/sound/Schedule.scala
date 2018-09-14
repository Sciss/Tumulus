/*
 *  Schedule.scala
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

import java.util.concurrent.TimeUnit
import java.util.{Calendar, Date, Timer, TimerTask}

import de.sciss.equal.Implicits._
import de.sciss.synth.proc.AuralSystem
import de.sciss.tumulus.IO
import de.sciss.tumulus.sound.Main.{atomic, tryPrint}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Schedule {
  def apply(as: AuralSystem, download: Option[DownloadRender], player: Option[Player])
           (implicit config: Config): Schedule = new Impl(as, download, player)

  private final class Impl(as: AuralSystem, downloadOpt: Option[DownloadRender], playerOpt: Option[Player])
                          (implicit config: Config)
    extends Schedule {
    private[this] val time0 = Calendar.getInstance()

    val dateStarted: Date = time0.getTime

    private def makeDate(tod: TimeOfDay): Date = {
      val cc = time0.clone().asInstanceOf[Calendar]
      cc.set(Calendar.HOUR_OF_DAY , tod.hour  )
      cc.set(Calendar.MINUTE      , tod.minute)
      cc.set(Calendar.SECOND      , 0         )
      cc.set(Calendar.MILLISECOND , 0         )

      if (cc.before(time0)) cc.add(Calendar.DAY_OF_MONTH, 1)
      cc.getTime
    }

    private[this] val isWeekend = {
      val d = time0.get(Calendar.DAY_OF_WEEK)
      d === Calendar.SATURDAY || d === Calendar.SUNDAY
    }

    val dateStopSound: Date = makeDate(if (isWeekend) config.soundStopWeekend else config.soundStopWeekdays)
    val dateStopLight: Date = {
      val d = makeDate(if (isWeekend) config.lightStopWeekend else config.lightStopWeekdays)
      if (d.after(dateStopSound)) d else dateStopSound
    }

    private[this] object ttStopSound extends TimerTask {
      def run(): Unit =
        atomic { implicit tx =>
          Player.setMasterVolume(as, 0.0)
          playerOpt.foreach(_.stop())
        }
    }

    private[this] object ttStopLight extends TimerTask {
      def run(): Unit = {
        Main.shutdownPi()

        val serverOpt = playerOpt.flatMap { player =>
          atomic { implicit tx =>
            tryPrint(player.stop())
            as.serverOption
          }
        }
        downloadOpt.foreach { download =>
          tryPrint(download.abort())
        }
        serverOpt.foreach { s =>
          tryPrint(s.peer.quit())
        }

        for {
          kill <- tryPrint(IO.process("killall", "jackd" :: Nil, timeOutSec = 20)(_ => ()))
          _    <- tryPrint(Await.result(kill, Duration(20, TimeUnit.SECONDS)))
        } ()

        tryPrint(Main.hibernateSelf())
        Main.rebootSelf()
      }
    }

    private[this] val timer = new Timer("tumulus-schedule")

    if (!config.noSchedule) {
      timer.schedule(ttStopSound, dateStopSound)
      timer.schedule(ttStopLight, dateStopLight)
    }

    def cancel(): Unit =
      timer.cancel()
  }
}
trait Schedule {
  def dateStarted   : Date
  def dateStopSound : Date
  def dateStopLight : Date

  def cancel(): Unit
}