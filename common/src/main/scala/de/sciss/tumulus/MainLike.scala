/*
 *  MainLike.scala
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

package de.sciss.tumulus

import scala.util.control.NonFatal

trait MainLike {
  implicit def _self: MainLike = this

  def setStatus(s: String): Unit

  private def buildInfString(key: String): String = try {
    val clazz = Class.forName("de.sciss.tumulus.BuildInfo")
    val m     = clazz.getMethod(key)
    m.invoke(null).toString
  } catch {
    case NonFatal(_) => "?"
  }

  protected def subModule: String

  def name              : String      = s"Mexican Tumulus ($subModule)"
  final def version     : String      = buildInfString("version")
  final def builtAt     : String      = buildInfString("builtAtString")
  final def fullVersion : String      = s"v$version, built $builtAt"
}
