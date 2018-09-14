/*
 *  ServerUser.scala
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

import de.sciss.lucre.synth.{Server, Txn}
import de.sciss.synth.proc.AuralSystem

import scala.util.control.NonFatal

abstract class ServerUser(name: String) extends AuralSystem.Client {
  protected def booted(s: Server)(implicit tx: Txn): Unit

  final def auralStarted(s: Server)(implicit tx: Txn): Unit =
    try {
      booted(s)
    } catch {
      case NonFatal(ex) =>
        println(s"In '$name' auralStarted:")
        ex.printStackTrace()
    }


  final def auralStopped()(implicit tx: Txn): Unit = ()
}
