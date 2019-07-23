// Warwick parent plugin
resolvers += "nexus" at "https://mvn.elab.warwick.ac.uk/nexus/repository/public-anonymous/"
addSbtPlugin("uk.ac.warwick" % "play-warwick" % "0.10")

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.7.3")

// https://github.com/playframework/twirl/pull/238 should be included in official 1.4.3 or 1.5.0 onwards
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.4.3-warwick")

// .tgz generator
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.25")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.0")
