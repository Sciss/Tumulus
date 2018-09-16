import com.typesafe.sbt.packager.linux.LinuxPackageMapping

lazy val baseName  = "Tumulus"
lazy val baseNameL = baseName.toLowerCase

lazy val commonSettings = Seq(
  version      := "0.4.5",
  description  := "An art project",
  organization := "de.sciss",
  homepage     := Some(url("https://github.com/Sciss/baseName")),
  licenses     := Seq("agpl v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
  scalaVersion := "2.12.6",
  scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Xfuture", "-encoding", "utf8", "-Xlint")
)

lazy val piMain     = "de.sciss.tumulus.Main"
lazy val soundMain  = "de.sciss.tumulus.sound.Main"
lazy val lightMain  = "de.sciss.tumulus.light.Main"

lazy val buildInfoSettings = Seq(
  // ---- build info ----
  buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
    BuildInfoKey.map(homepage) { case (k, opt)           => k -> opt.get },
    BuildInfoKey.map(licenses) { case (_, Seq((lic, _))) => "license" -> lic }
  ),
  buildInfoOptions += BuildInfoOption.BuildTime
)

lazy val deps = new {
  val main = new {
    val akka                = "2.4.20" // N.B. should match with FScape's
    val audioWidgets        = "1.12.2"
    val equal               = "0.1.2"
    val fileUtil            = "1.1.3"
    val fscape              = "2.17.2"
    val jRPiCam             = "0.2.0"
    val kollFlitz           = "0.2.2"
    val model               = "0.3.4"
    val numbers             = "0.2.0"
    val processor           = "0.4.1"
    val scalaColliderSwing  = "1.39.0"
    val scalaOSC            = "1.1.6"
    val scopt               = "3.7.0"
    val semVerFi            = "0.2.0"
    val soundProcesses      = "3.21.0"
    val sshj                = "0.26.0"
    val submin              = "0.2.2"
    val swingPlus           = "0.3.1"
    val virtualKeyboard     = "1.0.0"
  }
}

lazy val root = project.withId(baseNameL).in(file("."))
  .dependsOn(pi, sound, light, work)
  .aggregate(pi, sound, light, work)

lazy val work = project.withId(s"$baseName-work").in(file("work"))
  .dependsOn(common)
  .settings(commonSettings)
  .settings(
    name := s"$baseName-work",
    libraryDependencies ++= Seq(
      "de.sciss" %% "fscape"                % deps.main.fscape,
      "de.sciss" %% "soundprocesses-views"  % deps.main.soundProcesses,
    )
  )

lazy val common = project.withId(s"$baseNameL-common").in(file("common"))
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings)
  .settings(commonSettings)
  .settings(
    name := s"$baseName-Common",
    buildInfoPackage := "de.sciss.tumulus",
    libraryDependencies ++= Seq(
      "de.sciss"          %% "equal"      % deps.main.equal,
      "de.sciss"          %% "fileutil"   % deps.main.fileUtil,
      "de.sciss"          %% "kollflitz"  % deps.main.kollFlitz,
      "de.sciss"          %% "numbers"    % deps.main.numbers,
      "de.sciss"          %% "processor"  % deps.main.processor,
      "de.sciss"          %% "swingplus"  % deps.main.swingPlus,
      "com.github.scopt"  %% "scopt"      % deps.main.scopt,
      "com.hierynomus"    %  "sshj"       % deps.main.sshj,
    )
  )

lazy val pi = project.withId(piNameL).in(file("pi"))
  .dependsOn(common)
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .settings(commonSettings)
  .settings(
    name := piName,
//    buildInfoPackage := "de.sciss.tumulus",
    libraryDependencies ++= Seq(
      "de.sciss"    %% "audiowidgets-app"     % deps.main.audioWidgets,
      "de.sciss"    %  "jrpicam"              % deps.main.jRPiCam,
      "de.sciss"    %% "model"                % deps.main.model,
      "net.leibman" %% "semverfi"             % deps.main.semVerFi,
      "de.sciss"    %% "soundprocesses-views" % deps.main.soundProcesses,
      "de.sciss"    %  "submin"               % deps.main.submin,
      "de.sciss"    %  "virtualkeyboard"      % deps.main.virtualKeyboard,
    ),
    mainClass in Compile := Some(piMain),
  )
  .settings(piDebianSettings)

