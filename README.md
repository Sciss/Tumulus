# Tumulus

[![Build Status](https://travis-ci.org/Sciss/Tumulus.svg?branch=master)](https://travis-ci.org/Sciss/Tumulus)

## statement

Software for an art project. (C)opyright 2018 by Hanns Holger Rutz. All rights reserved. This project is released under the
[GNU Affero General Public License](http://github.com/Sciss/Tumulus/blob/master/LICENSE) v3+ and comes with absolutely no warranties.
To contact the author, send an email to `contact at sciss.de`.

## building

Builds with sbt against Scala 2.12.

## preparation

File `~/.tumulus/sftp.properties` should exist and have entries for `user` and `pass` (`key=value`).
Application should be first installed via `sudo dpkg -i tumulus-pi_version_all.deb`. It may then
be updated from within the application.
File `tumulus-pi.desktop` should be copied to `~/.config/autostart/`.
