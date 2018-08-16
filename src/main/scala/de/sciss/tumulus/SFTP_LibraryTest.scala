package de.sciss.tumulus

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.StreamCopier
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.xfer.{FileSystemFile, TransferListener}

object SFTP_LibraryTest {
  def main(args: Array[String]): Unit = {
//    testDownload(args(0), args(1), abort = true)
    testUpload(args(0), args(1))
//    testUpload(args(0), args(1), abort = true)
//    testList(args(0), args(1))
  }

  def testList(sftpUser: String, sftpPass: String): Unit = {
    val ssh = new SSHClient
    ssh.addHostKeyVerifier(new PromiscuousVerifier)
    ssh.connect("ssh.strato.de")
    ssh.authPassword(sftpUser, sftpPass)
    try {
      println("--1")
      val c = ssh.newSFTPClient()
      println("--2")
      import scala.collection.JavaConverters._
      c.ls(".").asScala.foreach(println)
      println("--4")
    } finally {
      ssh.disconnect()
    }
    println("--5")
  }

  def testUpload(sftpUser: String, sftpPass: String, abort: Boolean = false): Unit = {
    val ssh = new SSHClient
    ssh.addHostKeyVerifier(new PromiscuousVerifier)
    println("--1")
    ssh.connect("ssh.strato.de")
    println("--1b")
    ssh.authPassword(sftpUser, sftpPass)

    println("--2")
    try {
      println("--3")
      val c = ssh.newSCPFileTransfer()
      val tl = new MyTL(abortPerc = if (abort) 10 else -1)
      c.setTransferListener(tl)
      c.upload(new FileSystemFile("/data/temp/Untitled 1.aif"), "baz.aif")
//      c.upload(new FileSystemFile("/data/temp/_killme2.aif"), "")
      println("--4")
    } finally {
      println("--5")
      ssh.disconnect()
    }
    println("--6")
  }

  def testDownload(sftpUser: String, sftpPass: String, abort: Boolean = false): Unit = {
    val ssh = new SSHClient
//    val keyFile = userHome / ".ssh" / "id_rsa.pub"
//    require (keyFile.exists(), keyFile.path)
    //    ssh.addHostKeyVerifier("70:87:7d:47:68:6d:b6:b3:bc:1c:3f:1d:d8:a5:d2:2d")
    ssh.addHostKeyVerifier(new PromiscuousVerifier)
    println("--1")
    ssh.connect("ssh.strato.de")
    println("--1b")
    ssh.authPassword(sftpUser, sftpPass)

    println("--2")
    try {
      println("--3")
      val c = ssh.newSCPFileTransfer()
      val tl = new MyTL(abortPerc = if (abort) 10 else -1)
      c.setTransferListener(tl)
      c.download("_killme1.aif", new FileSystemFile("/tmp/"))
      println("--4")
    } finally {
      println("--5")
      ssh.disconnect()
    }
    println("--6")
  }

  class MyTL(relPath: String = "", abortPerc: Int = -1) extends TransferListener {
    def directory(name: String): TransferListener = {
      println(s"LOG: started transferring directory `$name")
      new MyTL(relPath + name + "/")
    }

    def file(name: String, size: Long): StreamCopier.Listener = {
      val path = relPath + name
      println(s"LOG: started transferring file `$path` ($size bytes)")
      println("_" * 100)
      new StreamCopier.Listener() {
        private[this] var lastProg = 0
        override def reportProgress(transferred: Long): Unit = {
          val prog = ((transferred * 100) / size).toInt
          while (lastProg < prog) {
            print('#')
            lastProg += 1
            if (lastProg == abortPerc) throw new Exception("Aborted")
          }
//          println(s"LOG: transferred ${(transferred * 100) / size}% of `$path`")
        }
      }
    }
  }
}
