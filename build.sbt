lazy val commonSettings = Seq (
    version := "0.1",
    organization := "openquant",
    name := "ibquoteprovider",
    scalaVersion := "2.11.7",
    scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8"),
    resolvers ++= Seq(
        Resolver.sonatypeRepo("releases"),
        Resolver.sonatypeRepo("public")
    )
)

lazy val testDependencies = Seq(
    "org.specs2" %% "specs2" % "3.+" % "test"
)


lazy val commonDependencies = Seq(
    "org.scalaz" %% "scalaz-core" % "7.2.0",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.7",
    "commons-logging" % "commons-logging" % "99-empty",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "com.iheart" %% "ficus" % "1.2.3",
    "openquant" %% "common" % "+",
    "openquant" %% "quoteprovider" % "+",
    "com.larroy" %% "ibclient" % "0.2.+"
)

lazy val proj = project.in(file("."))
    .settings(commonSettings:_*)
    .settings(libraryDependencies ++= commonDependencies)
    .settings(libraryDependencies ++= testDependencies)

parallelExecution in Test := false
