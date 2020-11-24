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
  .aggregate(redos, `regex-matching-analyzer`)

lazy val redos = project
  .in(file("modules/redos"))
  .settings(
    Compile / run / mainClass := Some("codes.quine.labo.redos_experiment.redos_tester.Main"),
    Compile / run / fork := true,
    libraryDependencies += "codes.quine.labo" %% "redos-core" % "1.1.1",
    libraryDependencies += "com.lihaoyi" %% "upickle" % "0.9.5",
    libraryDependencies += "com.lihaoyi" %% "os-lib" % "0.7.1"
  )

lazy val `regex-matching-analyzer` = project
  .in(file("modules/regex-matching-analyzer"))
  .settings(
    scalaVersion := "2.12.12",
    Compile / run / mainClass := Some("codes.quine.labo.redos_experiment.regex_matching_analyzer.Main"),
    Compile / run / fork := true,
    libraryDependencies += "default" %% "regex-matching-analyzer" % "0.1.0-SNAPSHOT",
    libraryDependencies += "com.lihaoyi" %% "upickle" % "0.9.5",
    libraryDependencies += "com.lihaoyi" %% "os-lib" % "0.7.1"
  )

lazy val rescue = project
  .in(file("modules/rescue"))
  .settings(
    Compile / run / mainClass := Some("codes.quine.labo.redos_experiment.rescue.Main"),
    Compile / run / fork := true,
    libraryDependencies += "cn.edu.nju.moon.ReScue" % "ReScue" % "0.0.1-SNAPSHOT",
    libraryDependencies += "com.lihaoyi" %% "upickle" % "0.9.5",
    libraryDependencies += "com.lihaoyi" %% "os-lib" % "0.7.1"
  )
