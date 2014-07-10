import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName = "medici-play"
  val appVersion = "1.0-SNAPSHOT"

  val appDependencies = Seq(
  	filters,
    "com.novus" %% "salat" % "1.9.5" exclude("org.scala-stm", "scala-stm_2.10.0"),
    "ws.securesocial" %% "securesocial" % "2.1.3" exclude("org.scala-stm", "scala-stm_2.10.0"),
    "com.rabbitmq" % "amqp-client" % "3.0.0",
    "org.elasticsearch" % "elasticsearch" % "0.90.2",
    "com.spatial4j" % "spatial4j" % "0.3",
    "org.mongodb" %% "casbah" % "2.6.3",
    "postgresql" % "postgresql" % "9.1-901.jdbc4",
    "com.wordnik" %% "swagger-play2" % "1.2.5" exclude("org.scala-stm", "scala-stm_2.10.0") exclude("play", "*"),
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
    "info.aduna.commons" % "aduna-commons-io" % "2.8.0",
    "info.aduna.commons" % "aduna-commons-lang" % "2.9.0",
    "info.aduna.commons" % "aduna-commons-net" % "2.7.0",
    "info.aduna.commons" % "aduna-commons-text" % "2.7.0",
    "info.aduna.commons" % "aduna-commons-xml" % "2.7.0",
    "commons-io" % "commons-io" % "2.4",
    "commons-logging" % "commons-logging" % "1.1.1",
    "gr.forth.ics" % "flexigraph" % "1.0",
    "com.google.inject" % "guice" % "3.0",
    "com.google.inject.extensions" % "guice-assistedinject" % "3.0",
    "com.netflix.astyanax" % "astyanax-core" % "1.56.43",
    "com.netflix.astyanax" % "astyanax-thrift" % "1.56.43" exclude("org.slf4j", "slf4j-log4j12"),
    "com.netflix.astyanax" % "astyanax-cassandra" % "1.56.43" exclude("org.slf4j", "slf4j-log4j12"),
    "com.netflix.astyanax" % "astyanax-recipes" % "1.56.43" exclude("org.slf4j", "slf4j-log4j12"),
    "org.apache.httpcomponents" % "httpclient" % "4.2.3",
    "org.apache.httpcomponents" % "httpcore" % "4.2.3",
    "org.apache.httpcomponents" % "httpmime" % "4.2.3",
    "com.googlecode.json-simple" % "json-simple" % "1.1.1",
    "log4j" % "log4j" % "1.2.14",
    "org.codeartisans" % "org.json" % "20131017",
    "postgresql" % "postgresql" % "8.1-407.jdbc3",
    "org.postgresql" % "com.springsource.org.postgresql.jdbc4" % "8.3.604",
    "org.springframework" % "spring" % "2.5.6",
    "org.scalatest" %% "scalatest" % "2.1.0" % "test"
  )

  // Only compile the bootstrap bootstrap.less file and any other *.less file in the stylesheets directory 
  def customLessEntryPoints(base: File): PathFinder = (
    (base / "app" / "assets" / "stylesheets" / "bootstrap" * "bootstrap.less") +++
    (base / "app" / "assets" / "stylesheets" / "bootstrap" * "responsive.less") +++
    (base / "app" / "assets" / "stylesheets" * "*.less")
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    lessEntryPoints <<= baseDirectory(customLessEntryPoints),
    testOptions in Test := Nil, // overwrite spec2 config to use scalatest instead
    routesImport += "models._",
    routesImport += "Binders._",
    templatesImport += "org.bson.types.ObjectId",
    resolvers += Resolver.url("sbt-plugin-releases", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns),
    resolvers += Resolver.url("sbt-plugin-snapshots", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots/"))(Resolver.ivyStylePatterns),
    resolvers += "Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
    resolvers += "Aduna" at "http://maven-us.nuxeo.org/nexus/content/repositories/public/",
    resolvers += "Forth" at "http://139.91.183.63/repository",
    resolvers += "opencastproject" at "http://repository.opencastproject.org/nexus/content/repositories/public"
  ).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
}
