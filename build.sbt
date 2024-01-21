inThisBuild(
  List(
    scalaVersion := "2.12.18",
    organization := "me.ptrdom",
    homepage := Some(url("https://github.com/ptrdom/sbt-scripted-sources")),
    licenses := List(License.MIT),
    developers := List(
      Developer(
        "ptrdom",
        "Domantas Petrauskas",
        "dom.petrauskas@gmail.com",
        url("https://ptrdom.me/")
      )
    ),
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
    versionScheme := Some("semver-spec")
  )
)

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "scripted-sbt-sources",
    scriptedLaunchOpts ++= Seq(
      "-Dplugin.version=" + version.value
    ),
    scriptedBufferLog := false
  )
