// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository 
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// Use the Play sbt plugin for Play projects
<<<<<<< Upstream, based on play-2.2
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.0")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")
=======
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.0")
>>>>>>> 8d0afe8 Upgraded to play 2.2. Compile errors are all fixed plus a few runtime errors. Current runtime error is:
