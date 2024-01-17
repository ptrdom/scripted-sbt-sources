package me.ptrdom.sbt.scripted.sources

import java.nio.file.Files

import sbt.*
import sbt.AutoPlugin
import sbt.Keys.*
import sbt.ScriptedPlugin
import sbt.ScriptedPlugin.autoImport.sbtTestDirectory
import sbt.ScriptedPlugin.autoImport.scripted
import sbt.internal.util.ManagedLogger

import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.Using

object ScriptedSourcesPlugin extends AutoPlugin {
  override def requires = ScriptedPlugin

  object autoImport {
    val scriptedSource = taskKey[Boolean]("")
    val scriptedSourceCheck = taskKey[Unit]("")
  }

  import autoImport.*

  private val sourceConfigFileName = ".source"

  // regex for sbt plugin version
  // [\"](.+)[\"]\s*[%]\s*[\"](.+)[\"]\s*[%]\s*[\"](.+)[\"]

  def runScriptedSource(
      dry: Boolean,
      log: ManagedLogger
  )(baseDirectoryV: File, sbtTestDirectoryV: File) = {
    Using(
      Files.walk(sbtTestDirectoryV.toPath)
    )(
      _.iterator().asScala
        .foldLeft(true) { case (pristine, testDirectory) =>
          val sourceConfig =
            new File(testDirectory.toFile, sourceConfigFileName)
          if (!sourceConfig.exists()) {
            log.debug(
              s"scripted source config missing [${sourceConfig.absolutePath}]"
            )
            pristine
          } else {
            val sourcesForTest: Seq[String] =
              Using(Source.fromFile(sourceConfig)) { source =>
                source.getLines().toList
              }.fold(
                ex =>
                  throw new RuntimeException(
                    s"Failed to read source config [${sourceConfig.getAbsolutePath}]",
                    ex
                  ),
                identity
              )

            sourcesForTest
              .foldLeft(true) { case (pristine, sourceForTest) =>
                log.debug(
                  s"Source for test is [$sourceForTest]"
                )

                val sourceToTest = baseDirectoryV / sourceForTest
                if (!sourceToTest.exists()) {
                  sys.error(
                    s"Source for test is missing [${sourceToTest.getAbsolutePath}]"
                  )
                } else {
                  log.debug(
                    s"Source for test [${sourceToTest.getAbsolutePath}] exists"
                  )
                  Using(
                    Files
                      .walk(sourceToTest.toPath)
                  )(
                    _.iterator().asScala
                      .map(_.toFile)
                      .filter(
                        _.isFile
                      )
                      .foldLeft(true) { case (pristine, file) =>
                        val targetFile = new File(
                          file.getAbsolutePath.replace(
                            sourceToTest.getAbsolutePath,
                            testDirectory.toFile.getAbsolutePath
                          )
                        )
                        if (
                          !Hash(file).sameElements(
                            Hash(targetFile)
                          ) || !targetFile.exists()
                        ) {
                          log.debug(
                            s"File changed [${file.getAbsolutePath}], copying to [${targetFile.getAbsolutePath}]"
                          )
                          if (!dry) {
                            IO.copyFile(
                              file,
                              targetFile
                            )
                          }
                          false
                        } else {
                          log.debug(
                            s"File not changed [${file.getAbsolutePath}]"
                          )
                          pristine
                        }
                      }
                  ).fold(ex => throw ex, identity)
                }
              }
          }
        }
    )
      .fold(ex => throw ex, identity)
  }

  override lazy val projectSettings: Seq[Setting[?]] = Seq(
    scriptedSource := {
      val log = streams.value.log

      log.debug("Running scripted source")

      val baseDirectoryV = baseDirectory.value
      val sbtTestDirectoryV = sbtTestDirectory.value

      runScriptedSource(dry = false, log)(baseDirectoryV, sbtTestDirectoryV)
    },
    scriptedSourceCheck := {
      val log = streams.value.log

      log.debug("Running scripted source check")

      val baseDirectoryV = baseDirectory.value
      val sbtTestDirectoryV = sbtTestDirectory.value

      val pristine =
        runScriptedSource(dry = true, log)(baseDirectoryV, sbtTestDirectoryV)
      if (pristine) {} else {
        sys.error("Scripted sources not in sync!")
      }
    },
    scripted := scripted.dependsOn(scriptedSource).evaluated
    // TODO add examples to `scripted / watchTriggers`
  )
}
