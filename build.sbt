import sbt.Path
import warwick.Changes

ThisBuild / organization := "uk.ac.warwick"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.0"

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
ThisBuild / scalacOptions in Test ++= Seq("-Yrangepos")
ThisBuild / scalacOptions in (Compile, doc) ++= Seq("-no-link-warnings")
ThisBuild / webpackEnabled := true

autoAPIMappings := true

// Avoid some of the constant SBT "Updating"
updateOptions := updateOptions.value.withCachedResolution(true)

lazy val root = (project in file("."))
  .enablePlugins(WarwickProject, PlayScala)
  .settings(
    name := """play-app-template""",
    packageZipTarball in Universal := (packageZipTarball in Universal).dependsOn(webpack).value,
    libraryDependencies ++= (appDeps ++ testDeps).map(excludeBadTransitiveDeps),
    Test / javaOptions += "-Dlogger.resource=test-logging.xml",
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
    Test / javaOptions += "-Dlogger.resource=test-logging.xml",
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

val enumeratumVersion = "1.5.13"
val enumeratumPlayVersion = "1.5.16"
val enumeratumSlickVersion = "1.5.16"
val playUtilsVersion = "1.39"
val ssoClientVersion = "2.71"
val warwickUtilsVersion = "20190503"

val appDeps = Seq(
  guice,
  ws,
  cacheApi,
  filters,

  // Upgrade transitive dependency on jackson-databind to remediate deserialization of untrusted data
  // https://snyk.io/vuln/SNYK-JAVA-COMFASTERXMLJACKSONCORE-455617
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.9.2",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.9.9",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.9.9",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % "2.9.9",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.9.9",

  "com.typesafe.play" %% "play-slick" % "4.0.2",
  "com.typesafe.play" %% "play-slick-evolutions" % "4.0.2",
  "com.typesafe.play" %% "play-jdbc-evolutions" % "2.7.3",

  "com.typesafe.slick" %% "slick" % "3.3.2",
  "org.postgresql" % "postgresql" % "42.2.6",
  "com.github.tminglei" %% "slick-pg" % "0.18.0",

  "net.codingwell" %% "scala-guice" % "4.2.6",
  "com.google.inject.extensions" % "guice-multibindings" % "4.2.2",
  "com.adrianhurt" %% "play-bootstrap" % "1.5-P27-B3",

  "uk.ac.warwick.sso" %% "sso-client-play" % ssoClientVersion,

  // Upgrade transitive dependency on Apache httpclient to remediate directory traversal
  // https://snyk.io/vuln/SNYK-JAVA-ORGAPACHEHTTPCOMPONENTS-31517
  "org.apache.httpcomponents" % "httpclient" % "4.5.9",

  "uk.ac.warwick.play-utils" %% "accesslog" % playUtilsVersion,
  "ch.qos.logback" % "logback-access" % "1.2.3",
  "uk.ac.warwick.play-utils" %% "healthcheck" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "slick" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "core" % playUtilsVersion,

  "uk.ac.warwick.util" % "warwickutils-core" % warwickUtilsVersion,
  "net.logstash.logback" % "logstash-logback-encoder" % "5.3",
  "uk.ac.warwick.util" % "warwickutils-service" % warwickUtilsVersion,

  "com.github.mumoshu" %% "play2-memcached-play27" % "0.10.1",

  "com.beachape" %% "enumeratum" % enumeratumVersion,
  "com.beachape" %% "enumeratum-play" % enumeratumPlayVersion,
  "com.beachape" %% "enumeratum-play-json" % enumeratumPlayVersion,
  "com.beachape" %% "enumeratum-slick" % enumeratumSlickVersion,

  "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0"
)

val testDeps = Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3",
  "org.mockito" % "mockito-core" % "2.28.2",
  "uk.ac.warwick.sso" %% "sso-client-play-testing" % ssoClientVersion,
  "uk.ac.warwick.play-utils" %% "testing" % playUtilsVersion,
  "com.opentable.components" % "otj-pg-embedded" % "0.13.1",
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
unmanagedResourceDirectories in Assets += baseDirectory.value / "target/assets"

resolvers += ("Local Maven Repository" at "file:///" + Path.userHome.absolutePath + "/.m2/repository")
resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
resolvers += "oauth" at "http://oauth.googlecode.com/svn/code/maven"
resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven"
resolvers += "slack-client" at "https://mvnrepository.com/artifact/net.gpedro.integrations.slack/slack-webhook"
resolvers += "SBT plugins" at "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"
resolvers += "nexus" at "https://mvn.elab.warwick.ac.uk/nexus/repository/public-anonymous/"

// Define a special test task which does not fail when any test fails, so sequential tasks will be performed no
// matter the test result.
lazy val bambooTest = taskKey[Unit]("Run tests for CI")

bambooTest := {
  // Capture the test result
  val testResult = (test in Test).result.value
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