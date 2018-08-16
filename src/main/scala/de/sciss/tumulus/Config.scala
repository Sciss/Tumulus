/*
 *  Config.scala
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

final case class Config(isLaptop: Boolean = false, sftpUser: String = "undefined",
                        sftpHost: String = "ssh.strato.de", dark: Boolean = true, fullScreen: Boolean = true) {

}
