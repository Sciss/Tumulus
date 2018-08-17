import com.typesafe.sbt.packager.linux.LinuxPackageMapping

lazy val baseName  = "Tumulus"
lazy val baseNameL = baseName.toLowerCase

lazy val commonSettings = Seq(
  version      := "0.1.3-SNAPSHOT",
  description  := "An art project",
  organization := "de.sciss",
  homepage     := Some(url("https://github.com/Sciss/baseName")),
  licenses     := Seq("agpl v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
  scalaVersion := "2.12.6",
  scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Xfuture", "-encoding", "utf8", "-Xlint")
)

lazy val piMain = "de.sciss.tumulus.Main"

lazy val buildInfoSettings = Seq(
  buildInfoPackage := "de.sciss.tumulus",
  // ---- build info ----
  buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
    BuildInfoKey.map(homepage) { case (k, opt)           => k -> opt.get },
    BuildInfoKey.map(licenses) { case (_, Seq((lic, _))) => "license" -> lic }
  ),
  buildInfoOptions += BuildInfoOption.BuildTime
)

lazy val root = project.withId(piNameL).in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .settings(commonSettings)
  .settings(buildInfoSettings)
  .settings(
    name := "piName",
    // mainClass       in assembly := Some(piMain)
    // assemblyJarName in assembly := "CamShot.jar"
    libraryDependencies ++= Seq(
      "de.sciss"          %% "fileutil"             % "1.1.3",
      "de.sciss"          %% "numbers"              % "0.2.0",
      "de.sciss"          %% "kollflitz"            % "0.2.2",
      "de.sciss"          %% "equal"                % "0.1.2",
      "de.sciss"          %% "model"                % "0.3.4",
      "de.sciss"          %% "scalaaudiofile"       % "1.4.7",
      "de.sciss"          %% "soundprocesses-views" % "3.21.0",
      "de.sciss"          %% "swingplus"            % "0.3.1",
      "de.sciss"          %% "processor"            % "0.4.1",
      "de.sciss"          %  "submin"               % "0.2.2",
      // "de.sciss"          %  "jrpicam"          % "0.2.0",
      // "com.pi4j"          %  "pi4j-core"        % "1.1",
      "com.github.scopt"  %% "scopt"                % "3.7.0",
      "net.leibman"       %% "semverfi"             % "0.2.0",
      "com.hierynomus"    %  "sshj"                 % "0.26.0"
    ),
    mainClass in Compile := Some(piMain),
  )
  .settings(piDebianSettings)

// ---- debian package ----

lazy val maintainerHH = "Hanns Holger Rutz <contact@sciss.de>"

lazy val piName  = s"$baseName-Pi"
lazy val piNameL = piName.toLowerCase

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

