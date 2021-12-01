enablePlugins(SbtTwirl, JavaAppPackaging)

name := "zio-start"

scalaVersion := "3.1.1-RC1-bin-20210927-3f978b3-NIGHTLY"

val zioVersion = "1.0.12"

scalacOptions += "-language:experimental.fewerBraces"

// temporary for zhttp-test
resolvers += "Sonatype OSS Snapshots s01" at "https://s01.oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"          % zioVersion,

  "dev.zio" %% "zio-process" % "0.5.0",

  "io.d11" %% "zhttp"         % "1.0.0.0-RC17",

  "io.github.classgraph" % "classgraph" % "4.8.137",

  "dev.zio" %% "zio-test"     % zioVersion % Test,

  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,

  //"io.d11" %% "zhttp-test" % "1.0.0.0-RC17" % Test,
  // temporary snapshot
  "io.d11" %% "zhttp-test" % "1.0.0.0-RC17+47-0ea2e2b7-SNAPSHOT" % Test,
  //  "org.slf4j"  %  "slf4j-simple"        % "1.7.30",
)

libraryDependencies := libraryDependencies.value.map {
  case module if module.name == "twirl-api" =>
    module.withCrossVersion(CrossVersion.for3Use2_13)
  case module =>
    module
}

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
