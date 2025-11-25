inThisBuild(
  List(
    scalaVersion := "2.13.18",
    organization := "me.ptrdom",
    homepage := Some(url("https://github.com/ptrdom/scripted-sbt-sources")),
    licenses := List(License.MIT),
    developers := List(
      Developer(
        "ptrdom",
        "Domantas Petrauskas",
        "dom.petrauskas@gmail.com",
        url("https://ptrdom.me/")
      )
    ),
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
