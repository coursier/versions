import $ivy.`com.github.lolgab::mill-mima::0.0.24`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`

import com.github.lolgab.mill.mima._
import de.tobiasroeser.mill.vcs.version._
import mill._
import mill.scalajslib._
import mill.scalanativelib._
import mill.scalalib._
import mill.scalalib.publish._

import java.io.File

object DepVersions {
  def mdoc = "2.3.6"
  def scala213 = "2.13.16"
  def scalaJs = "1.18.1"
  def scalaNative = "0.5.6"

  def scala = Seq(scala213, "2.12.20")
}

object Deps {
  def macroParadise = ivy"org.scalamacros:::paradise:2.1.1"
  def pprint = ivy"com.lihaoyi::pprint::0.9.0"
  def utest = ivy"com.lihaoyi::utest::0.8.5"
}

trait VersionsMima extends Mima {
  def mimaPreviousVersions: T[Seq[String]] = T.input {
    val current = os.proc("git", "describe", "--tags", "--match", "v*")
      .call(cwd = T.workspace)
      .out.trim()
    val cutOff = coursier.core.Version("0.4.0")
    os.proc("git", "tag", "-l")
      .call(cwd = T.workspace)
      .out.lines()
      .filter(_ != current)
      .filter(_.startsWith("v"))
      .map(_.stripPrefix("v"))
      .map(coursier.core.Version(_))
      .filter(_ > cutOff)
      .sorted
      .map(_.repr)
  }
  // required if mimaPreviousVersions is empty
  def mimaPreviousArtifacts = T {
    val versions = mimaPreviousVersions().distinct
    mill.api.Result.Success(
      Agg.from(
        versions.map(version =>
          ivy"${pomSettings().organization}:${artifactId()}:$version"
        )
      )
    )
  }
}

trait VersionsPublishModule extends PublishModule with VersionsMima {
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "io.get-coursier",
    url = "https://github.com/coursier/versions",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("coursier", "versions"),
    developers = Seq(
      Developer("alexarchambault", "Alex Archambault","https://github.com/alexarchambault")
    )
  )

  def publishVersion = T {
    val state = VcsVersion.vcsState()
    if (state.commitsSinceLastTag > 0) {
      val versionOrEmpty = state.lastTag
        .filter(_ != "latest")
        .map(_.stripPrefix("v"))
        .flatMap { tag =>
          val idx = tag.lastIndexOf(".")
          if (idx >= 0)
            Some(tag.take(idx + 1) + (tag.drop(idx + 1).takeWhile(_.isDigit).toInt + 1).toString + "-SNAPSHOT")
          else None
        }
        .getOrElse("0.0.1-SNAPSHOT")
      Some(versionOrEmpty)
        .filter(_.nonEmpty)
        .getOrElse(state.format())
    } else
      state
        .lastTag
        .getOrElse(state.format())
        .stripPrefix("v")
  }
}

trait Versions extends Cross.Module[String] with ScalaModule with VersionsPublishModule {
  def artifactName = "versions"
  def scalaVersion = crossValue
  def scalacPluginIvyDeps = T {
    val sv = scalaVersion()
    val scala212Plugins =
      if (sv.startsWith("2.12.")) Agg(Deps.macroParadise)
      else Nil
    super.scalacPluginIvyDeps() ++ scala212Plugins
  }
  def scalacOptions = T {
    val sv = scalaVersion()
    val scala213Opts =
      if (sv.startsWith("2.13.")) Seq("-Ymacro-annotations")
      else Nil
    super.scalacOptions() ++ scala213Opts ++ Seq(
      "-deprecation",
      "--release",
      "8"
    )
  }
  def sources = T.sources {
    super.sources() ++ Seq(PathRef(T.workspace / "versions/shared/src"))
  }

  def testSources = T.sources {
    Seq(PathRef(T.workspace / "versions/shared/test/src"))
  }

  def compileIvyDeps = Agg(
    ivy"io.github.alexarchambault::data-class:0.2.7"
  )

  def mimaBinaryIssueFilters = super.mimaBinaryIssueFilters() ++ Seq(
    // Additional abstract method on *sealed* trait
    ProblemFilter.exclude[ReversedMissingMethodProblem]("coursier.version.VersionCompatibility.minimumCompatibleVersion")
  )
}

trait VersionsJvm extends Versions {

  object test extends ScalaTests {
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.pprint,
      Deps.utest
    )
    def testFramework = "utest.runner.Framework"
    def sources = T.sources {
      super.sources() ++ testSources()
    }
  }
}

trait VersionsJs extends Versions with ScalaJSModule {
  def scalaJSVersion = DepVersions.scalaJs

  object test extends ScalaJSTests {
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.pprint,
      Deps.utest
    )
    def testFramework = "utest.runner.Framework"
    def sources = T.sources {
      super.sources() ++ testSources()
    }
  }
}

trait VersionsNative extends Versions with ScalaNativeModule {
  def scalaNativeVersion = DepVersions.scalaNative

  def mimaPreviousVersions = T {
    val cutOff = coursier.core.Version("0.3.3")
    super.mimaPreviousVersions().filter { v =>
      coursier.core.Version(v) > cutOff
    }
  }

  // required if mimaPreviousVersions is empty
  def mimaPreviousArtifacts = T {
    val versions = mimaPreviousVersions().distinct
    mill.api.Result.Success(
      Agg.from(
        versions.map(version =>
          ivy"${pomSettings().organization}:${artifactId()}:$version"
        )
      )
    )
  }

  object test extends ScalaNativeTests {
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.utest
    )
    def testFramework = "utest.runner.Framework"
    def sources = T.sources {
      super.sources() ++ testSources()
    }
  }
}

object versions extends Module {
  object jvm extends Cross[VersionsJvm](DepVersions.scala)
  object js extends Cross[VersionsJs](DepVersions.scala)
  object native extends Cross[VersionsNative](DepVersions.scala)
}


def readme = T.sources {
  Seq(PathRef(T.workspace / "README.md"))
}

private def mdocScalaVersion = DepVersions.scala213
def mdoc(args: String*) = T.command {
  val readme0 = readme().head.path
  val dest = T.dest / "README.md"
  val cp = (versions.jvm(mdocScalaVersion).runClasspath() :+ versions.jvm(mdocScalaVersion).jar())
    .map(_.path)
    .filter(os.exists(_))
    .filter(!os.isDir(_))
  val cmd = Seq("cs", "launch", s"mdoc:${DepVersions.mdoc}", "--scala", mdocScalaVersion)
  val mdocArgs = Seq(
    "--in", readme0.toString,
    "--out", dest.toString,
    "--classpath", cp.mkString(File.pathSeparator)
  )
  os.proc(cmd, "--", mdocArgs, args).call(
    cwd = T.workspace,
    stdin = os.Inherit,
    stdout = os.Inherit,
    stderr = os.Inherit
  )
}
