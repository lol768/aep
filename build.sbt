import sbt.Path
import warwick.Changes

ThisBuild / organization := "uk.ac.warwick"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.1"

ThisBuild / javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
ThisBuild / scalacOptions ++= Seq(
  "-encoding", "UTF-8", // yes, this is 2 args
  "-target:jvm-1.8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Ywarn-numeric-widen",
  "-Xfatal-warnings",
  "-Xsource:2.13"
)
ThisBuild / Test / scalacOptions ++= Seq("-Yrangepos")
ThisBuild / Compile / doc / scalacOptions ++= Seq("-no-link-warnings")
ThisBuild / webpackEnabled := true

// Avoid some of the constant SBT "Updating"
updateOptions := updateOptions.value.withCachedResolution(true)

lazy val root = (project in file("."))
  .enablePlugins(WarwickProject, PlayScala)
  .settings(
    name := """onlineexams""",
    packageZipTarball in Universal := (packageZipTarball in Universal).dependsOn(webpack).value,
    libraryDependencies ++= (appDeps ++ testDeps).map(excludeBadTransitiveDeps),
    PlayKeys.devSettings := Seq("play.server.http.port" -> "8080"),
  )

// Separate project rather than an extra config on the root
// project - it's simpler overall.
lazy val integration = (project in file("it"))
  .dependsOn(root, root % "test->test") // get access to the common test classes
  .settings(
    libraryDependencies ++= Seq(
      "org.pegdown" % "pegdown" % "1.6.0" % Test, // For Scalatest HTML reports
      "uk.ac.warwick.play-utils" %% "testing" % playUtilsVersion,
    ),
    sourceDirectory := baseDirectory.value, // no "src" subfolder
    Test / testOptions ++= Seq(
      Tests.Argument(TestFrameworks.ScalaTest, "-o"), // console out
      Tests.Argument(TestFrameworks.ScalaTest, "-h", s"${target.value}/test-html")
    ),
    Test / test := (Test / test).dependsOn(root / webpack).value,
    // Forking changes the working dir which breaks where we look for things, so don't fork for now.
    // May be able to fix some other way by updating ForkOptions.
    Test / fork := false,
    Test / parallelExecution := false,
    // Make assets available so they have styles and scripts
    Test / managedClasspath += (root / Assets / packageBin).value,
)



// Dependencies

val enumeratumVersion = "1.5.15"
val enumeratumPlayVersion = "1.5.17"
val enumeratumSlickVersion = "1.5.16"
val playUtilsVersion = "1.51"
val akkaVersion = "2.6.3"
val ssoClientVersion = "2.81"
val warwickUtilsVersion = "20200415"

val appDeps = Seq(
  guice,
  ws,
  cacheApi,
  filters,

  "com.typesafe.play" %% "play-slick" % "5.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0",

  "com.typesafe.slick" %% "slick" % "3.3.2",
  "org.postgresql" % "postgresql" % "42.2.10",
  "com.github.tminglei" %% "slick-pg" % "0.18.1",
  "com.github.tminglei" %% "slick-pg_play-json" % "0.18.1",

  "com.typesafe.play" %% "play-mailer" % "8.0.0",
  "com.typesafe.play" %% "play-mailer-guice" % "8.0.0",

  // in-memory JNDI context used by Play to pass DataSource to Quartz
  "tyrex" % "tyrex" % "1.0.1",
  "org.quartz-scheduler" % "quartz" % "2.3.2" exclude("com.zaxxer", "HikariCP-java6"),

  "net.codingwell" %% "scala-guice" % "4.2.6",
  "com.google.inject.extensions" % "guice-multibindings" % "4.2.2",
  "com.adrianhurt" %% "play-bootstrap" % "1.5.1-P27-B3",

  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % "10.1.11",

  "uk.ac.warwick.sso" %% "sso-client-play" % ssoClientVersion,

  "uk.ac.warwick.play-utils" %% "accesslog" % playUtilsVersion,
  "ch.qos.logback" % "logback-access" % "1.2.3",
  "uk.ac.warwick.play-utils" %% "core" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "caching" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "fileuploads" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "healthcheck" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "slick" % playUtilsVersion,

  "uk.ac.warwick.util" % "warwickutils-core" % warwickUtilsVersion,
  "net.logstash.logback" % "logstash-logback-encoder" % "5.3",
  "uk.ac.warwick.util" % "warwickutils-mywarwick" % warwickUtilsVersion,
  "uk.ac.warwick.util" % "warwickutils-service" % warwickUtilsVersion,
  "uk.ac.warwick.util" % "warwickutils-web" % warwickUtilsVersion,

  "com.github.mumoshu" %% "play2-memcached-play28" % "0.11.0",

  "com.beachape" %% "enumeratum" % enumeratumVersion,
  "com.beachape" %% "enumeratum-play" % enumeratumPlayVersion,
  "com.beachape" %% "enumeratum-play-json" % enumeratumPlayVersion,
  "com.beachape" %% "enumeratum-slick" % enumeratumSlickVersion,

  "org.apache.jclouds.api" % "filesystem" % "2.2.0",

  "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.1",

  "org.apache.commons" % "commons-compress" % "1.20",
  "com.github.tototoshi" %% "scala-csv" % "1.3.6"
)

