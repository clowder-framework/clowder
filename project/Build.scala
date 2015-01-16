import com.typesafe.sbt.packager.Keys._
import sbt._
import Keys._
import play.Project._
import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._

object ApplicationBuild extends Build {

  val appName = "medici-play"
  val version = "2.0.0"

  def appVersion: String = {
    if (gitBranchName == "master") {
      version
    } else {
      s"${version}-SNAPSHOT"
    }
  }

  def exec(cmd: String): Seq[String] = {
    val r = java.lang.Runtime.getRuntime()
    val p = r.exec(cmd)
    p.waitFor()
    val ret = p.exitValue()
    if (ret != 0) {
      sys.error("Command failed: " + cmd)
    }
    val is = p.getInputStream
    val res = scala.io.Source.fromInputStream(is).getLines()
    res.toSeq
  }

  def gitShortHash: String = {
    val hash = exec("git rev-parse --short HEAD")
    assert(hash.length == 1)
    hash(0)
  }

  def gitBranchName: String = {
    val branch = exec("git rev-parse --abbrev-ref HEAD")
    assert(branch.length == 1)
    if (branch(0) == "HEAD") return "detached"
    branch(0)
  }

  def getBambooBuild: String = {
    sys.env.getOrElse("bamboo_buildNumber", default = "local")
  }

  val appDependencies = Seq(
  	filters,
    "com.novus" %% "salat" % "1.9.5" exclude("org.scala-stm", "scala-stm_2.10.0"),
    "ws.securesocial" %% "securesocial" % "2.1.3" exclude("org.scala-stm", "scala-stm_2.10.0"),
    "com.rabbitmq" % "amqp-client" % "3.0.0",
    "org.elasticsearch" % "elasticsearch" % "1.3.4",
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
    "com.netflix.astyanax" % "astyanax-core" % "1.56.43" exclude("org.jboss.netty", "netty"),
    "com.netflix.astyanax" % "astyanax-thrift" % "1.56.43" exclude("org.slf4j", "slf4j-log4j12") exclude("org.jboss.netty", "netty"),
    "com.netflix.astyanax" % "astyanax-cassandra" % "1.56.43" exclude("org.slf4j", "slf4j-log4j12") exclude("org.jboss.netty", "netty") ,
    "com.netflix.astyanax" % "astyanax-recipes" % "1.56.43" exclude("org.slf4j", "slf4j-log4j12") exclude("org.jboss.netty", "netty"),
    "org.apache.httpcomponents" % "httpclient" % "4.2.3",
    "org.apache.httpcomponents" % "httpcore" % "4.2.3",
    "org.apache.httpcomponents" % "httpmime" % "4.2.3",
    "com.googlecode.json-simple" % "json-simple" % "1.1.1",
    "log4j" % "log4j" % "1.2.14",
    "org.codeartisans" % "org.json" % "20131017",
    "postgresql" % "postgresql" % "8.1-407.jdbc3",
    "org.postgresql" % "com.springsource.org.postgresql.jdbc4" % "8.3.604",
    "org.springframework" % "spring" % "2.5.6",
    "org.scalatestplus" % "play_2.10" % "1.0.0" % "test",
    "org.irods.jargon" % "jargon-core" % "3.3.3-beta1",
    "org.julienrf" %% "play-jsonp-filter" % "1.1"
  )


  // Only compile the bootstrap bootstrap.less file and any other *.less file in the stylesheets directory 
  def customLessEntryPoints(base: File): PathFinder = (
    (base / "app" / "assets" / "stylesheets" / "bootstrap" * "bootstrap.less") +++
    (base / "app" / "assets" / "stylesheets" / "bootstrap" * "responsive.less") +++
    (base / "app" / "assets" / "stylesheets" * "*.less")
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    offline := true,
    lessEntryPoints <<= baseDirectory(customLessEntryPoints),
    javaOptions in Test += "-Dconfig.file=" + Option(System.getProperty("config.file")).getOrElse("conf/application.conf"),
    testOptions in Test := Nil, // overwrite spec2 config to use scalatest instead
    routesImport += "models._",
    routesImport += "Binders._",
    templatesImport += "org.bson.types.ObjectId",
    resolvers += Resolver.url("sbt-plugin-releases", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns),
    resolvers += Resolver.url("sbt-plugin-snapshots", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots/"))(Resolver.ivyStylePatterns),
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    resolvers += "Aduna" at "http://maven-us.nuxeo.org/nexus/content/repositories/public/",
    //resolvers += "Forth" at "http://139.91.183.63/repository",
    resolvers += "NCSA" at "https://opensource.ncsa.illinois.edu/nexus/content/repositories/thirdparty",   
    resolvers += "opencastproject" at "http://repository.opencastproject.org/nexus/content/repositories/public",

    // add custom folder to the classpath, use this to add/modify medici:
    // custom/public/stylesheets/themes     - for custom themes
    // custom/public/javascripts/previewers - for custom previewers
    // custom/custom.conf                   - to customize application.conf
    scriptClasspath += "../custom",

    // add build number so we can use it in templates
    bashScriptExtraDefines += "addJava \"-Dbuild.version=" + version + "\"",
    bashScriptExtraDefines += "addJava \"-Dbuild.bamboo=" + getBambooBuild + "\"",
    bashScriptExtraDefines += "addJava \"-Dbuild.branch=" + gitBranchName + "\"",
    bashScriptExtraDefines += "addJava \"-Dbuild.gitsha1=" + gitShortHash + "\"",

    batScriptExtraDefines += "addJava \"-Dbuild.version=" + version + "\"",
    batScriptExtraDefines += "addJava \"-Dbuild.bamboo=" + getBambooBuild + "\"",
    batScriptExtraDefines += "addJava \"-Dbuild.branch=" + gitBranchName + "\"",
    batScriptExtraDefines += "addJava \"-Dbuild.gitsha1=" + gitShortHash + "\""

  ).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
}

