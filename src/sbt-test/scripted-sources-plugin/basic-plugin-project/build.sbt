import sbt.scripted.sources.ScriptedSourcesPlugin

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin, ScriptedSourcesPlugin)
  .settings(
    name := "basic-plugin-project",
    scriptedLaunchOpts ++= Seq(
      "-Dplugin.version=" + version.value
    ),
    scriptedBufferLog := false
  )
