package me.ptrdom.sbt.scripted.examples

import java.nio.file.Files
import java.nio.file.Paths

import sbt.AutoPlugin
import sbt.ScriptedPlugin
import sbt.*
import sbt.Keys.*
import sbt.ScriptedPlugin.autoImport.sbtTestDirectory
import sbt.ScriptedPlugin.autoImport.scripted

import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.Using

class ScriptedSourcesPlugin extends AutoPlugin {
  override def requires = ScriptedPlugin

  object autoImport {}

  import autoImport.*

  private val exampleConfigFileName = ".example"

  private val scriptedExamplesTask = Def.task {
    val log = streams.value.log

    val baseDirectoryV = baseDirectory.value
    val sbtTestDirectoryV = sbtTestDirectory.value
    Files
      .list(sbtTestDirectoryV.toPath)
      .iterator()
      .asScala
      .foreach { testDirectory =>
        val exampleConfig =
          new File(testDirectory.toFile, exampleConfigFileName)
        if (!exampleConfig.exists()) {
          log.info(
            s"scripted example config missing [${exampleConfig.absolutePath}]"
          ) // TODO convert to debug
        } else {
          val exampleToTest = Using(Source.fromFile(exampleConfig)) { source =>
            source.getLines().take(1).mkString("")
          }.fold(
            ex =>
              throw new RuntimeException(
                s"Failed to read example config [${exampleConfig.getAbsolutePath}]",
                ex
              ),
            identity
          )
          log.info(
            s"Example to test is [$exampleToTest]"
          ) // TODO convert to debug

          val exampleToTestSource = baseDirectoryV / exampleToTest
          if (exampleToTestSource.exists()) {
            sys.error(
              s"Source for example to test is missing [${exampleToTestSource.getAbsolutePath}]"
            )
          } else {
            log.info(
              s"Source for example to test is [${exampleToTestSource.getAbsolutePath}]"
            ) // TODO convert to debug
          }
        }
      }
  }

  private def copyChanges(
      logger: Logger
  )(
      sourceDirectory: File,
      targetDirectory: File,
      currentDirectory: File
  ): Unit = {
    logger.debug(s"Walking directory [${currentDirectory.getAbsolutePath}]")
    Files
      .walk(currentDirectory.toPath)
      .iterator()
      .asScala
      .map(_.toFile)
      .filter(file => file.getAbsolutePath != currentDirectory.getAbsolutePath)
      .foreach { file =>
        if (file.isDirectory) {
          copyChanges(logger)(sourceDirectory, targetDirectory, file)
        } else {
          val targetFile = new File(
            file.getAbsolutePath.replace(
              sourceDirectory.getAbsolutePath,
              targetDirectory.getAbsolutePath
            )
          )
          if (!Hash(file).sameElements(Hash(targetFile))) {
            logger.debug(
              s"File changed [${file.getAbsolutePath}], copying to [${targetFile.getAbsolutePath}]"
            )
            IO.copyFile(
              file,
              targetFile
            )
          } else {
            logger.debug(s"File not changed [${file.getAbsolutePath}]")
          }
        }
      }
  }

  override lazy val projectSettings: Seq[Setting[?]] = {
    scripted := scripted.dependsOn(scriptedExamplesTask).value
  }
}
