/*
 *  Network.scala
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

import java.net.{InetAddress, InetSocketAddress}

import scala.util.{Failure, Success, Try}

object Network {

  def parseSocket(s: String): Either[String, InetSocketAddress] = {
    val arr = s.split(':')
    if (arr.length != 2) Left(s"Must be of format <host>:<port>")
    else parseSocket(arr)
  }

  private def parseSocket(arr: Array[String]): Either[String, InetSocketAddress] = {
    val host = arr(0)
    val port = arr(1)
    Try(new InetSocketAddress(host, port.toInt)) match {
      case Success(addr)  => Right(addr)
      case Failure(ex)    => Left(s"Invalid socket address: $host:$port - ${ex.getClass.getSimpleName}")
    }
  }

  def mkOwnSocket(defaultPort: Int)(implicit config: ConfigLike): InetSocketAddress = {
    val res = config.ownSocket.getOrElse {
      val host = thisIP()
      //      if (!config.isLaptop) {
      //        Network.compareIP(host)
      //      }
      new InetSocketAddress(host, defaultPort)
    }
    res
  }

  def thisIP(): String = {
    import sys.process._
    // cf. https://unix.stackexchange.com/questions/384135/
    val ifConfig    = Seq("ip", "a", "show", "eth0").!!
    val ifConfigPat = "inet "
    val line        = ifConfig.split("\n").map(_.trim).find(_.startsWith(ifConfigPat)).getOrElse("")
    val i0          = line.indexOf(ifConfigPat)
    val i1          = if (i0 < 0) 0 else i0 + ifConfigPat.length
    val i2          = line.indexOf("/", i1)
    if (i0 < 0 || i2 < 0) {
      val local = InetAddress.getLocalHost.getHostAddress
      Console.err.println(s"No assigned IP4 found in eth0! Falling back to $local")
      local
    } else {
      line.substring(i1, i2)
    }
  }
}
