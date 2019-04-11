import sbt.Path

ThisBuild / organization := "uk.ac.warwick"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.8"

ThisBuild / javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
ThisBuild / scalacOptions ++= Seq(
  "-encoding", "UTF-8", // yes, this is 2 args
  "-target:jvm-1.8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Xfatal-warnings",
  "-Xsource:2.13"
)
ThisBuild / scalacOptions in Test ++= Seq("-Yrangepos")
ThisBuild / scalacOptions in (Compile, doc) ++= Seq("-no-link-warnings")

autoAPIMappings := true

// Avoid some of the constant SBT "Updating"
updateOptions := updateOptions.value.withCachedResolution(true)

lazy val root = (project in file("."))
  .enablePlugins(WarwickProject, PlayScala)
  .settings(
    name := """play-app-template""",
    packageZipTarball in Universal := (packageZipTarball in Universal).dependsOn(webpack).value,
    libraryDependencies ++= (appDeps ++ testDeps).map(excludeBadTransitiveDeps),
    javaOptions in Test += "-Dlogger.resource=test-logging.xml"
  )

// Separate project rather than an extra config on the root
// project - it's simpler overall.
lazy val integration = (project in file("it"))
  .dependsOn(root, root % "test->test") // get access to the common test classes
  .settings(
    libraryDependencies ++= Seq(
      "org.pegdown" % "pegdown" % "1.6.0" % Test, // For Scalatest HTML reports
    ),
    sourceDirectory := baseDirectory.value, // no "src" subfolder
    testOptions in Test ++= Seq(
      Tests.Argument(TestFrameworks.ScalaTest, "-o"), // console out
      Tests.Argument(TestFrameworks.ScalaTest, "-h", s"${target.value}/test-html")
    ),
  )

// Dependencies

val enumeratumVersion = "1.5.13"
val enumeratumSlickVersion = "1.5.15"
val playUtilsVersion = "1.32"
val ssoClientVersion = "2.63"
val warwickUtilsVersion = "20190221"

val appDeps = Seq(
  guice,
  ws,
  cacheApi,
  filters,

  // Don't upgrade to 4.x or you'll get Slick 3.3
  "com.typesafe.play" %% "play-slick" % "3.0.3",
  "com.typesafe.play" %% "play-slick-evolutions" % "3.0.3",

  // Intentionally Slick 3.2, not 3.3 - 3.3 has weird behaviour with our custom OffsetDateTime
  "com.typesafe.slick" %% "slick" % "3.2.3",
  "org.postgresql" % "postgresql" % "42.2.5",
  "com.github.tminglei" %% "slick-pg" % "0.17.1", // Don't upgrade past 0.17.1 or you'll get Slick 3.3

  "net.codingwell" %% "scala-guice" % "4.2.1",
  "com.google.inject.extensions" % "guice-multibindings" % "4.2.2",
  "com.adrianhurt" %% "play-bootstrap" % "1.2-P26-B3",

  "uk.ac.warwick.sso" %% "sso-client-play" % ssoClientVersion,

  "uk.ac.warwick.play-utils" %% "accesslog" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "healthcheck" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "slick" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "core" % playUtilsVersion,

  "uk.ac.warwick.util" % "warwickutils-core" % warwickUtilsVersion,
  "uk.ac.warwick.util" % "warwickutils-service" % warwickUtilsVersion,

  "com.github.mumoshu" %% "play2-memcached-play27" % "0.10.0-RC3",

  "com.beachape" %% "enumeratum" % enumeratumVersion,
  "com.beachape" %% "enumeratum-play" % enumeratumVersion,
  "com.beachape" %% "enumeratum-play-json" % enumeratumVersion,
  "com.beachape" %% "enumeratum-slick" % enumeratumSlickVersion,

  "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0"
)

val testDeps = Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.1",
  "org.mockito" % "mockito-core" % "2.24.5",
  "uk.ac.warwick.sso" %% "sso-client-play-testing" % ssoClientVersion,
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

// JClouds requires v2.5 https://issues.apache.org/jira/browse/JCLOUDS-1166
ThisBuild / dependencyOverrides += "com.google.code.gson" % "gson" % "2.5"

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

def runWebpack(file: File): Int = Process("npm run build", file).!

webpack := {
  if (runWebpack(baseDirectory.value) != 0) throw new Exception("Something went wrong when running webpack.")
}

runner := runner.dependsOn(webpack).value
dist := dist.dependsOn(webpack).value
stage := stage.dependsOn(webpack).value
