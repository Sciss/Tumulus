/*
 *  DownloadRender.scala
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

import java.util.concurrent.TimeUnit

import de.sciss.file._
import de.sciss.fscape.graph.ImageFile
import de.sciss.kollflitz.Vec
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.processor.{Processor, ProcessorLike}
import de.sciss.synth.io.{AudioFile, AudioFileSpec}
import de.sciss.tumulus.sound.Main.{atomic, downloadDir, setStatus}
import de.sciss.tumulus.sound.Player.inBackup
import de.sciss.tumulus.{IO, MainLike, PhotoSettings, SFTP}

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, blocking}
import scala.util.{Failure, Success, Try}

object DownloadRender {
  def apply(player: Player)(implicit config: Config, main: MainLike): DownloadRender = {
    import Main.ec
    val res = new Impl(player)
    Main.mkDirs()
    res.start()
    res
  }

  private final class Impl(player: Player)(implicit config: Config, main: MainLike)
    extends ProcessorImpl[Unit, DownloadRender] with DownloadRender {

    private[this] val sync = new AnyRef
    private[this] var subProc = Option.empty[ProcessorLike[Any, Any]]

    override protected def notifyAborted(): Unit =
      sync.synchronized {
        subProc.foreach(_.abort())
      }

    protected def awaitT[B](that: ProcessorLike[B, Any], timeOut: Duration = Duration.Inf): B = {
      val res = try {
        sync.synchronized {
          assert(subProc.isEmpty)
          subProc = Some(that)
        }
        Await.result(that, timeOut)
      } finally {
        sync.synchronized {
          assert(subProc.contains(that))
          subProc = None
        }
      }
      res
    }

    private def tryPrint[A](body: => A): Try[A] =
      printError(Try(body))

    private def printError[A](tr: Try[A]): Try[A] = {
      tr match {
        case Failure(Processor.Aborted()) =>
        case Failure(ex)                  => ex.printStackTrace()
        case _                            =>
      }
      tr
    }

    private def waitSome(): Unit =
      blocking(Thread.sleep(20000L))

    @tailrec
    private def listFiles(): List[SFTP.Entry] = {
      checkAborted()
      val procTr = tryPrint(SFTP.list())
      procTr match {
        case Success(proc) =>
          val listTr = tryPrint(awaitT(proc))
          listTr match {
            case Success(list)  => list
            case Failure(_)     => listFiles()
          }

        case Failure(_) =>
          waitSome()
          listFiles()
      }
    }

    private def log(what: => String): Unit =
      if (config.verbose) println(what)

    // --------- determine online recording sets ---------
    private def bodyListFiles(): Vec[String] = {
      val list0: List[SFTP.Entry] = listFiles()
      val nameSet = list0.iterator.filter(e => e.isFile && e.size > 0L).map(_.name).toSet

      def mkBase(name: String): String = {
        val i = name.indexOf(".")
        if (name.startsWith("rec") && i < 0) "" else name.substring(0, i)
      }

      def entryNewAndComplete(name: String): Boolean = {
        val base      = mkBase(name)
        val inCurrent = Player.okFile(base)
        val isOld     = inCurrent.exists() || Player.inBackup(inCurrent).exists()

        !isOld && base.nonEmpty && nameSet.contains(flacName(base)) &&
          nameSet.contains(photoName(base)) && nameSet.contains(metaName(base))
      }

      val baseSet = nameSet.iterator.collect {
        case n if entryNewAndComplete(n) => mkBase(n)
      } .toSet

      val infoSize = s"Number of recordings online: ${baseSet.size}"
      log(infoSize)
      setStatus(infoSize)

      checkAborted()

      val setVec = baseSet.toVector.sorted  // thus oldest to newest
      setVec
    }

    // --------- start downloading ---------
    private def bodyDownload(base: String): Option[Local] = {
      val remoteNames = List(
        "sound" -> flacName(base), "photo" -> photoName(base), "meta" -> metaName(base)
      )

      @tailrec
      def loop(rem: List[(String, String)], triesRem: Int = 3): Boolean = {
        checkAborted()
        rem match {
          case (prefix, fRemote) :: tail =>
            val fLocal  = downloadDir / fRemote
            val dlTry   = tryPrint(SFTP.download(prefix = prefix, file = fRemote, target = fLocal))
            val resTry  = dlTry.map { dl =>
              tryPrint(awaitT(dl))
            }

            resTry match {
              case Success(_) =>
                log(s"Downloaded '$fRemote'")
                loop(tail)

              case _ =>
                val triesRemNew = triesRem - 1
                log(s"! Failed to download '$fRemote' ($triesRemNew tries left)")
                waitSome()
                if (triesRemNew > 0) loop(rem, triesRemNew) else false
            }

          case Nil =>
            true
        }
      }

      val dlOk = loop(remoteNames)

      checkAborted()

      val res = if (dlOk) {
        (for {
          meta      <- tryPrint(PhotoSettings().loadFrom(downloadDir / metaName (base)))
          imageSpec <- tryPrint(ImageFile      .readSpec(downloadDir / photoName(base)))
          _         <- decompressFLAC(fIn = downloadDir / flacName(base), fOut = downloadDir / wavName(base))
          audioSpec <- tryPrint(AudioFile      .readSpec(downloadDir / wavName  (base)))
        } yield {
          Local(base, meta, audioSpec, imageSpec)
        }).toOption

      } else None

      checkAborted()
      res
    }

    private def decompressFLAC(fIn: File, fOut: File): Try[Unit] = {
      val cmd     = "flac"
      val args    = List("-s", "-d", "-f", "--no-delete-input-file", "-o", fOut.path, fIn.path)
      for {
        flac <- tryPrint(IO.process(cmd, args, timeOutSec = 30)(_ => ()))
        _    <- tryPrint(awaitT(flac))
      } yield ()
    }

    @tailrec
    protected def body(): Unit = {
      log("Running download check...")
      val setVec = bodyListFiles()

      setVec.foreach { base =>
        val localOpt = bodyDownload(base)
        localOpt.foreach { local =>
          val fResonance = Player.resonanceFile(local.base)
          val soundOk = (for {
            soundPr <- tryPrint(SoundRenderer.run(fIn = local.fSound, specIn = local.audioSpec, fOut = fResonance))
            _       <- tryPrint(Await.result(soundPr, Duration(2, TimeUnit.MINUTES)))
          } yield ()).isSuccess

          checkAborted()

          if (soundOk) {
            val fColors = Player.colorsFile(local.base)
            val colorsTr =
              for {
                pixelsPr <- tryPrint(PixelRenderer.run(fIn = local.fPhoto, specIn = local.imageSpec,
                                      photoSettings = local.meta, fOutColor = fColors))
                _        <- tryPrint(Await.result(pixelsPr, Duration(2, TimeUnit.MINUTES)))
                colors   <- tryPrint(PixelRenderer.readColors(fColors))
             } yield colors

            colorsTr match {
              case Success(colors) =>
                val fOk   = Player.okFile(local.base)
                val okOk  = tryPrint(fOk.createNewFile()).isSuccess

                if (okOk) {
                  setStatus(s"Rendered '$base'.")
                  local.moveToBackup()
                  val pe = Player.Entry(base = base, fResonance = fResonance, fColors = fColors, colors = colors)
                  atomic { implicit tx =>
                    player.inject(pe)
                  }
                }

              case Failure(_) =>
            }
          }
        }
      }

      log("All downloaded. Waiting for an hour.")
      blocking(Thread.sleep(60 * 60 * 1000L)) // check again in an hour
      body()
    }
  }

  private def flacName  (base: String): String = s"$base.flac"
  private def wavName   (base: String): String = s"$base.wav"
  private def photoName (base: String): String = s"$base.jpg"
  private def metaName  (base: String): String = s"$base.properties"

  private case class Local(base: String, meta: PhotoSettings, audioSpec: AudioFileSpec, imageSpec: ImageFile.Spec) {
    def fSound: File = downloadDir / wavName  (base)
    def fPhoto: File = downloadDir / photoName(base)

    private def moveToBackup(f: File): Unit = {
      val ok = f.renameTo(inBackup(f))
      if (!ok) f.delete()
    }

    def moveToBackup(): Unit = {
      moveToBackup(fSound)
      moveToBackup(fPhoto)
      moveToBackup(flacName(base))
    }
  }
}
trait DownloadRender extends ProcessorLike[Unit, DownloadRender]