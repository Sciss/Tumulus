/*
 *  TimeOfDay.scala
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

import scala.util.{Failure, Try}
import de.sciss.numbers.Implicits._

object TimeOfDay {
  implicit object Read extends scopt.Read[TimeOfDay] {
    def arity: Int = 1

    def reads: String => TimeOfDay = parse(_).get
  }

  def parse(s: String): Try[TimeOfDay] = {
    val arr = s.trim.split(':')
    if (arr.length != 2) Failure(new IllegalArgumentException(s"Must be of format <hour>:<minute>"))
    else {
      val tr = for {
        hour    <- Try(arr(0).toInt.wrap(0, 23))
        minute  <- Try(arr(1).toInt.wrap(0, 59))
      } yield {
        TimeOfDay(hour, minute)
      }
      tr
    }
  }
}
final case class TimeOfDay(hour: Int, minute: Int) {
  require (hour   >= 0 && hour   < 24, toString)
  require (minute >= 0 && minute < 60, toString)

  override def toString = f"$hour%02d:$minute%02d"
}