resolvers += "Spray Repository" at "http://repo.spray.cc/"

// need scalatest also as a build dependency: the build implements a custom reporter
libraryDependencies += "org.scalatest" %% "scalatest" % "2.0"

scalacOptions ++= Seq("-deprecation")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.4.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8.1")

// for dependency-graph plugin
// net.virtualvoid.sbt.graph.Plugin.graphSettings

