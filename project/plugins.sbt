// Warwick parent plugin
resolvers += "nexus" at "https://mvn.elab.warwick.ac.uk/nexus/repository/public-anonymous/"
addSbtPlugin("uk.ac.warwick" % "play-warwick" % "0.19")

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.1")

// .tgz generator
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.6.1")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")

// Dependency graph plugin is a pre-requisite for Snyk vulnerability scans
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
