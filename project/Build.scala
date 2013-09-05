import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "medici-play"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      "com.novus" %% "salat" % "1.9.2",
      "securesocial" % "securesocial" % "master-SNAPSHOT",
      "com.rabbitmq" % "amqp-client" % "3.0.0",
      "org.elasticsearch" % "elasticsearch" % "0.90.2",
      "com.spatial4j" % "spatial4j" % "0.3",
      "org.mongodb" %% "casbah" % "2.6.2",
      "postgresql" % "postgresql" % "9.1-901.jdbc4",
       "com.wordnik" %% "swagger-play2" % "1.2.1-SNAPSHOT"
//      "org.scalaj" %% "scalaj-collection" % "1.2"
    )
    
    // Only compile the bootstrap bootstrap.less file and any other *.less file in the stylesheets directory 
	def customLessEntryPoints(base: File): PathFinder = ( 
	    (base / "app" / "assets" / "stylesheets" / "bootstrap" * "bootstrap.less") +++
	    (base / "app" / "assets" / "stylesheets" / "bootstrap" * "responsive.less") +++ 
	    (base / "app" / "assets" / "stylesheets" * "*.less")
	)

    val main = play.Project(appName, appVersion, appDependencies).settings(
      lessEntryPoints <<= baseDirectory(customLessEntryPoints),
      routesImport += "Binders._",
      templatesImport += "org.bson.types.ObjectId",
      resolvers += Resolver.url("sbt-plugin-releases", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns),
      resolvers += Resolver.url("sbt-plugin-snapshots", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots/"))(Resolver.ivyStylePatterns),
      resolvers += "Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots"
    )
}
