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

import de.sciss.file._
import de.sciss.processor.Processor
import de.sciss.tumulus.UI._
import de.sciss.tumulus.impl.ProcImpl
import semverfi.{PreReleaseVersion, SemVersion, Version}

import scala.concurrent.blocking
import scala.swing.{BorderPanel, Button, GridPanel, Label, Swing}
import scala.util.{Failure, Success}

class UpdatePanel(w: MainWindow)(implicit config: Config)
  extends BorderPanel {

  case class Update(name: String, version: SemVersion)

  private[this] var _hasScanned = false
  private[this] var available   = Option.empty[Update]

  def hasScanned: Boolean = _hasScanned
  private[this] var scanProc    = Option.empty[Processor[List[SFTP.Entry]]]
  private[this] var updateProc  = Option.empty[Processor[Unit]]

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

  private[this] val ggInstall = mkButton("Install") {
    available.foreach(install)
  }
  ggInstall.enabled = false

  add(new GridPanel(0, 1) {
    contents += ggBack
    contents += new Label("Running version:")
    contents += UI.mkInfoLabel(Main.version)
    contents += new Label("Available update:")
  }, BorderPanel.Position.North)

  add(ggAvail, BorderPanel.Position.Center)

  add(new GridPanel(0, 1) {
    contents += ggScan
    contents += ggInstall
  }, BorderPanel.Position.South)

  whenShown(this)(if (!hasScanned) scan())

  // --------------- INSTALL UPDATE ---------------

  def install(u: Update): Unit = {
    UI.requireEDT()
    updateProc.foreach(_.abort())
    updateProc = None

    ggInstall.enabled = false
    val prefix = "Downloading update..."
    Main.setStatus(prefix)
    val debFile = File.createTempIn(userHome, prefix = "tumulus", suffix = ".deb")

    val pDownload = SFTP.download(prefix = prefix, dir = config.sftpDebDir, file = u.name, target = debFile)
    updateProc = Some(pDownload)
    import Main.ec
    pDownload.onComplete { trDownload =>
      Swing.onEDT {
        if (updateProc.contains(pDownload)) {
          updateProc = None
          trDownload match {
            case Success(_) =>
              Main.setStatus("Installing update...")
              val pUpd = new ProcImpl[Unit] {
                protected def body(): Unit = blocking {
                  val code = IO.sudo("dpkg", List("-i", debFile.path))
                  if (code != 0) throw new Exception(s"Code $code")
                }
              }
              updateProc = Some(pUpd)
              pUpd.start()
              pUpd.onComplete { trInstall =>
                Swing.onEDT {
                  if (updateProc.contains(pUpd)) {
                    updateProc = None
                    val msg = trInstall match {
                      case Success(_) => "Updated. Now reboot!"
                      case Failure(ex) => s"Update failed ${ex.getMessage}"
                    }
                    Main.setStatus(msg)
                  }
                }
              }

            case Failure(ex) =>
              enableInstall()
              val msg = ex match {
                case IO.Aborted() => s"Download failed! ${ex.getMessage}"
                case _ => ""
              }
              Main.setStatus(msg)
          }
        }
      }
    }
  }

  private def enableInstall(): Unit =
    ggInstall.enabled = available.isDefined

  // --------------- FIND UPDATE ---------------

  def scan(): Unit = {
    UI.requireEDT()
    scanProc.foreach(_.abort())
    scanProc = None

    ggScan.enabled = false
    _hasScanned = true
    Main.setStatus("Looking for updates...")

    val p = SFTP.list(config.sftpDebDir)
    scanProc = Some(p)
    import Main.ec
    p.onComplete { tr =>
      Swing.onEDT {
        if (scanProc.contains(p)) {
          ggScan.enabled = true
          scanProc = None
          val current = Main.semVersion.opt
          tr match {
            case Success(names) =>
              val upd = names.flatMap { s =>
                val n = s.name
                if (s.isFile && n.startsWith(Main.debPrefix) && n.endsWith(Main.debSuffix)) {
                  val i   = n.indexOf("_") + 1
                  val j   = math.max(i, n.lastIndexOf("_"))
                  val mid = n.substring(i, j)
                  val vRemote = Version(mid)
                  val isNewer = current.forall {
                    case vLocal: PreReleaseVersion => vLocal <= vRemote
                    case vLocal => vLocal < vRemote
                  }
                  if (isNewer) Some(Update(n, vRemote)) else None
                } else {
                  None
                }
              }
              val hasUpdate = upd.nonEmpty
              Main.setStatus(if (hasUpdate) "Found update." else "No update found.")

              if (hasUpdate) {
                val u = upd.maxBy(_.version)
                available         = Some(u)
                ggAvail.text       = u.version.toString
//                grpList.select(ggList)
                ggAvail.visible    = true
                ggInstall.enabled = /* true && */ updateProc.isEmpty
                revalidate()
                repaint()
              } else {
                available         = None
                ggAvail.visible    = false
                ggInstall.enabled = false
                revalidate()
                repaint()
              }

            case Failure(ex) =>
              Main.setStatus(s"Update scan failed: ${ex.getMessage}")
          }
        }
      }
    }
  }
}
