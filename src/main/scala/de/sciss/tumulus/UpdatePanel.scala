/*
 *  UpdatePanel.scala
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

import java.util.concurrent.TimeUnit

import de.sciss.equal.Implicits._
import de.sciss.file._
import de.sciss.processor.Processor
import de.sciss.tumulus.UI._
import de.sciss.tumulus.impl.ProcImpl
import semverfi.{PreReleaseVersion, SemVersion, Version}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise, blocking}
import scala.swing.{BorderPanel, Button, GridPanel, Label, Swing}
import scala.util.{Failure, Success}

class UpdatePanel(w: MainWindow)(implicit config: Config)
  extends BorderPanel {

  sealed trait Source
  case object Remote extends Source
  case class Local(debFile: File) extends Source

  case class Update(name: String, version: SemVersion, source: Source)

  private[this] var _hasScanned = false
  private[this] var available   = Option.empty[Update]

  def hasScanned: Boolean = _hasScanned
  private[this] var scanRemoteProc  = Option.empty[Processor[List[SFTP.Entry]]]
  private[this] var scanLocalProc   = Option.empty[Processor[List[Update]]]
  private[this] var scanFut         = Option.empty[Future[List[Update]]]
  private[this] var updateProc      = Option.empty[Processor[Unit]]

  private[this] val ggBack = mkBackPane("Update") {
    w.home()
  }
//  private[this] val ggList  = new RadioButton()
//  private[this] val grpList = new ButtonGroup(ggList)
//  ggList.visible = false

  private[this] val ggAvail = UI.mkInfoLabel("")

  private[this] val ggScan: Button = mkButton("Scan") {
    scan()
  }

  private[this] var isReboot = false

  private[this] val ggInstallReboot = mkButton("Install") {
    if (isReboot) {
      Main.reboot()
    } else {
      available.foreach(install)
    }
  }
  ggInstallReboot.enabled = false

  add(new GridPanel(0, 1) {
    contents += ggBack
    contents += new Label("Running version:")
    contents += UI.mkInfoLabel(Main.version)
    contents += new Label("Available update:")
  }, BorderPanel.Position.North)

  add(ggAvail, BorderPanel.Position.Center)

  add(new GridPanel(0, 1) {
    contents += ggScan
    contents += ggInstallReboot
  }, BorderPanel.Position.South)

  whenShown(this)(if (!hasScanned) scan())

  // --------------- INSTALL UPDATE ---------------

  def install(u: Update): Unit = {
    UI.requireEDT()
    updateProc.foreach(_.abort())
    updateProc = None

    ggInstallReboot.enabled = false
    val prefix = "Downloading update..."
    Main.setStatus(prefix)
//    val debFile = File.createTempIn(userHome, prefix = "tumulus", suffix = ".deb")

    u.source match {
      case Local(debFile) => installDeb(debFile)
      case Remote =>
        val debFile   = File.createTemp(prefix = "tumulus", suffix = ".deb")
        val pDownload = SFTP.download(prefix = prefix, dir = config.sftpDebDir, file = u.name, target = debFile)
        updateProc = Some(pDownload)
        import Main.ec
        pDownload.onComplete { trDownload =>
          Swing.onEDT {
            if (updateProc.contains(pDownload)) {
              updateProc = None
              trDownload match {
                case Success(_) =>
                  installDeb(debFile)

                case Failure(ex) =>
                  enableInstall()
                  val msg = ex match {
                    case Processor.Aborted() => ""
                    case _ => s"Download failed! ${ex.getMessage}"
                  }
                  Main.setStatus(msg)
              }
            }
          }
        }
    }
  }

  private def installDeb(debFile: File): Unit = {
    requireEDT()
    import Main.ec
    Main.setStatus("Installing update...")
    val pUpd = new ProcImpl[Unit] {
      protected def body(): Unit = blocking {
        val (code, _) = IO.sudo("dpkg", List("-i", debFile.path))
        if (code !== 0) throw new Exception(s"Code $code")
      }
    }
    updateProc = Some(pUpd)
    pUpd.start()
    pUpd.onComplete { trInstall =>
      Swing.onEDT {
        if (updateProc.contains(pUpd)) {
          updateProc = None
          trInstall match {
            case Success(_) =>
              Main.setStatus("Updated. Now reboot!")
              ggInstallReboot.text    = "Reboot"
              ggInstallReboot.enabled = true
              isReboot                = true

            case Failure(ex) =>
              Main.setStatus(s"Update failed ${ex.getMessage}")
          }
        }
      }
    }
  }

  private def enableInstall(): Unit =
    ggInstallReboot.enabled = available.isDefined

  // --------------- FIND UPDATE ---------------

  def scan(): Unit = {
    UI.requireEDT()
    scanRemoteProc.foreach(_.abort())
    scanRemoteProc = None
    scanLocalProc .foreach(_.abort())
    scanLocalProc = None
    ggScan.enabled = false
    _hasScanned = true
    Main.setStatus("Looking for updates...")

    import Main.ec

    val vCurrent = Main.semVersion.opt
    val accept: String => Option[SemVersion] = { n =>
      if (n.startsWith(Main.debPrefix) && n.endsWith(Main.debSuffix)) {
        val i       = n.indexOf("_") + 1
        val j       = math.max(i, n.lastIndexOf("_"))
        val mid     = n.substring(i, j)
        val vAvail  = Version(mid)
        val isNewer = vCurrent.forall {
          case vLocal: PreReleaseVersion => vLocal <= vAvail
          case vLocal => vLocal < vAvail
        }
        if (isNewer) Some(vAvail) else None
      } else None
    }

    val futLoc: Future[List[Update]] = scanLocal(accept)
    val futRem: Future[List[Update]] = futLoc.transformWith {
      case Failure(_) | Success(Nil) =>
        val res = Promise[List[Update]]()
        Swing.onEDT {
          Util.protect(res) {
            val fR = scanRemote(accept)
            res.tryCompleteWith(fR)
          }
        }
        res.future
      case Success(upd) => Future.successful(upd)
    }
    val fut = Future {
      Await.result(futRem, Duration(2, TimeUnit.MINUTES))
    }

    scanFut = Some(fut)

    fut.onComplete { tr =>
      Swing.onEDT {
        if (scanFut.contains(fut)) {
          ggScan.enabled  = true
          scanRemoteProc  = None
          scanLocalProc   = None
        }

        tr match {
          case Success(upd) =>
            val hasUpdate = upd.nonEmpty
            Main.setStatus(if (hasUpdate) "Found update." else "No update found.")

            if (hasUpdate) {
              val u = upd.maxBy(_.version)
              available               = Some(u)
              ggAvail.text            = u.version.toString
              ggAvail.visible         = true
              ggInstallReboot.enabled = /* true && */ updateProc.isEmpty
              revalidate()
              repaint()
            } else {
              available               = None
              ggAvail.visible         = false
              ggInstallReboot.enabled = false
              revalidate()
              repaint()
            }

          case Failure(ex) =>
            Main.setStatus(s"Update scan failed: ${ex.getMessage}")
        }
      }
    }
  }

  private def scanLocal(accept: String => Option[SemVersion]): Future[List[Update]] = {
    UI.requireEDT()
    val pL = new ProcImpl[List[Update]] {
      protected def body(): List[Update] = blocking {
        (file("/media") / sys.props("user.name"))
          .children(_.isDirectory).flatMap(_.children(_.isFile))
          .iterator
          .flatMap { f =>
            accept(f.name).map(vLocal => Update(f.name, vLocal, Local(f)))
          }
          .toList
      }
      start()(Main.ec)
    }
    scanLocalProc = Some(pL)
    pL
  }

  private def scanRemote(accept: String => Option[SemVersion]): Future[List[Update]] = {
    UI.requireEDT()
    val pR          = SFTP.list(config.sftpDebDir)
    scanRemoteProc  = Some(pR)
    import Main.ec

    pR.map { names =>
      val upd = names.flatMap { s =>
        val n = s.name
        if (!s.isFile) None else accept(n).map(vRemote => Update(n, vRemote, Remote))
      }
      upd
    }
  }
}
