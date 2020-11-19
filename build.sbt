lazy val root = (project in file(".")).enablePlugins(PlayScala)
//  .settings(
    name := "clowder"

    scalaVersion := "2.12.3"

//    TwirlKeys.useOldParser in Compile := true

    routesImport += "models._"

    routesImport += "Binders._"

    resolvers += Resolver.jcenterRepo

    resolvers += "dice.repository" at "https://raw.github.com/DICE-UNC/DICE-Maven/master/releases"

    resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

    resolvers in ThisBuild += "Atlassian Releases" at "https://maven.atlassian.com/public/"

    libraryDependencies ++= Seq(
      guice,
      ws,
      "org.joda" % "joda-convert" % "2.2.1",
      "net.logstash.logback" % "logstash-logback-encoder" % "6.2",
//      "io.lemonlabs" %% "scala-uri" % "1.5.1",
      "net.codingwell" %% "scala-guice" % "4.2.6",
      "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,


      //"ws.securesocial" %% "securesocial" % "3.0-M8",
      "com.unboundid" % "unboundid-ldapsdk" % "4.0.1",

      // messagebus
      "com.rabbitmq" % "amqp-client" % "3.0.0",

      // indexing
      "org.elasticsearch" % "elasticsearch" % "7.5.2" exclude("io.netty", "netty"),
      "org.elasticsearch.client" % "elasticsearch-rest-client" % "7.5.2",
      "org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % "7.5.2",

      // mongo storage
      "com.github.salat" %% "salat" % "1.11.2" pomOnly(),
//      "org.mongodb" %% "casbah" % "2.6.3",

      // Find listing of previewers/stylesheets at runtime
      //  servlet is needed here since it is not specified in org.reflections.
      "javax.servlet" % "servlet-api" % "2.5",
      "org.reflections" % "reflections" % "0.9.10",

      // ??
      "commons-lang" % "commons-lang" % "2.6",
      "commons-io" % "commons-io" % "2.4",
      "commons-logging" % "commons-logging" % "1.1.3",

      // Guice dependency injection
//      "com.google.inject" % "guice" % "3.0",

      // ??
      "org.apache.httpcomponents" % "httpclient" % "4.2.3",
      "org.apache.httpcomponents" % "httpcore" % "4.2.3",
      "org.apache.httpcomponents" % "httpmime" % "4.2.3",

      // Mailing
      "com.typesafe.play" %% "play-mailer" % "6.0.1",
      "com.typesafe.play" %% "play-mailer-guice" % "6.0.1",

      // JSONparser and JSONObject
      "com.googlecode.json-simple" % "json-simple" % "1.1.1",
      "org.codeartisans" % "org.json" % "20131017",

      // Testing framework
//      "org.scalatestplus" % "play_2.10" % "1.0.0" % "test",

      // iRods filestorage
      "org.irods.jargon" % "jargon-core" % "4.3.1.0-RELEASE",

      // jsonp return from /api
//      "org.julienrf" %% "play-jsonp-filter" % "1.1",

      // Official AWS Java SDK
      "com.amazonaws" % "aws-java-sdk-bom" % "1.11.106",

      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.106",

      // Silhouette
      "com.mohiva" %% "play-silhouette" % "5.0.0",
      "com.mohiva" %% "play-silhouette-password-bcrypt" % "5.0.0",
      "com.mohiva" %% "play-silhouette-persistence" % "5.0.0",
      "com.mohiva" %% "play-silhouette-crypto-jca" % "5.0.0",
      "org.webjars" %% "webjars-play" % "2.6.1",
      "org.webjars" % "bootstrap" % "3.3.7-1" exclude("org.webjars", "jquery"),
      "org.webjars" % "jquery" % "3.2.1",
      "net.codingwell" %% "scala-guice" % "4.1.0",
      "com.iheart" %% "ficus" % "1.4.1",
      //"com.enragedginger" %% "akka-quartz-scheduler" % "1.6.1-akka-2.5.x",
      //"com.adrianhurt" %% "play-bootstrap" % "1.2-P26-B3",
      //"com.mohiva" %% "play-silhouette-testkit" % "5.0.0" % "test"
    )

    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-Xfatal-warnings"
    )
//  )

