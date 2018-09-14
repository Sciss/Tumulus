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

import de.sciss.file._
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.processor.{Processor, ProcessorLike}
import de.sciss.tumulus.{MainLike, SFTP}

import scala.annotation.tailrec
import scala.concurrent.{Await, blocking}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object DownloadRender {
  def apply()(implicit config: Config, main: MainLike): DownloadRender = {
    import Main.ec
    val res = new Impl
    Main.mkDirs()
    res.start()
    res
  }

  private final class Impl(implicit config: Config, main: MainLike)
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

    private def soundName (base: String): String = s"$base.flac"
    private def photoName (base: String): String = s"$base.jpg"
    private def metaName  (base: String): String = s"$base.properties"

    protected def body(): Unit = {
      // --------- determine online recording sets ---------

      val list0: List[SFTP.Entry] = listFiles()
      val nameSet = list0.iterator.filter(e => e.isFile && e.size > 0L).map(_.name).toSet

      def mkBase(name: String): String = {
        val i = name.indexOf(".")
        if (name.startsWith("rec") && i < 0) "" else name.substring(0, i)
      }

      def entryComplete(name: String): Boolean = {
        val base = mkBase(name)
        base.nonEmpty && nameSet.contains(soundName(base)) &&
          nameSet.contains(photoName(base)) && nameSet.contains(metaName(base))
      }

      val baseSet = nameSet.iterator.collect {
        case n if entryComplete(n) => mkBase(n)
      } .toSet

      log(s"Number of recordings online: ${baseSet.size}")

      checkAborted()

      // --------- start downloading ---------

      val setVec = baseSet.toVector.sorted  // thus oldest to newest
      setVec.foreach { base =>
        val remoteNames = List(
          "sound" -> soundName(base), "photo" -> photoName(base), "meta" -> metaName(base)
        )

        @tailrec
        def loop(rem: List[(String, String)], res: List[String], triesRem: Int = 3): List[String] = {
          checkAborted()
          rem match {
            case (prefix, fRemote) :: tail =>
              val fLocal = Main.downloadDir / fRemote
              val dlTry   = tryPrint(SFTP.download(prefix = prefix, file = fRemote, target = fLocal))
              val resTry  = dlTry.map { dl =>
                tryPrint(awaitT(dl))
              }

              resTry match {
                case Success(_) =>
                  log(s"Downloaded '$fRemote'")
                  loop(tail, fRemote :: res)

                case _ =>
                  val triesRemNew = triesRem - 1
                  log(s"! Failed to download '$fRemote' ($triesRemNew tries left)")
                  waitSome()
                  if (triesRemNew > 0) loop(rem, res, triesRemNew) else loop(tail, res)
              }

            case Nil =>
              res.reverse
          }
        }

        val okNames = loop(remoteNames, res = Nil)

        checkAborted()

        // --------- bla ---------
      }
    }
  }
}
trait DownloadRender extends ProcessorLike[Unit, DownloadRender]