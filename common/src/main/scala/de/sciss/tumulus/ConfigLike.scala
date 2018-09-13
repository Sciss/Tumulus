/*
 *  ConfigLike.scala
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

import java.net.InetSocketAddress

trait ConfigLike {
  def verbose     : Boolean
  def isLaptop    : Boolean

  def qJackCtl    : Boolean
  def qJackCtlDly : Int

  def sftpUser    : String
  def sftpPass    : String
  def sftpHost    : String
  def sftpFinger  : String

  def ownSocket   : Option[InetSocketAddress]

  def ledGroups   : Int
  def ledPerGroup : Int
  def ledCount    : Int = ledGroups * ledPerGroup
}