lazy val sound = project.withId(soundNameL).in(file("sound"))
  .dependsOn(common)
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .settings(commonSettings)
  .settings(
    name := soundName,
    libraryDependencies ++= Seq(
      "de.sciss"          %% "fscape"                   % deps.main.fscape,
      "de.sciss"          %% "audiowidgets-app"         % deps.main.audioWidgets,
      "de.sciss"          %% "soundprocesses-views"     % deps.main.soundProcesses,
      "de.sciss"          %% "scalacolliderswing-core"  % deps.main.scalaColliderSwing,
      "de.sciss"          %  "submin"                   % deps.main.submin,
      "com.typesafe.akka" %% "akka-actor"               % deps.main.akka,
    ),
    mainClass in Compile := Some(soundMain),
  )
  .settings(soundDebianSettings)

lazy val light = project.withId(lightNameL).in(file("light"))
  .dependsOn(common)
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .settings(commonSettings)
  .settings(
    name := lightName,
    libraryDependencies ++= Seq(
      "de.sciss" %% "scalaosc" % deps.main.scalaOSC,
    ),
    mainClass in Compile := Some(lightMain),
  )
  .settings(lightDebianSettings)

// ---- debian package ----

lazy val maintainerHH = "Hanns Holger Rutz <contact@sciss.de>"

lazy val piName  = s"$baseName-Pi"
lazy val piNameL = piName.toLowerCase

lazy val soundName  = s"$baseName-Sound"
lazy val soundNameL = soundName.toLowerCase

lazy val lightName  = s"$baseName-Light"
lazy val lightNameL = lightName.toLowerCase

lazy val piDebianSettings = useNativeZip ++ Seq[Def.Setting[_]](
  executableScriptName /* in Universal */ := piNameL,
  scriptClasspath /* in Universal */ := Seq("*"),
  name        in Debian := piNameL,
  packageName in Debian := piNameL,
  name        in Linux  := piNameL,
  packageName in Linux  := piNameL,
  mainClass   in Debian := Some(piMain),
  maintainer  in Debian := maintainerHH,
  debianPackageDependencies in Debian += "java8-runtime",
  packageSummary in Debian := description.value,
  packageDescription in Debian :=
    s"""Software for an art installation - $piName.
       |""".stripMargin
) ++ commonDebianSettings

lazy val soundDebianSettings = useNativeZip ++ Seq[Def.Setting[_]](
  executableScriptName /* in Universal */ := soundNameL,
  scriptClasspath /* in Universal */ := Seq("*"),
  name        in Debian := soundNameL,
  packageName in Debian := soundNameL,
  name        in Linux  := soundNameL,
  packageName in Linux  := soundNameL,
  mainClass   in Debian := Some(soundMain),
  maintainer  in Debian := maintainerHH,
  debianPackageDependencies in Debian += "java8-runtime",
  packageSummary in Debian := description.value,
  packageDescription in Debian :=
    s"""Software for an art installation - $soundName.
       |""".stripMargin
) ++ commonDebianSettings

lazy val lightDebianSettings = useNativeZip ++ Seq[Def.Setting[_]](
  javaOptions in Universal += "-Djava.library.path=/usr/share/tumulus-light/lib/",
  executableScriptName /* in Universal */ := lightNameL,
  scriptClasspath /* in Universal */ := Seq("*"),
  name        in Debian := lightNameL,
  packageName in Debian := lightNameL,
  name        in Linux  := lightNameL,
  packageName in Linux  := lightNameL,
  mainClass   in Debian := Some(lightMain),
  maintainer  in Debian := maintainerHH,
  debianPackageDependencies in Debian += "java8-runtime",
  packageSummary in Debian := description.value,
  packageDescription in Debian :=
    s"""Software for an art installation - $lightName.
      |""".stripMargin
) ++ commonDebianSettings

lazy val commonDebianSettings = Seq(
  // include all files in src/debian in the installed base directory
  linuxPackageMappings in Debian ++= {
    val n     = (name            in Debian).value.toLowerCase
    val dir   = (sourceDirectory in Debian).value / "debian"
    val f1    = (dir * "*").filter(_.isFile).get  // direct child files inside `debian` folder
    val f2    = ((dir / "doc") * "*").get
    //
    def readOnly(in: LinuxPackageMapping) =
      in.withUser ("root")
        .withGroup("root")
        .withPerms("0644")  // http://help.unc.edu/help/how-to-use-unix-and-linux-file-permissions/
    //
    val aux   = f1.map { fIn => packageMapping(fIn -> s"/usr/share/$n/${fIn.name}") }
    val doc   = f2.map { fIn => packageMapping(fIn -> s"/usr/share/doc/$n/${fIn.name}") }
    (aux ++ doc).map(readOnly)
  }
)

