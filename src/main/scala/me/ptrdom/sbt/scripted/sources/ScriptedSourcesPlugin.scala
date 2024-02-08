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
    val scriptedSourcesSbtTestDirectory = settingKey[File]("")
    val scriptedSourcesSync =
      taskKey[Boolean]("Sync scripted tests with their sources")
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
      log: ManagedLogger
  )(baseDirectoryV: File, sbtTestDirectoryV: File): Boolean = {
    processScriptedSources(log)(baseDirectoryV, sbtTestDirectoryV)(_.flatMap {
      case (testDirectory, sourcesForTest) =>
        sourcesForTest.map(sourceForTest => (testDirectory, sourceForTest))
    }
      .foldLeft(true) { case (pristine, (testDirectory, sourceForTest)) =>
        if (
          !copyDirectory(log)(
            sourceForTest,
            testDirectory.toFile,
            sourcePriority = false
          )
        ) false
        else pristine
      })
  }

  private def copyDirectory(
      log: ManagedLogger
  )(sourceDirectory: File, targetDirectory: File, sourcePriority: Boolean) = {
    Using(
      Files
        .walk(sourceDirectory.toPath)
    )(
      _.iterator().asScala
        .map(_.toFile)
        .filter(
          _.isFile
        )
        .foldLeft(true) { case (pristine, file) =>
          val targetFile = new File(
            file.getAbsolutePath.replace(
              sourceDirectory.getAbsolutePath,
              targetDirectory.getAbsolutePath
            )
          )
          if (
            (sourcePriority && !Hash(file).sameElements(
              Hash(targetFile)
            )) || !targetFile.exists()
          ) {
            log.info(
              s"File changed [${file.getAbsolutePath}], copying to [${targetFile.getAbsolutePath}]"
            )
            IO.copyFile(
              file,
              targetFile
            )
            false
          } else {
            log.info(
              s"File not changed [${file.getAbsolutePath}]"
            )
            pristine
          }
        }
    ).fold(ex => throw ex, identity)
  }

  override lazy val projectSettings: Seq[Setting[?]] = Seq(
    sbtTestDirectory := target.value / "generated-sbt-test",
    scriptedSourcesSbtTestDirectory := sourceDirectory.value / "sbt-test",
    scripted / watchTriggers += Glob(
      scriptedSourcesSbtTestDirectory.value,
      RecursiveGlob
    ),
    scriptedSourcesSync := {
      val log = streams.value.log

      log.debug("Running scripted sources sync")

      val baseDirectoryV = baseDirectory.value
      val scriptedSourcesSbtTestDirectoryV =
        scriptedSourcesSbtTestDirectory.value
      val sbtTestDirectoryV = sbtTestDirectory.value

      sbtTestDirectoryV.mkdirs()

      Seq(
        copyDirectory(log)(
          scriptedSourcesSbtTestDirectoryV,
          sbtTestDirectoryV,
          sourcePriority = true
        ),
        runScriptedSource(log)(
          baseDirectoryV,
          sbtTestDirectoryV
        )
      ).reduce(_ && _)
    },
    scripted := scripted.dependsOn(scriptedSourcesSync).evaluated,
    scripted / watchTriggers ++= {
      val log = Keys.sLog.value

      log.debug("Setting scripted sources as watch triggers")

      val baseDirectoryV = baseDirectory.value
      val scriptedSourcesSbtTestDirectoryV =
        scriptedSourcesSbtTestDirectory.value

      processScriptedSources(log)(
        baseDirectoryV,
        scriptedSourcesSbtTestDirectoryV
      )(
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
