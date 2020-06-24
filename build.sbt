
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
  scalaVersion := "2.13.2",
  crossScalaVersions := Seq("2.13.2", "2.12.11"),
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
      "com.lihaoyi" %% "utest" % "0.7.4" % Test,
      "io.github.alexarchambault" %% "data-class" % "0.2.3" % Provided
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
  )

lazy val versionsJVM = versions.jvm
lazy val versionsJS = versions.js

lazy val readme = project
  .dependsOn(versionsJVM)
  .disablePlugins(MimaPlugin)
  .settings(
    shared,
    skip.in(publish) := true,
    fork.in(run) := true,
    forkOptions.in(Compile, run) := forkOptions.in(Compile, run).value.withWorkingDirectory(baseDirectory.in(ThisBuild).value),
    libraryDependencies += "org.scalameta" %% "mdoc" % "2.2.2",
    watchTriggers += (baseDirectory.in(ThisBuild).value / "README.template.md").toGlob
  )

crossScalaVersions := Nil
skip.in(publish) := true
disablePlugins(MimaPlugin)
