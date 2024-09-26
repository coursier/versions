
inThisBuild(List(
  organization := "io.get-coursier",
  homepage := Some(url("https://github.com/coursier/versions")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      "alexarchambault",
      "Alexandre Archambault",
      "",
      url("https://github.com/alexarchambault")
    )
  )
))

lazy val shared = Def.settings(
  scalaVersion := "2.13.15",
  crossScalaVersions := Seq("2.13.15", "2.12.17"),
  libraryDependencies ++= {
    if (isAtLeastScala213.value) Nil
    else Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full))
  },
  scalacOptions ++= {
    if (isAtLeastScala213.value) Seq("-Ymacro-annotations")
    else Nil
  },
  scalacOptions += "-deprecation"
)

lazy val isAtLeastScala213 = Def.setting {
  import Ordering.Implicits._
  CrossVersion.partialVersion(scalaVersion.value).exists(_ >= (2, 13))
}


lazy val versions = crossProject(JVMPlatform, JSPlatform)
  .settings(
    shared,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "utest" % "0.7.11" % Test,
      "io.github.alexarchambault" %% "data-class" % "0.2.6" % Provided
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      Seq(
        // Additional abstract method on *sealed* trait
        ProblemFilters.exclude[ReversedMissingMethodProblem]("coursier.version.VersionCompatibility.minimumCompatibleVersion")
      )
    },
    mimaPreviousArtifacts := {
      Mima.binaryCompatibilityVersions
        .map(ver => (organization.value % moduleName.value % ver).cross(crossVersion.value))
    }
  )

lazy val versionsJVM = versions.jvm
lazy val versionsJS = versions.js

lazy val readme = project
  .dependsOn(versionsJVM)
  .disablePlugins(MimaPlugin)
  .settings(
    shared,
    (publish / skip) := true,
    (run / fork) := true,
    (Compile / run / forkOptions) := (Compile / run / forkOptions).value.withWorkingDirectory((ThisBuild / baseDirectory).value),
    libraryDependencies += "org.scalameta" %% "mdoc" % "2.2.2",
    watchTriggers += ((ThisBuild / baseDirectory).value / "README.template.md").toGlob
  )

crossScalaVersions := Nil
(publish / skip) := true
disablePlugins(MimaPlugin)
