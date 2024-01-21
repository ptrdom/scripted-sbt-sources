import org.scalatest.Assertions.succeed

TaskKey[Unit]("testBuildDependencies") := {
  streams.value.log.info("Running testBuildDependencies")
  succeed
  ()
}
