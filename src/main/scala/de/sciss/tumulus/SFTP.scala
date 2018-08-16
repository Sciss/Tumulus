/*
 *  SFTP.scala
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

import java.util.regex.Pattern

import de.sciss.file._
import de.sciss.processor.Processor
import de.sciss.tumulus.IO.ProcessorMonitor

import scala.swing.Swing

object SFTP {
  final val program = "sftp"

  /** Lists the contents of a directory (or the root directory if not specified).
    * Returns the names of the children of that directory.
    *
    * @param dir        the path on the server or empty string
    * @param timeOutSec the time-out in seconds (default: 30)
    */
  def list(dir: String = "", timeOutSec: Long = 60)(implicit config: Config): Processor[List[String]] = {
    val args = List("-q", config.sftpAddress(dir))
    IO.processStringInStringOut(program, args, input = "ls -1", timeOutSec = timeOutSec)(out =>
      out.split("\n").iterator.dropWhile(_.startsWith("sftp>")).toList
    )
  }

  def download(dir: String = "", file: String, timeOutSec: Long = 1800, target: File, resume: Boolean = false)
              (implicit config: Config): ProcessorMonitor[Unit] = {

    val args = List(/* "-q", */ "-v", config.sftpAddress(dir))
    val patProgress = Pattern.compile("\\s+")
    val flags = if (resume) "-af" else "-f"
//    val input = s"progress\nget $flags $file ${target.path}"
    val input = s"get $flags $file ${target.path}"
    IO.processStringIn(program, args, input = input, timeOutSec = timeOutSec) { lineOut =>
      println(s"LINE: $lineOut")
      if (lineOut.startsWith("sftp>") || lineOut.startsWith("Progress meter ")) {
        ()
      } else {
        val arr = patProgress.split(lineOut)
        val s = arr.iterator.drop(1).mkString("Downloading...  ", "  ", "")
        Swing.onEDT {
          Main.setStatus(s)
        }
      }
    } {
      ()
    }
  }
}
