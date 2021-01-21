Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / organization := "codes.quine.labo"

ThisBuild / scalaVersion := "2.13.4"
ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-feature",
  "-deprecation"
)

ThisBuild / resolvers += Resolver.mavenLocal

lazy val root = project
  .in(file("."))
  .aggregate(common.projectRefs: _*)
  .aggregate(redos, `regex-matching-analyzer`, `regex-static-analysis`, rescue)

lazy val common = projectMatrix
  .in(file("modules/common"))
  .settings(
    name := "redos-experiment-common",
    libraryDependencies += "io.circe" %% "circe-core" % "0.12.3",
    libraryDependencies += "io.circe" %% "circe-generic" % "0.12.3",
    libraryDependencies += "io.circe" %% "circe-parser" % "0.12.3",
    libraryDependencies += "com.monovore" %% "decline" % "1.3.0"
  )
  .jvmPlatform(scalaVersions = Seq("2.13.4", "2.12.13"))

lazy val common2_13 = common.jvm("2.13.4")
lazy val common2_12 = common.jvm("2.12.13")

lazy val redos = project
  .in(file("modules/redos"))
  .settings(
    name := "redos-experiment-redos",
    Compile / run / mainClass := Some("codes.quine.labo.redos_experiment.redos.Main"),
    Compile / run / fork := true,
    Compile / run / baseDirectory := file(".").getAbsoluteFile,
    libraryDependencies += "codes.quine.labo" %% "redos" % "1.2.0",
  )
  .dependsOn(common2_13)

lazy val `regex-matching-analyzer` = project
  .in(file("modules/regex-matching-analyzer"))
  .settings(
    name := "redos-experiment-regex-matching-analyzer",
    scalaVersion := "2.12.13",
    Compile / run / mainClass := Some("codes.quine.labo.redos_experiment.regex_matching_analyzer.Main"),
    Compile / run / fork := true,
    Compile / run / baseDirectory := file(".").getAbsoluteFile,
    libraryDependencies += "default" %% "regex-matching-analyzer" % "0.1.0-SNAPSHOT",
  )
  .dependsOn(common2_12)

lazy val `regex-static-analysis` = project
  .in(file("modules/regex-static-analysis"))
  .settings(
    name := "redos-experiment-regex-static-analysis",
    Compile / run / mainClass := Some("codes.quine.labo.redos_experiment.regex_static_analysis.Main"),
    Compile / run / fork := true,
    Compile / run / baseDirectory := file(".").getAbsoluteFile,
    libraryDependencies += "nicolaasweideman" % "regex-static-analysis" % "1.0-SNAPSHOT",
  )
  .dependsOn(common2_13)

lazy val rescue = project
  .in(file("modules/rescue"))
  .settings(
    name := "redos-experiment-rescue",
    Compile / run / mainClass := Some("codes.quine.labo.redos_experiment.rescue.Main"),
    Compile / run / fork := true,
    Compile / run / baseDirectory := file(".").getAbsoluteFile,
    libraryDependencies += "cn.edu.nju.moon.ReScue" % "ReScue" % "0.0.1-SNAPSHOT",
  )
  .dependsOn(common2_13)
