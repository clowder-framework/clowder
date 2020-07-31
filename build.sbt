lazy val root = (project in file(".")).enablePlugins(PlayScala)
//  .settings(
    name := "clowder"

    scalaVersion := "2.11.12"

//    TwirlKeys.useOldParser in Compile := true

    routesImport += "models._"
    routesImport += "Binders._"

    libraryDependencies ++= Seq(
      guice,
      ws,
      "org.joda" % "joda-convert" % "2.2.1",
      "net.logstash.logback" % "logstash-logback-encoder" % "6.2",
//      "io.lemonlabs" %% "scala-uri" % "1.5.1",
      "net.codingwell" %% "scala-guice" % "4.2.6",
      "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,


      "ws.securesocial" %% "securesocial" % "3.0-M8",
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

      // JSONparser and JSONObject
      "com.googlecode.json-simple" % "json-simple" % "1.1.1",
      "org.codeartisans" % "org.json" % "20131017",

      // Testing framework
//      "org.scalatestplus" % "play_2.10" % "1.0.0" % "test",

      // iRods filestorage
//      "org.irods.jargon" % "jargon-core" % "3.3.3-alpha1",

      // jsonp return from /api
//      "org.julienrf" %% "play-jsonp-filter" % "1.1",

      // Official AWS Java SDK
      "com.amazonaws" % "aws-java-sdk-bom" % "1.11.106",

      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.106"
    )

    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-Xfatal-warnings"
    )
//  )

