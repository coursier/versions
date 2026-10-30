# coursier-versions

*coursier-versions* is the library parsing and comparing versions of
[coursier](https://get-coursier.io).

It's mainly designed around the needs of coursier itself, but can find uses outside of it.

It aims at limiting binary compatibility breakages, and follows semantic versioning.
While still in `0.x`, backward binary compatibility hasn't been broken between its first release
up to `0.3.3`. `0.4.0` breaks binary compatibility with former versions. Later releases are
checked with [MiMa](https://github.com/lightbend-labs/mima) against all the releases after `0.4.0`.

## Usage

With sbt, add to your `build.sbt`:
```scala
libraryDependencies += "io.get-coursier" %% "versions" % "0.6.1"
// or, for Scala.js and Scala Native
libraryDependencies += "io.get-coursier" %%% "versions" % "0.6.1"
```

With Mill, add to your module:
```scala
def mvnDeps = Seq(mvn"io.get-coursier::versions:0.6.1")
// or, for Scala.js and Scala Native
def mvnDeps = Seq(mvn"io.get-coursier::versions::0.6.1")
```

With Scala CLI:
```scala
//> using dep io.get-coursier::versions:0.6.1
```

*coursier-versions* is available for the following platforms:

| Platform     | Scala versions  |
|--------------|-----------------|
| JVM          | 2.12, 2.13, 3   |
| Scala.js     | 2.13, 3         |
| Scala Native | 2.13, 3         |

Scala 3 and Scala Native artifacts are available since `0.6.0`. Scala 2.12 artifacts for Scala.js
aren't published anymore since `0.6.1`.

The latest version is
[![Maven Central](https://img.shields.io/maven-central/v/io.get-coursier/versions_3.svg)](https://central.sonatype.com/artifact/io.get-coursier/versions_3).

All *coursier-versions* classes live under the `coursier.version` namespace. The snippets below
assume
```scala mdoc
import coursier.version._
```

## `Version`

Version parsing and comparison is handled by `coursier.version.Version`.

Parse a version with
```scala mdoc
val version = Version("1.2.0-RC3")
val items = version.items
```

Parsing is meant to be very loose, and tries to make sense of any input. The actual
parsing happens when `items`, which is a `lazy val`, is called on `Version`.

If you'd like to reject potentially invalid inputs, use `VersionParse.version`:
```scala mdoc
val versionOpt = VersionParse.version("[1.2,1.3)")
```

```scala mdoc:passthrough
assert(versionOpt.isEmpty)
```

The parsing logic is originally based on [Maven / Aether version parsing](https://github.com/apache/maven-resolver/blob/3b8a7ec07799d894d5ffde523ec9a8062956805a/aether-util/src/main/java/org/eclipse/aether/util/version/GenericVersion.java), which was later tweaked to accommodate Scala / sbt needs.

`items` gives the list of elements composing the parsed version, and is later used to compare versions.

The coursier documentation [details](https://get-coursier.io/docs/other-version-handling.html#ordering)
how versions are compared.

`Version` implements `Ordered[Version]`, so that `Version` instances can be compared together,
and a sequence of `Version`s can be sorted. That order is total, and consistent with `equals`:
`a.compare(b)` is `0` if and only if `a == b`. Versions that mean the same thing but are spelled
differently, like `1.0` and `1.0.0`, or `1.2+foo` and `1.2+bar`, are ordered by their
representation, so that sorted collections and hash-based ones agree on which versions are
distinct.

Use `compareSemantic` to compare versions up to that equivalence - it returns `0` for versions
that only differ by padding, separators, build metadata, or a release qualifier like `Final` or
`GA`. This is the order that decides whether a version sits in an interval, or whether two
constraints can be reconciled:
```scala mdoc
Version("1.0").compare(Version("1.0.0"))
Version("1.0").compareSemantic(Version("1.0.0"))
Version("1.1.0").compareSemantic(Version("1.1.0.Final"))
```

## `VersionConstraint`

`VersionConstraint` aims at representing not only versions, but also version intervals, or
interval selectors such as `1.2+`.

Parse a version constraint with
```scala mdoc
val intervalConstraint = VersionParse.versionConstraint("[1.2,1.3)")
val intervalSelectorConstraint = VersionParse.versionConstraint("1.2+")
val simpleVersionConstraint = VersionParse.versionConstraint("1.4.2")
```

```scala mdoc:passthrough
assert(intervalConstraint.interval == VersionInterval(Some(Version("1.2")), Some(Version("1.3")), true, false))
assert(intervalConstraint.preferred.isEmpty)
assert(intervalSelectorConstraint.interval == VersionInterval(Some(Version("1.2")), Some(Version("1.2.max")), true, true))
assert(intervalSelectorConstraint.preferred.isEmpty)
assert(simpleVersionConstraint.interval == VersionInterval(None, None, false, false))
assert(simpleVersionConstraint.preferred == Some(Version("1.4.2")))
```

Like for versions, parsing is meant to be very loose, trying to make sense of any input.

Use `VersionConstraint.merge` to find a constraint satisfying several ones. It keeps the
intersection of intervals and the highest preferred version, and returns `None` if the input
constraints can't be reconciled. `VersionConstraint.relaxedMerge` always succeeds, by giving
up on the lowest constraints when needed.
```scala mdoc
val mergedConstraint = VersionConstraint.merge(
  VersionConstraint("[1.2,1.4)"),
  VersionConstraint("1.3")
)
val highestVersion = VersionConstraint.merge(
  VersionConstraint("1.2"),
  VersionConstraint("1.3")
)
val noOverlap = VersionConstraint.merge(
  VersionConstraint("[1.0,1.1)"),
  VersionConstraint("[1.2,1.3)")
)
val relaxed = VersionConstraint.relaxedMerge(
  VersionConstraint("[1.0,1.1)"),
  VersionConstraint("[1.2,1.3)")
)
```

```scala mdoc:passthrough
assert(mergedConstraint.map(_.asString) == Some("[1.2,1.4)&1.3"))
assert(highestVersion.map(_.asString) == Some("1.3"))
assert(noOverlap.isEmpty)
assert(relaxed.asString == "[1.2,1.3)")
```

## `VersionInterval`

`VersionInterval` represents the interval part of a version constraint. Its bounds are
compared with `compareSemantic`.

```scala mdoc
val interval = VersionParse.versionInterval("[1.2,1.3)").get
val inInterval = interval.contains(Version("1.2.5"))
val upperBound = interval.contains(Version("1.3"))
val intersection = interval.merge(VersionParse.versionInterval("[1.2.5,)").get)
val noIntersection = interval.merge(VersionParse.versionInterval("[1.4,)").get)
val invalid = VersionParse.versionInterval("[1.3,1.2)").get.isValid
```

```scala mdoc:passthrough
assert(inInterval)
assert(!upperBound)
assert(intersection.map(_.repr) == Some("[1.2.5,1.3)"))
assert(noIntersection.isEmpty)
assert(!invalid)
```

`VersionParse` also parses Ivy-like selectors, and the multi-interval syntax of Maven, of which
only the last interval is retained:
```scala mdoc
val ivySelector = VersionParse.ivyLatestSubRevisionInterval("1.2.+")
val multiInterval = VersionParse.multiVersionInterval("[1.0,1.2),[1.3,)")
```

```scala mdoc:passthrough
assert(ivySelector.map(_.repr) == Some("[1.2,1.2.max]"))
assert(multiInterval.map(_.repr) == Some("[1.3,)"))
```

## `Latest`

`Latest` represents the `latest.integration`, `latest.release`, and `latest.stable` selectors.
The `LATEST` and `RELEASE` meta versions of Maven 2 are accepted too, and correspond to
`latest.integration` and `latest.release`.

```scala mdoc
val latestRelease = Latest("latest.release")
val mavenLatest = Latest("LATEST")
val notLatest = Latest("1.2")
val latestConstraint = VersionParse.versionConstraint("latest.stable")
val latestOfConstraint = latestConstraint.latest
```

```scala mdoc:passthrough
assert(latestRelease == Some(Latest.Release))
assert(mavenLatest == Some(Latest.Integration))
assert(notLatest.isEmpty)
assert(latestOfConstraint == Some(Latest.Stable))
```

`Latest` values are ordered, from `Stable` to `Release` to `Integration`. When merging constraints,
the highest one is retained. Picking an actual version out of them is left to coursier.
```scala mdoc
val mergedLatest = VersionConstraint.merge(
  VersionConstraint("latest.stable"),
  VersionConstraint("latest.release")
)
```

```scala mdoc:passthrough
assert(mergedLatest.flatMap(_.latest) == Some(Latest.Release))
```

## `ModuleMatcher`

`ModuleMatcher` aims at representing glob-based module matchers, like
```scala mdoc
val matcher = ModuleMatcher("org.scala-lang.modules", "*")
val matchesScalaXml = matcher.matches("org.scala-lang.modules", "scala-xml_2.13")
val matchesCoursier = matcher.matches("io.get-coursier", "coursier_2.13")
```

```scala mdoc:passthrough
assert(matchesScalaXml)
assert(!matchesCoursier)
```

`*` matches any sequence of characters. `ModuleMatcher` can also match module attributes, whose
keys have to be the same as those of the module for it to match:
```scala mdoc
val attributeMatcher = ModuleMatcher("org.foo", "bar", Map("scalaVersion" -> "2.*"))
val matchesAttributes = attributeMatcher.matches("org.foo", "bar", Map("scalaVersion" -> "2.13"))
val missingAttributes = attributeMatcher.matches("org.foo", "bar")
```

```scala mdoc:passthrough
assert(matchesAttributes)
assert(!missingAttributes)
```

## `ModuleMatchers`

Slightly more powerful than [`ModuleMatcher`](#modulematcher), `ModuleMatchers` can
either include or exclude all modules by default, and both include or exclude modules
from glob-based expressions, like
```scala mdoc
val allButScalaLib = ModuleMatchers(
  exclude = Set(ModuleMatcher("org.scala-lang", "scala-library")),
  include = Set(),
  includeByDefault = true
)
val notScalaLib = allButScalaLib.matches("io.get-coursier", "coursier_2.13")
val scalaLib = allButScalaLib.matches("org.scala-lang", "scala-library")
```

```scala mdoc:passthrough
assert(notScalaLib)
assert(!scalaLib)
```

`include` takes precedence over `exclude` if modules are included by default, and the other way
around if they aren't.
```scala mdoc
val allButScalaLangExceptLibrary = ModuleMatchers(
  exclude = Set(ModuleMatcher("org.scala-lang", "*")),
  include = Set(ModuleMatcher("org.scala-lang", "scala-library"))
)
val scalaLibIncluded = allButScalaLangExceptLibrary.matches("org.scala-lang", "scala-library")
val scalaReflectExcluded = allButScalaLangExceptLibrary.matches("org.scala-lang", "scala-reflect")
```

```scala mdoc:passthrough
assert(scalaLibIncluded)
assert(!scalaReflectExcluded)
```

`ModuleMatchers.all` matches all modules, and `ModuleMatchers.only` only a given one:
```scala mdoc
val matchesAll = ModuleMatchers.all.matches("io.get-coursier", "coursier_2.13")
val onlyScalaLib = ModuleMatchers.only("org.scala-lang", "scala-library")
val onlyScalaLibMatches = onlyScalaLib.matches("org.scala-lang", "scala-library")
val onlyScalaLibOther = onlyScalaLib.matches("io.get-coursier", "coursier_2.13")
```

```scala mdoc:passthrough
assert(matchesAll)
assert(onlyScalaLibMatches)
assert(!onlyScalaLibOther)
```

## `VersionCompatibility`

`VersionCompatibility` checks whether two versions, or a version constraint and a version,
are compatible with each other.

The following algorithms are available:
- `EarlySemVer`: early semantic versioning that keeps 0.6.z bincompat
- `SemVerSpec`: semantic versioning spec that treats all 0.y.z as initial development
- `PackVer`: package versioning policy
- `Strict`: the version has to be exactly the one of the constraint, or to be in its interval
- `Always`: assume any input constraint and version match
- `Default`: currently equal to `PackVer`

`SemVer` is a deprecated alias of `EarlySemVer`.

```scala mdoc
val semVerCheck1 = VersionCompatibility.EarlySemVer.isCompatible("1.2.0", "1.2.4")
val semVerCheck2 = VersionCompatibility.EarlySemVer.isCompatible("1.2+", "1.2.4")
val semVerCheck3 = VersionCompatibility.EarlySemVer.isCompatible("2.1.3", "1.2.4")
```

```scala mdoc:passthrough
assert(semVerCheck1)
assert(semVerCheck2)
assert(!semVerCheck3)
```

`Strict` doesn't allow for any difference between two versions, but a version only has to be in
the interval of an interval constraint, like `1.2+` here:
```scala mdoc
val strictCheck1 = VersionCompatibility.Strict.isCompatible("1.2.0", "1.2.4")
val strictCheck2 = VersionCompatibility.Strict.isCompatible("1.2+", "1.2.4")
val strictCheck3 = VersionCompatibility.Strict.isCompatible("2.1.3", "1.2.4")
```

```scala mdoc:passthrough
assert(!strictCheck1)
assert(strictCheck2)
assert(!strictCheck3)
```

`minimumCompatibleVersion` gives the lowest version that a version is compatible with:
```scala mdoc
val earlySemVerMin = VersionCompatibility.EarlySemVer.minimumCompatibleVersion("1.2.4")
val earlySemVerMin0 = VersionCompatibility.EarlySemVer.minimumCompatibleVersion("0.6.3")
val semVerSpecMin0 = VersionCompatibility.SemVerSpec.minimumCompatibleVersion("0.6.3")
val packVerMin = VersionCompatibility.PackVer.minimumCompatibleVersion("1.2.4")
```

```scala mdoc:passthrough
assert(earlySemVerMin == "1")
assert(earlySemVerMin0 == "0.6")
assert(semVerSpecMin0 == "0.6.3")
assert(packVerMin == "1.2")
```

`VersionCompatibility` instances can be parsed from strings, among `default`, `always`, `strict`,
`early-semver`, `semver-spec`, and `pvp`. Beware that `semver` throws an exception rather than
returning `None`, as it's ambiguous between `early-semver` and `semver-spec`.
```scala mdoc
val parsedCompatibility = VersionCompatibility("early-semver")
val unknownCompatibility = VersionCompatibility("foo")
```

```scala mdoc:passthrough
assert(parsedCompatibility == Some(VersionCompatibility.EarlySemVer))
assert(unknownCompatibility.isEmpty)
assert(scala.util.Try(VersionCompatibility("semver")).isFailure)
```

## `ConstraintReconciliation`

`ConstraintReconciliation` reconciles several version constraints during resolution:
- `Default`: relies on [`VersionConstraint.merge`](#versionconstraint), fails if intervals don't overlap
- `Relaxed`: always succeeds, discarding the lowest intervals if they don't overlap
- `Strict` and `SemVer`: behave like `Default` here. Their actual checks are done by coursier
during resolution.

```scala mdoc
val conflicting = Seq(VersionConstraint("[1.0,1.1)"), VersionConstraint("[1.2,1.3)"))
val defaultReconciliation = ConstraintReconciliation.Default.reconcile(conflicting)
val relaxedReconciliation = ConstraintReconciliation.Relaxed.reconcile(conflicting)
```

```scala mdoc:passthrough
assert(defaultReconciliation.isEmpty)
assert(relaxedReconciliation.map(_.asString) == Some("[1.2,1.3)"))
```

Instances can be parsed from `default`, `relaxed`, `strict`, or `semver`, and can be derived from
a [`VersionCompatibility`](#versioncompatibility). `Always` then corresponds to `Relaxed`, and
all the others to `Default`.
```scala mdoc
val parsedReconciliation = ConstraintReconciliation("relaxed")
val alwaysReconciliation = ConstraintReconciliation(VersionCompatibility.Always)
val earlySemVerReconciliation = ConstraintReconciliation(VersionCompatibility.EarlySemVer)
```

```scala mdoc:passthrough
assert(parsedReconciliation == Some(ConstraintReconciliation.Relaxed))
assert(alwaysReconciliation == ConstraintReconciliation.Relaxed)
assert(earlySemVerReconciliation == ConstraintReconciliation.Default)
```

## `Previous`

`Previous.previousStableVersion` guesses the stable version preceding a version. For a version
with a build metadata part, such as those computed from git tags, that's the part before the `+`.
It returns `None` if it can't guess that version.

```scala mdoc
val previousOfRelease = Previous.previousStableVersion("1.2.3")
val previousOfMilestone = Previous.previousStableVersion("1.2.3-RC1")
val previousOfGitVersion = Previous.previousStableVersion("1.2.3+4-abcdef")
val noPrevious = Previous.previousStableVersion("1.2.0")
```

```scala mdoc:passthrough
assert(previousOfRelease == Some("1.2.2"))
assert(previousOfMilestone == Some("1.2.2"))
assert(previousOfGitVersion == Some("1.2.3"))
assert(noPrevious.isEmpty)
```

## Contributing

*coursier-versions* is built with [Mill](https://mill-build.org), via the `./mill` launcher at the
root of this repository.

Run the tests for all platforms and Scala versions, or only for the JVM, with
```text
$ ./mill __.test
$ ./mill versions.jvm.__.test
```

Check binary compatibility with
```text
$ ./mill __.mimaReportBinaryIssues
```

`README.md` is generated from `README.template.md` with [mdoc](https://scalameta.org/mdoc), so that
its Scala snippets are compiled and run, and their outputs are up-to-date. Edit the template rather
than `README.md`, then re-generate the latter with the command below, which requires
[`cs`](https://get-coursier.io/docs/cli-installation) to be installed:
```text
$ ./mill mdoc
```
The version in the [Usage](#usage) section has to be updated by hand after a release.
