Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / organization := "codes.quine.labo"

ThisBuild / scalaVersion := "2.13.3"
ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-feature",
  "-deprecation"
)

ThisBuild / resolvers += Resolver.mavenLocal

lazy val root = project
  .in(file("."))
  .aggregate(redos, `regex-matching-analyzer`, `regex-static-analysis`, rescue)

lazy val redos = project
  .in(file("modules/redos"))
  .settings(
    name := "redos-expreiment-redos",
    Compile / run / mainClass := Some("codes.quine.labo.redos_experiment.redos.Main"),
    Compile / run / fork := true,
    libraryDependencies += "codes.quine.labo" %% "redos" % "1.2.0",
    libraryDependencies += "com.lihaoyi" %% "upickle" % "0.9.5",
    libraryDependencies += "com.lihaoyi" %% "os-lib" % "0.7.1"
  )

lazy val `regex-matching-analyzer` = project
  .in(file("modules/regex-matching-analyzer"))
  .settings(
    name := "redos-expreiment-regex-matching-analyzer",
    scalaVersion := "2.12.12",
    Compile / run / mainClass := Some("codes.quine.labo.redos_experiment.regex_matching_analyzer.Main"),
    Compile / run / fork := true,
    libraryDependencies += "default" %% "regex-matching-analyzer" % "0.1.0-SNAPSHOT",
    libraryDependencies += "com.lihaoyi" %% "upickle" % "0.9.5",
    libraryDependencies += "com.lihaoyi" %% "os-lib" % "0.7.1"
  )

lazy val `regex-static-analysis` = project
  .in(file("modules/regex-static-analysis"))
  .settings(
    name := "redos-expreiment-regex-static-analysis",
    Compile / run / mainClass := Some("codes.quine.labo.redos_experiment.regex_static_analysis.Main"),
    Compile / run / fork := true,
    libraryDependencies += "nicolaasweideman" % "regex-static-analysis" % "1.0-SNAPSHOT",
    libraryDependencies += "com.lihaoyi" %% "upickle" % "0.9.5",
    libraryDependencies += "com.lihaoyi" %% "os-lib" % "0.7.1"
  )

lazy val rescue = project
  .in(file("modules/rescue"))
  .settings(
    name := "redos-expreiment-rescue",
    Compile / run / mainClass := Some("codes.quine.labo.redos_experiment.rescue.Main"),
    Compile / run / fork := true,
    libraryDependencies += "cn.edu.nju.moon.ReScue" % "ReScue" % "0.0.1-SNAPSHOT",
    libraryDependencies += "com.lihaoyi" %% "upickle" % "0.9.5",
    libraryDependencies += "com.lihaoyi" %% "os-lib" % "0.7.1"
  )
