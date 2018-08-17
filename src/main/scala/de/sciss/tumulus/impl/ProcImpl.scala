/*
 *  ProcImpl.scala
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

package de.sciss.tumulus.impl

import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.tumulus.IO.ProcessorMonitor

abstract class ProcImpl[A] extends ProcessorImpl[A, Processor[A]] with ProcessorMonitor[A]