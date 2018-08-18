/*
 *  Util.scala
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

import java.awt.image.BufferedImage

import de.sciss.file.File
import de.sciss.numbers
import de.sciss.processor.Processor
import javax.imageio.{IIOImage, ImageIO, ImageTypeSpecifier, ImageWriteParam, ImageWriter}
import javax.imageio.plugins.jpeg.JPEGImageWriteParam
import javax.imageio.stream.FileImageOutputStream

import scala.concurrent.{Future, Promise}
import scala.swing.Swing
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

object Util {
  def protect[A](res: Promise[A])(thunk: => Unit): Unit =
    try {
      thunk
    } catch {
      case NonFatal(ex) =>
        res.tryFailure(ex)
        throw ex
    }

  def flatMapEDT[A, B](in: Future[A])(fun: A => Future[B]): Future[B] = {
    import Main.ec
    in.flatMap { a =>
      val res = Promise[B]()
      UI.deferIfNeeded {
        protect(res) {
          val fut = fun(a)
          res.tryCompleteWith(fut)
        }
      }
      res.future
    }
  }

  def uploadWithStatus(tpe: String)(source: => File)(done: File => Unit)(implicit config: Config): Future[Unit] = {
    UI.requireEDT()
    val prefix = s"Uploading $tpe..."
    Main.setStatus(prefix)

    val upP   = SFTP.upload(prefix = prefix, source = source, dir = "", file = "")
    import Main.ec
    upP.onComplete { trUp =>
      done(source)
      Swing.onEDT {
        trUp match {
          case Success(_) =>
            Main.setStatus(s"${tpe.capitalize} upload done.")

          case Failure(ex) =>
            val msg = ex match {
              case Processor.Aborted() => ""
              case _ => s"${tpe.capitalize} upload failed! ${ex.getMessage}"
            }
            Main.setStatus(msg)
        }
      }
    }

    upP
  }

  def writeJPEG(img: BufferedImage, fOut: File, quality: Int = 95): Unit = {
    val imgParam: ImageWriteParam = {
      val p = new JPEGImageWriteParam(null)
      p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
      import numbers.Implicits._
      p.setCompressionQuality((quality * 0.01f).clip(0f, 1f))
      p
    }

    val writer: ImageWriter = {
      val fmtName = "jpg"
      val it = ImageIO.getImageWriters(ImageTypeSpecifier.createFromRenderedImage(img), fmtName)
      if (!it.hasNext) throw new IllegalArgumentException(s"No image writer for JPEG")
      it.next()
    }

    val out = new FileImageOutputStream(fOut)
    writer.setOutput(out)
    writer.write(null /* meta */ , new IIOImage(img, null /* thumb */ , null /* meta */), imgParam)
    writer.reset()
  }
}
