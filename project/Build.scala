import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName = "medici-play"
  val appVersion = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    "com.novus" %% "salat" % "1.9.2" exclude("org.scala-stm", "scala-stm_2.10.0"),
    "securesocial" %% "securesocial" % "master-SNAPSHOT" exclude("org.scala-stm", "scala-stm_2.10.0"),
    "com.rabbitmq" % "amqp-client" % "3.0.0",
    "org.elasticsearch" % "elasticsearch" % "0.90.2",
    "com.spatial4j" % "spatial4j" % "0.3",
    "org.mongodb" %% "casbah" % "2.6.3",
    "postgresql" % "postgresql" % "9.1-901.jdbc4",
    "com.wordnik" %% "swagger-play2" % "1.2.6-SNAPSHOT" exclude("org.scala-stm", "scala-stm_2.10.0"),
    "org.reflections" % "reflections" % "0.9.9-RC1",
    "com.google.code.findbugs" % "jsr305" % "2.0.1",
    "org.openrdf.sesame" % "sesame-rio-api" % "2.7.8",
    "org.openrdf.sesame" % "sesame-model" % "2.7.8",
    "org.openrdf.sesame" % "sesame-rio-n3" % "2.7.8",
    "org.openrdf.sesame" % "sesame-rio-ntriples" % "2.7.8",
    "org.openrdf.sesame" % "sesame-rio-rdfxml" % "2.7.8",
    "org.openrdf.sesame" % "sesame-rio-trig" % "2.7.8",
    "org.openrdf.sesame" % "sesame-rio-trix" % "2.7.8",
    "org.openrdf.sesame" % "sesame-rio-turtle" % "2.7.8",
    "com.google.inject" % "guice" % "3.0",
    "com.google.inject.extensions" % "guice-assistedinject" % "3.0",
    "com.netflix.astyanax" % "astyanax-core" % "1.56.43",
    "com.netflix.astyanax" % "astyanax-thrift" % "1.56.43",
    "com.netflix.astyanax" % "astyanax-cassandra" % "1.56.43",
    "com.netflix.astyanax" % "astyanax-recipes" % "1.56.43"
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
  ).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
}
