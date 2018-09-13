package de.sciss.tumulus.sound

import de.sciss.tumulus.ConfigLike

final case class Config(isLaptop            : Boolean = false,
                        sftpUser            : String  = "",
                        sftpPass            : String  = "",
                        sftpHost            : String  = "ssh.strato.de",
                        sftpFinger          : String  = "70:87:7d:47:68:6d:b6:b3:bc:1c:3f:1d:d8:a5:d2:2d",
                        sftpDebDir          : String  = "deb",
                        verbose             : Boolean = false,
                        jackName            : String  = "Tumulus",
                        qJackCtl            : Boolean = true,
                        qJackCtlDly         : Int     = 6,
                        errorInterval       : Int     = 300,
                       ) extends ConfigLike {

  def sftpAddress(path: String): String = s"$sftpUser@$sftpHost:$path"
}
