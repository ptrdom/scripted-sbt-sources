package me.ptrdom.sbt.scripted.sources

import java.nio.file.Files
import java.nio.file.Path

import sbt.*
import sbt.AutoPlugin
import sbt.Keys.*
import sbt.ScriptedPlugin
import sbt.ScriptedPlugin.autoImport.sbtTestDirectory
import sbt.ScriptedPlugin.autoImport.scripted
import sbt.internal.util.ManagedLogger
import sbt.nio.Keys.watchTriggers

import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.Using

object ScriptedSourcesPlugin extends AutoPlugin {
  override def requires = ScriptedPlugin

  object autoImport {
    val scriptedSourcesSync =
      taskKey[Boolean]("Sync scripted tests with their sources")
    val scriptedSourcesCheck =
      taskKey[Unit]("Check if scripted tests and their sources are in sync")
  }

  import autoImport.*

  private val sourcesConfigFileName = ".sources"

  private def processScriptedSources[T](
      log: Logger
  )(
      baseDirectoryV: File,
      sbtTestDirectoryV: File
  )(
      handler: Iterator[(Path, Set[sbt.File])] => T
  ): T = {
    Using(
      Files.walk(sbtTestDirectoryV.toPath)
    ) { stream =>
      val dataForHandler = stream
        .iterator()
        .asScala
        .flatMap { testDirectory =>
          val sourcesConfig =
            new File(testDirectory.toFile, sourcesConfigFileName)
          if (!sourcesConfig.exists()) {
            log.debug(
              s"scripted sources config missing [${sourcesConfig.absolutePath}]"
            )
            List.empty
          } else {
            val sourcesForTest: Set[File] =
              Using(Source.fromFile(sourcesConfig)) { source =>
                source
                  .getLines()
                  .map { sourceForTest =>
                    val sourceForTestDirectory = baseDirectoryV / sourceForTest
                    if (!sourceForTestDirectory.exists()) {
                      sys.error(
                        s"Source for test is missing [${sourceForTestDirectory.getAbsolutePath}]"
                      )
                    } else {
                      log.debug(
                        s"Source for test [${sourceForTestDirectory.getAbsolutePath}] exists"
                      )
                      sourceForTestDirectory
                    }
                  }
                  .toSet
              }.fold(
                ex =>
                  throw new RuntimeException(
                    s"Failed to read source config [${sourcesConfig.getAbsolutePath}]",
                    ex
                  ),
                identity
              )
            List((testDirectory, sourcesForTest))
          }
        }
      handler(dataForHandler)
    }
      .fold(ex => throw ex, identity)
  }

  private def runScriptedSource(
      dry: Boolean,
      log: ManagedLogger
  )(baseDirectoryV: File, sbtTestDirectoryV: File): Boolean = {
    processScriptedSources(log)(baseDirectoryV, sbtTestDirectoryV)(_.flatMap {
      case (testDirectory, sourcesForTest) =>
        sourcesForTest.map(sourceForTest => (testDirectory, sourceForTest))
    }
      .foldLeft(true) { case (pristine, (testDirectory, sourceForTest)) =>
        Using(
          Files
            .walk(sourceForTest.toPath)
        )(
          _.iterator().asScala
            .map(_.toFile)
            .filter(
              _.isFile
            )
            .foldLeft(pristine) { case (pristine, file) =>
              val targetFile = new File(
                file.getAbsolutePath.replace(
                  sourceForTest.getAbsolutePath,
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
      })
  }

  override lazy val projectSettings: Seq[Setting[?]] = Seq(
    scriptedSourcesSync := {
      val log = streams.value.log

      log.debug("Running scripted sources sync")

      val baseDirectoryV = baseDirectory.value
      val sbtTestDirectoryV = sbtTestDirectory.value

      runScriptedSource(dry = false, log)(baseDirectoryV, sbtTestDirectoryV)
    },
    scriptedSourcesCheck := {
      val log = streams.value.log

      log.debug("Running scripted sources check")

      val baseDirectoryV = baseDirectory.value
      val sbtTestDirectoryV = sbtTestDirectory.value

      val pristine =
        runScriptedSource(dry = true, log)(baseDirectoryV, sbtTestDirectoryV)
      if (!pristine) {
        sys.error("Scripted sources not in sync!")
      }
    },
    scripted := scripted.dependsOn(scriptedSourcesSync).evaluated,
    scripted / watchTriggers ++= {
      val log = Keys.sLog.value

      log.debug("Setting scripted sources as watch triggers")

      val baseDirectoryV = baseDirectory.value
      val sbtTestDirectoryV = sbtTestDirectory.value

      processScriptedSources(log)(baseDirectoryV, sbtTestDirectoryV)(
        _.flatMap { case (_, sourcesForTest) =>
          sourcesForTest
            .map { sourceForTest =>
              Glob(sourceForTest, RecursiveGlob)
            }
        }.toList
      )
    }
  )
}
