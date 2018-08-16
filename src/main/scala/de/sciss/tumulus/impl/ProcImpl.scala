package de.sciss.tumulus.impl

import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.tumulus.IO.ProcessorMonitor

abstract class ProcImpl[A] extends ProcessorImpl[A, Processor[A]] with ProcessorMonitor[A]