val testDeps = Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0",
  "uk.ac.warwick.sso" %% "sso-client-play-testing" % ssoClientVersion,
  "uk.ac.warwick.play-utils" %% "testing" % playUtilsVersion,
  "com.opentable.components" % "otj-pg-embedded" % "0.13.3"
).map(_ % Test)

def excludeBadTransitiveDeps(mod: ModuleID): ModuleID = mod.excludeAll(
  ExclusionRule(organization = "commons-logging"),
  // No EhCache please we're British
  ExclusionRule(organization = "net.sf.ehcache"),
  ExclusionRule(organization = "org.ehcache"),
  ExclusionRule(organization = "ehcache"),
  // brought in by warwick utils, pulls in old XML shit
  ExclusionRule(organization = "rome"),
  ExclusionRule(organization = "dom4j"),
  // Tika pulls in slf4j-log4j12
  ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12")
)

// Make built output available as Play assets.
Assets / unmanagedResourceDirectories  += baseDirectory.value / "target/assets"

// Imported by the routes compiler
routesImport += "system.routes.PathBindables._" // to use our types as path variables
routesImport += "system.routes.QueryStringBindables._" // to use our types as query string variables
routesImport += "system.routes.Types._" // type aliases so they don't need to be fully qualified

resolvers += ("Local Maven Repository" at "file:///" + Path.userHome.absolutePath + "/.m2/repository")
resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
resolvers += "oauth" at "https://oauth.googlecode.com/svn/code/maven"
resolvers += "softprops-maven" at "https://dl.bintray.com/content/softprops/maven"
resolvers += "slack-client" at "https://mvnrepository.com/artifact/net.gpedro.integrations.slack/slack-webhook"
resolvers += "SBT plugins" at "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"
resolvers += "nexus" at "https://mvn.elab.warwick.ac.uk/nexus/repository/public-anonymous/"

// Define a special test task which does not fail when any test fails, so sequential tasks will be performed no
// matter the test result.
lazy val bambooTest = taskKey[Unit]("Run tests for CI")

bambooTest := {
  // Capture the test result
  val testResult = (Test / test).result.value
}

// Webpack task

import scala.sys.process.Process

lazy val webpack = taskKey[Unit]("Run webpack when packaging the application")

lazy val webpackEnabled = settingKey[Boolean]("Is webpack enabled")

def runWebpack(file: File): Int = Process("npm run build", file).!

webpack := {
  if (webpackEnabled.value) {
    Changes.ifChanged(
      target.value / "webpack-tracking",
      baseDirectory.value / "app" / "assets",
      target.value / "assets"
    ) {
      if (runWebpack(baseDirectory.value) != 0) throw new Exception("Something went wrong when running webpack.")
    }
  }
}

runner := runner.dependsOn(webpack).value
dist := dist.dependsOn(webpack).value
stage := stage.dependsOn(webpack).value

// Generate a new AES key for object store encryption
lazy val newEncryptionKey = taskKey[Unit]("Generate and print a new encryption key")
newEncryptionKey := println(EncryptionKey.generate())

