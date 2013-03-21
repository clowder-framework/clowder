import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "medici-play"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      "com.novus" %% "salat" % "1.9.2-SNAPSHOT",
      "securesocial" % "securesocial" % "master-SNAPSHOT",
      "com.rabbitmq" % "amqp-client" % "3.0.0",
      "org.elasticsearch" % "elasticsearch" % "0.20.1",
      "com.spatial4j" % "spatial4j" % "0.3",
      "org.mongodb" %% "casbah" % "2.5.0"
//      "org.scalaj" %% "scalaj-collection" % "1.2"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
      routesImport += "Binders._",
      templatesImport += "org.bson.types.ObjectId",
      resolvers += Resolver.url("sbt-plugin-releases", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns),
      resolvers += Resolver.url("sbt-plugin-snapshots", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots/"))(Resolver.ivyStylePatterns),
      resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    )
}
