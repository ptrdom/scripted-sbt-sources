addSbtPlugin(
  "basic-plugin-project" % "basic-plugin-project" % sys.props.getOrElse(
    "plugin.version",
    sys.error("'plugin.version' environment variable is not set")
  )
)
