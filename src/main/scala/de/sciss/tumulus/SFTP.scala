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

import de.sciss.file._
import de.sciss.processor.Processor
import de.sciss.tumulus.IO.ProcessorMonitor
import de.sciss.tumulus.impl.ProcImpl
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.StreamCopier
import net.schmizz.sshj.transport.verification.{FingerprintVerifier, PromiscuousVerifier}
import net.schmizz.sshj.xfer.scp.ScpCommandLine
import net.schmizz.sshj.xfer.{FileSystemFile, TransferListener}

import scala.concurrent.blocking
import scala.swing.Swing

object SFTP {
  case class Entry(name: String, isFile: Boolean, isDirectory: Boolean, size: Long, lastModified: Long)

  /** Lists the contents of a directory (or the root directory if not specified).
    * Returns the names of the children of that directory.
    *
    * @param dir        the path on the server or empty string
    * @param timeOutSec the time-out in seconds (default: 30)
    */
  def list(dir: String = "", timeOutSec: Long = 60)(implicit config: Config): Processor[List[Entry]] = {
    runProc[List[Entry]] {
      withSSH { ssh =>
        val c = ssh.newSFTPClient()
        import scala.collection.JavaConverters._
        val dir1 = if (dir.isEmpty) "." else dir
        c.ls(dir1).iterator().asScala.map { info =>
          val attr = info.getAttributes
          Entry(info.getName, isFile = info.isRegularFile, isDirectory = info.isDirectory,
            size = attr.getSize, lastModified = attr.getMtime)
        } .toList
      }
    }
  }

  def download(prefix: String, dir: String = "", file: String, timeOutSec: Long = 1800, target: File)
              (implicit config: Config): ProcessorMonitor[Unit] = {
    runProc[Unit] {
      withSSH { ssh =>
        val c = ssh.newSCPFileTransfer()
        val tl = new ProgressTracker(prefix)
        c.setTransferListener(tl)
        val path = if (dir.isEmpty) file else s"$dir/$file"
        c.download(path, new FileSystemFile(target))
      }
    }
  }

  def upload(prefix: String, source: File, dir: String = "", file: String, timeOutSec: Long = 3600)
            (implicit config: Config): ProcessorMonitor[Unit] = {
    runProc[Unit] {
      withSSH { ssh =>
        val c = ssh.newSCPFileTransfer()
        val tl = new ProgressTracker(prefix)
        c.setTransferListener(tl)
        val path = if (dir.isEmpty) file else s"$dir/$file"
        // cf. https://github.com/hierynomus/sshj/issues/449
//        c.upload(new FileSystemFile(source), path)
        val up = c.newSCPUploadClient()
        up.copy(new FileSystemFile(source), path, ScpCommandLine.EscapeMode.NoEscape)
      }
    }
  }

  private def runProc[A](block: => A): ProcessorMonitor[A] = {
    val p = new ProcImpl[A] {
      protected def body(): A = blocking(block)
    }
    import Main.ec
    p.start()
    p
  }

  private def withSSH[A](body: SSHClient => A)(implicit config: Config): A = {
    val ssh = new SSHClient
    val kv = if (config.sftpFinger.isEmpty) new PromiscuousVerifier
             else FingerprintVerifier.getInstance(config.sftpFinger)
    ssh.addHostKeyVerifier(kv)
    ssh.connect(config.sftpHost)
    ssh.authPassword(config.sftpUser, config.sftpPass)
    try {
      body(ssh)
    } finally {
      ssh.disconnect()
    }
  }

  private class ProgressTracker(prefix: String) extends TransferListener {
    def directory(name: String): TransferListener = {
//      println(s"LOG: started transferring directory `$name")
      this
    }

    def file(name: String, size: Long): StreamCopier.Listener = {
//      println(s"LOG: started transferring file `$path` ($size bytes)")
//      println("_" * 100)
      new StreamCopier.Listener() {
        private[this] var lastProgress = 0
        override def reportProgress(transferred: Long): Unit = {
          val progress = ((transferred * 100) / size).toInt
          if (lastProgress < progress) {
            lastProgress = progress
            Swing.onEDT {
              Main.setStatus(s"$prefix $progress%")
            }
          }
        }
      }
    }
  }
}
