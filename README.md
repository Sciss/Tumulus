# Tumulus

[![Build Status](https://travis-ci.org/Sciss/Tumulus.svg?branch=master)](https://travis-ci.org/Sciss/Tumulus)

## statement

Software for an art project. (C)opyright 2018 by Hanns Holger Rutz. All rights reserved. This project is released under the
[GNU Affero General Public License](http://github.com/Sciss/Tumulus/blob/master/LICENSE) v3+ and comes with absolutely no warranties.
To contact the author, send an email to `contact at sciss.de`.

Shutter icons by Dávid Gladiš, SK, Wifi icon by Adrien Coquet, FR, all licensed under Creative Commons CC-BY.

## building

Builds with sbt against Scala 2.12.

## "Tumulus-Pi" (Recorder)

### preparation

File `~/.tumulus/sftp.properties` should exist and have entries for `user` and `pass` (`key=value`).
Application should be first installed via `sudo dpkg -i tumulus-pi_version_all.deb`. It may then
be updated from within the application.
File `tumulus-pi.desktop` should be copied to `~/.config/autostart/`.

QJackCtl must be installed, and it must be configured to auto-start a configuration for the
Behringer UMC22 at 44.1 kHz. 'Soft' mode and 3 periods of 1024 are recommended.
It also must load a patch bay that contains a bidirectional link
between client `system` and client `Tumulus`. QJackCtl is launched from the main application.

The UMC should have phantom power enabled and direct monitoring disabled. The headphones socket
can be used to monitor the signal.

For encoding, `flac` must be installed on the Pi (`sudo apt install flac`).

The Pi is expected to be connected to a 320x480 pixels touch screen, the UI layout is made for this size.

On the laptop, `fswebcam` is used to take photos for testing (`sudo apt install fswebcam`).

## Tumulus-Light

### preparation

The JNI library `librpiws28114j.so` must be installed.
