package de.sciss.tumulus.sound

import de.sciss.processor.impl.ProcessorImpl
import de.sciss.processor.{Processor, ProcessorLike}
import de.sciss.tumulus.SFTP

import scala.annotation.tailrec
import scala.concurrent.{blocking, Await}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object DownloadRender {
  def apply()(implicit config: Config): DownloadRender = {
    import Main.ec
    val res = new Impl
    Main.mkDirs()
    res.start()
    res
  }

  private final class Impl(implicit config: Config)
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
      val procTr = printError(Try(SFTP.list()))
      procTr match {
        case Success(proc) =>
          val listTr = printError(Try(awaitT(proc)))
          listTr match {
            case Success(list)  => list
            case Failure(_)     => listFiles()
          }

        case Failure(_) =>
          waitSome()
          listFiles()
      }
    }


    protected def body(): Unit = {
      val list: List[SFTP.Entry] = listFiles()
      println("----LIST----")
      list.foreach(println)
    }
  }
}
trait DownloadRender extends ProcessorLike[Unit, DownloadRender]