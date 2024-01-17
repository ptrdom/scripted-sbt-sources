package me.ptrdom.sbt.scripted.sources

import java.nio.file.Files

import sbt.*
import sbt.AutoPlugin
import sbt.Keys.*
import sbt.ScriptedPlugin
import sbt.ScriptedPlugin.autoImport.sbtTestDirectory
import sbt.ScriptedPlugin.autoImport.scripted

import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.Using

object ScriptedSourcesPlugin extends AutoPlugin {
  override def requires = ScriptedPlugin

  object autoImport {
    val scriptedSource = taskKey[Unit]("")
  }

  import autoImport.*

  private val sourceConfigFileName = ".source"

  // regex for sbt plugin version
  // [\"](.+)[\"]\s*[%]\s*[\"](.+)[\"]\s*[%]\s*[\"](.+)[\"]

  override lazy val projectSettings: Seq[Setting[?]] = Seq(
    scriptedSource := {
      val log = streams.value.log

      log.debug("Running scripted source")

      val baseDirectoryV = baseDirectory.value
      val sbtTestDirectoryV = sbtTestDirectory.value

      Using(
        Files.walk(sbtTestDirectoryV.toPath)
      )(
        _.iterator().asScala
          .foreach { testDirectory =>
            val sourceConfig =
              new File(testDirectory.toFile, sourceConfigFileName)
            if (!sourceConfig.exists()) {
              log.debug(
                s"scripted source config missing [${sourceConfig.absolutePath}]"
              )
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
                .foreach { sourceForTest =>
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
                        .foreach { file =>
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
                            IO.copyFile(
                              file,
                              targetFile
                            )
                          } else {
                            log.debug(
                              s"File not changed [${file.getAbsolutePath}]"
                            )
                          }
                        }
                    )
                  }
                }
            }
          }
      )
    },
    scripted := scripted.dependsOn(scriptedSource).evaluated
    // TODO add examples to `scripted / watchTriggers`
  )
}
