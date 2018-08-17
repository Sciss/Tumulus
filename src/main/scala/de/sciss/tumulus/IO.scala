/*
 *  IO.scala
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

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import de.sciss.file.File
import de.sciss.kollflitz.ISeq
import de.sciss.processor.Processor
import de.sciss.tumulus.impl.ProcImpl

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, blocking}
import scala.sys.process._

object IO {
//  case class Aborted() extends Exception

  trait ProcessorMonitor[A] extends Processor[A] {
    def progress_=(value: Double): Unit
  }

  def process[A](cmd: String, args: ISeq[String], timeOutSec: Long)
                (output: String => A)(implicit config: Config): Processor[A] =
    processStringInStringOut(cmd = cmd, args = args, input = "", timeOutSec = timeOutSec)(output)

    /** Runs a process with a given string input.
    *
    * @param cmd          the shell command
    * @param args         the arguments to the command
    * @param input        the input piped into the command
    * @param timeOutSec   the time-out in seconds before the process is aborted
    * @param output       the function that maps the process' standard output string to a return value
    * @return A processor that can be cancelled to abort the process early
    */
  def processStringInStringOut[A](cmd: String, args: ISeq[String], input: String, timeOutSec: Long)
                                 (output: String => A)(implicit config: Config): ProcessorMonitor[A] = {
    val sbOut = new StringBuffer()
    processStringIn[A](cmd, args, input = input, timeOutSec = timeOutSec) { s =>
      sbOut.append(s)
      sbOut.append('\n')
    } {
      val outS = sbOut.toString
      output(outS)
    }
  }

  def processStringIn[A](cmd: String, args: ISeq[String], input: String, timeOutSec: Long)(lineOut: String => Unit)
                        (mkResult: => A)(implicit config: Config): ProcessorMonitor[A] = {
    val pb = Process(cmd, args).#<(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)))
    if (config.verbose) {
      println(args.mkString(s"EXEC: $cmd ", " ", ""))
    }

    val p: ProcessorMonitor[A] = new ProcImpl[A] {
      private[this] var _process: Process = _

      override protected def notifyAborted(): Unit = synchronized {
        if (_process != null) _process.destroy()
      }

      protected def body(): A = {
        val sbErr = new StringBuffer()
        val pl = ProcessLogger(fout = { s =>
          lineOut(s)
        }, ferr = { s =>
          sbErr.append(s)
          sbErr.append('\n')
        })
        synchronized {
          checkAborted()
          _process = pb.run(pl)
        }
        val futAux = Future {
          blocking {
            _process.exitValue()
          }
        }
        val code: Int = Await.result(futAux, Duration(timeOutSec, TimeUnit.SECONDS))
        if (code == 0) {
          mkResult
        } else {
          throw new Exception(s"exit code $code\n\n$sbErr")
        }
      }

      start()(Main.ec)
    }
    p
  }

  def sudo(cmd: String, args: List[String])(implicit config: Config): Int = {
    val pb = if (config.isLaptop) {
      val cmd1 = "sudo" :: "-A" :: cmd :: args
      Process(cmd1, Option.empty[File], "SUDO_ASKPASS" -> "/usr/bin/ssh-askpass")
    } else {
      val cmd1 = "sudo" :: cmd :: args
      Process(cmd1)
    }
    pb.!
  }
}
