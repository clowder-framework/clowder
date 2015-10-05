import com.typesafe.sbt.packager.Keys._
import sbt._
import Keys._
import play.Project._
import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._
//import com.typesafe.sbt.SbtLicenseReport.autoImportImpl._
//import com.typesafe.sbt.license.LicenseCategory
//import com.typesafe.sbt.license.LicenseInfo
//import com.typesafe.sbt.license.DepModuleInfo
//import com.typesafe.sbt.license.Html

object ApplicationBuild extends Build {

  val appName = "clowder"
  val version = "0.9.1"

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
    try {
      val hash = exec("git rev-parse --short HEAD")
      assert(hash.length == 1)
      hash(0)
    } catch {
      case e: Exception => "N/A"
    }
  }

  def gitBranchName: String = {
    try {
      val branch = exec("git rev-parse --abbrev-ref HEAD")
      assert(branch.length == 1)
      if (branch(0) == "HEAD") return "detached"
      branch(0)
    } catch {
      case e: Exception => "N/A"
    }
  }

  def getBambooBuild: String = {
    sys.env.getOrElse("bamboo_buildNumber", default = "local")
  }

  val appDependencies = Seq(
    filters,
    // login
    "ws.securesocial" %% "securesocial" % "2.1.3" exclude("org.scala-stm", "scala-stm_2.10.0"),

    // messagebus
    "com.rabbitmq" % "amqp-client" % "3.0.0",

    // indexing
    "org.elasticsearch" % "elasticsearch" % "1.3.4",

    // mongo storage
    "com.novus" %% "salat" % "1.9.5" exclude("org.scala-stm", "scala-stm_2.10.0"),
    "org.mongodb" %% "casbah" % "2.6.3",

    // geostreams
    "org.postgresql" % "postgresql" % "9.4-1203-jdbc41",

    // Documentation
    "com.wordnik" %% "swagger-play2" % "1.2.6-SNAPSHOT" exclude("org.scala-stm", "scala-stm_2.10.0"),

    // Find listing of previewers/stylesheets at runtime
    //  servlet is needed here since it is not specified in org.reflections.
    "javax.servlet" % "servlet-api" % "2.5",
    "org.reflections" % "reflections" % "0.9.10",

    // RDF
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

    // ??
    "commons-io" % "commons-io" % "2.4",
    "commons-logging" % "commons-logging" % "1.1.1",

    // RDF
    "gr.forth.ics" % "flexigraph" % "1.0",

    // Guice dependency injection
    "com.google.inject" % "guice" % "3.0",

    // ??
    "org.apache.httpcomponents" % "httpclient" % "4.2.3",
    "org.apache.httpcomponents" % "httpcore" % "4.2.3",
    "org.apache.httpcomponents" % "httpmime" % "4.2.3",

    // JSONparser and JSONObject
    "com.googlecode.json-simple" % "json-simple" % "1.1.1",
    "org.codeartisans" % "org.json" % "20131017",

    // Testing framework
    "org.scalatestplus" % "play_2.10" % "1.0.0" % "test",

    // iRods filestorage
    "org.irods.jargon" % "jargon-core" % "3.3.3-beta1",

    // jsonp return from /api
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
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/scalatest-reports"),
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

    // same for development mode
    unmanagedClasspath in Runtime += baseDirectory.value / "custom",

    // add build number so we can use it in templates
    bashScriptExtraDefines += "addJava \"-Dbuild.version=" + version + "\"",
    bashScriptExtraDefines += "addJava \"-Dbuild.bamboo=" + getBambooBuild + "\"",
    bashScriptExtraDefines += "addJava \"-Dbuild.branch=" + gitBranchName + "\"",
    bashScriptExtraDefines += "addJava \"-Dbuild.gitsha1=" + gitShortHash + "\"",

    batScriptExtraDefines += "addJava \"-Dbuild.version=" + version + "\"",
    batScriptExtraDefines += "addJava \"-Dbuild.bamboo=" + getBambooBuild + "\"",
    batScriptExtraDefines += "addJava \"-Dbuild.branch=" + gitBranchName + "\"",
    batScriptExtraDefines += "addJava \"-Dbuild.gitsha1=" + gitShortHash + "\""

    // license report
//    licenseReportTitle := "Medici Licenses",
//    licenseConfigurations := Set("compile", "provided"),
//    licenseSelection := Seq(LicenseCategory("NCSA"), LicenseCategory("Apache")) ++ LicenseCategory.all,
//    licenseOverrides := licenseOverrides.value orElse {
//      case DepModuleInfo("com.rabbitmq", "amqp-client", _) => LicenseInfo(LicenseCategory.Mozilla, "Mozilla Public License v1.1", "https://www.rabbitmq.com/mpl.html")
//      case DepModuleInfo("com.typesafe.play", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
//      case DepModuleInfo("org.apache.lucene", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
//      // The word Aduna with capital A will crash dumpLicenseReport, no idea why.
//      case DepModuleInfo("org.openrdf.sesame", _, _) => LicenseInfo(LicenseCategory.BSD, "aduna BSD license", "http://repo.aduna-software.org/legal/aduna-bsd.txt")
//      case DepModuleInfo("org.reflections", _, _) => LicenseInfo(LicenseCategory.PublicDomain, "WTFPL", "http://www.wtfpl.net/txt/copying")
//    },
//    licenseReportMakeHeader := {
//      case Html => Html.header1(licenseReportTitle.value) + "<p>Medici is licensed under the <a href='http://opensource.org/licenses/NCSA'>University of Illinois/NCSA Open Source License</a>.</p><p>Below are the libraries that Medici depends on and their licenses.<br></p>"
//      case l => l.header1(licenseReportTitle.value)
//    }

  ).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
}

