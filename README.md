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
```scala
import coursier.version._
```

## `Version`

Version parsing and comparison is handled by `coursier.version.Version`.

Parse a version with
```scala
val version = Version("1.2.0-RC3")
// version: Version = Version(repr = "1.2.0-RC3")
val items = version.items
// items: Vector[Version.Item] = Vector(
//   Number(value = 1),
//   Number(value = 2),
//   Number(value = 0),
//   Tag(value = "rc"),
//   Number(value = 3)
// )
```

Parsing is meant to be very loose, and tries to make sense of any input. The actual
parsing happens when `items`, which is a `lazy val`, is called on `Version`.

If you'd like to reject potentially invalid inputs, use `VersionParse.version`:
```scala
val versionOpt = VersionParse.version("[1.2,1.3)")
// versionOpt: Option[Version] = None
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
```scala
Version("1.0").compare(Version("1.0.0"))
// res1: Int = -2
Version("1.0").compareSemantic(Version("1.0.0"))
// res2: Int = 0
Version("1.1.0").compareSemantic(Version("1.1.0.Final"))
// res3: Int = 0
```

## `VersionConstraint`

`VersionConstraint` aims at representing not only versions, but also version intervals, or
interval selectors such as `1.2+`.

Parse a version constraint with
```scala
val intervalConstraint = VersionParse.versionConstraint("[1.2,1.3)")
// intervalConstraint: VersionConstraint = Eager(
//   asString = "[1.2,1.3)",
//   interval = VersionInterval(
//     from = Some(value = Version(repr = "1.2")),
//     to = Some(value = Version(repr = "1.3")),
//     fromIncluded = true,
//     toIncluded = false
//   ),
//   preferred = None,
//   latest = None
// )
val intervalSelectorConstraint = VersionParse.versionConstraint("1.2+")
// intervalSelectorConstraint: VersionConstraint = Eager(
//   asString = "1.2+",
//   interval = VersionInterval(
//     from = Some(value = Version(repr = "1.2")),
//     to = Some(value = Version(repr = "1.2.max")),
//     fromIncluded = true,
//     toIncluded = true
//   ),
//   preferred = None,
//   latest = None
// )
val simpleVersionConstraint = VersionParse.versionConstraint("1.4.2")
// simpleVersionConstraint: VersionConstraint = Eager(
//   asString = "1.4.2",
//   interval = VersionInterval(
//     from = None,
//     to = None,
//     fromIncluded = false,
//     toIncluded = false
//   ),
//   preferred = Some(value = Version(repr = "1.4.2")),
//   latest = None
// )
```


Like for versions, parsing is meant to be very loose, trying to make sense of any input.

Use `VersionConstraint.merge` to find a constraint satisfying several ones. It keeps the
intersection of intervals and the highest preferred version, and returns `None` if the input
constraints can't be reconciled. `VersionConstraint.relaxedMerge` always succeeds, by giving
up on the lowest constraints when needed.
```scala
val mergedConstraint = VersionConstraint.merge(
  VersionConstraint("[1.2,1.4)"),
  VersionConstraint("1.3")
)
// mergedConstraint: Option[VersionConstraint] = Some(
//   value = Eager(
//     asString = "[1.2,1.4)&1.3",
//     interval = VersionInterval(
//       from = Some(value = Version(repr = "1.2")),
//       to = Some(value = Version(repr = "1.4")),
//       fromIncluded = true,
//       toIncluded = false
//     ),
//     preferred = Some(value = Version(repr = "1.3")),
//     latest = None
//   )
// )
val highestVersion = VersionConstraint.merge(
  VersionConstraint("1.2"),
  VersionConstraint("1.3")
)
// highestVersion: Option[VersionConstraint] = Some(
//   value = Eager(
//     asString = "1.3",
//     interval = VersionInterval(
//       from = None,
//       to = None,
//       fromIncluded = false,
//       toIncluded = false
//     ),
//     preferred = Some(value = Version(repr = "1.3")),
//     latest = None
//   )
// )
val noOverlap = VersionConstraint.merge(
  VersionConstraint("[1.0,1.1)"),
  VersionConstraint("[1.2,1.3)")
)
// noOverlap: Option[VersionConstraint] = None
val relaxed = VersionConstraint.relaxedMerge(
  VersionConstraint("[1.0,1.1)"),
  VersionConstraint("[1.2,1.3)")
)
// relaxed: VersionConstraint = Lazy(asString = "[1.2,1.3)")
```


## `VersionInterval`

`VersionInterval` represents the interval part of a version constraint. Its bounds are
compared with `compareSemantic`.

```scala
val interval = VersionParse.versionInterval("[1.2,1.3)").get
// interval: VersionInterval = VersionInterval(
//   from = Some(value = Version(repr = "1.2")),
//   to = Some(value = Version(repr = "1.3")),
//   fromIncluded = true,
//   toIncluded = false
// )
val inInterval = interval.contains(Version("1.2.5"))
// inInterval: Boolean = true
val upperBound = interval.contains(Version("1.3"))
// upperBound: Boolean = false
val intersection = interval.merge(VersionParse.versionInterval("[1.2.5,)").get)
// intersection: Option[VersionInterval] = Some(
//   value = VersionInterval(
//     from = Some(value = Version(repr = "1.2.5")),
//     to = Some(value = Version(repr = "1.3")),
//     fromIncluded = true,
//     toIncluded = false
//   )
// )
val noIntersection = interval.merge(VersionParse.versionInterval("[1.4,)").get)
// noIntersection: Option[VersionInterval] = None
val invalid = VersionParse.versionInterval("[1.3,1.2)").get.isValid
// invalid: Boolean = false
```


`VersionParse` also parses Ivy-like selectors, and the multi-interval syntax of Maven, of which
only the last interval is retained:
```scala
val ivySelector = VersionParse.ivyLatestSubRevisionInterval("1.2.+")
// ivySelector: Option[VersionInterval] = Some(
//   value = VersionInterval(
//     from = Some(value = Version(repr = "1.2")),
//     to = Some(value = Version(repr = "1.2.max")),
//     fromIncluded = true,
//     toIncluded = true
//   )
// )
val multiInterval = VersionParse.multiVersionInterval("[1.0,1.2),[1.3,)")
// multiInterval: Option[VersionInterval] = Some(
//   value = VersionInterval(
//     from = Some(value = Version(repr = "1.3")),
//     to = None,
//     fromIncluded = true,
//     toIncluded = false
//   )
// )
```


## `Latest`

`Latest` represents the `latest.integration`, `latest.release`, and `latest.stable` selectors.
The `LATEST` and `RELEASE` meta versions of Maven 2 are accepted too, and correspond to
`latest.integration` and `latest.release`.

```scala
val latestRelease = Latest("latest.release")
// latestRelease: Option[Latest] = Some(value = Release)
val mavenLatest = Latest("LATEST")
// mavenLatest: Option[Latest] = Some(value = Integration)
val notLatest = Latest("1.2")
// notLatest: Option[Latest] = None
val latestConstraint = VersionParse.versionConstraint("latest.stable")
// latestConstraint: VersionConstraint = Eager(
//   asString = "latest.stable",
//   interval = VersionInterval(
//     from = None,
//     to = None,
//     fromIncluded = false,
//     toIncluded = false
//   ),
//   preferred = None,
//   latest = Some(value = Stable)
// )
val latestOfConstraint = latestConstraint.latest
// latestOfConstraint: Option[Latest] = Some(value = Stable)
```


`Latest` values are ordered, from `Stable` to `Release` to `Integration`. When merging constraints,
the highest one is retained. Picking an actual version out of them is left to coursier.
```scala
val mergedLatest = VersionConstraint.merge(
  VersionConstraint("latest.stable"),
  VersionConstraint("latest.release")
)
// mergedLatest: Option[VersionConstraint] = Some(
//   value = Eager(
//     asString = "latest.release",
//     interval = VersionInterval(
//       from = None,
//       to = None,
//       fromIncluded = false,
//       toIncluded = false
//     ),
//     preferred = None,
//     latest = Some(value = Release)
//   )
// )
```


## `ModuleMatcher`

`ModuleMatcher` aims at representing glob-based module matchers, like
```scala
val matcher = ModuleMatcher("org.scala-lang.modules", "*")
// matcher: ModuleMatcher = ModuleMatcher(
//   organizationMatcher = "org.scala-lang.modules",
//   nameMatcher = "*",
//   attributeMatchers = Map()
// )
val matchesScalaXml = matcher.matches("org.scala-lang.modules", "scala-xml_2.13")
// matchesScalaXml: Boolean = true
val matchesCoursier = matcher.matches("io.get-coursier", "coursier_2.13")
// matchesCoursier: Boolean = false
```


## `ModuleMatchers`

Slightly more powerful than [`ModuleMatcher`](#modulematcher), `ModuleMatchers` can
either include or exclude all modules by default, and both include or exclude modules
from glob-based expressions, like
```scala
val allButScalaLib = ModuleMatchers(
  exclude = Set(ModuleMatcher("org.scala-lang", "scala-library")),
  include = Set(),
  includeByDefault = true
)
// allButScalaLib: ModuleMatchers = ModuleMatchers(
//   exclude = Set(
//     ModuleMatcher(
//       organizationMatcher = "org.scala-lang",
//       nameMatcher = "scala-library",
//       attributeMatchers = Map()
//     )
//   ),
//   include = Set(),
//   includeByDefault = true
// )
val notScalaLib = allButScalaLib.matches("io.get-coursier", "coursier_2.13")
// notScalaLib: Boolean = true
val scalaLib = allButScalaLib.matches("org.scala-lang", "scala-library")
// scalaLib: Boolean = false
```


## `VersionCompatibility`

`VersionCompatibility` checks whether two versions, or a version constraint and a version,
are compatible with each other.

The following algorithms are available:
- `EarlySemVer`: early semantic versioning that keeps 0.6.z bincompat
- `SemVerSpec`: semantic versioning spec that treats all 0.y.z as initial development
- `PackVer`: package versioning policy
- `Strict`: exact match required
- `Always`: assume any input constraint and version match
- `Default`: currently equal to `PackVer`

```scala
val semVerCheck1 = VersionCompatibility.EarlySemVer.isCompatible("1.2.0", "1.2.4")
// semVerCheck1: Boolean = true
val semVerCheck2 = VersionCompatibility.EarlySemVer.isCompatible("1.2+", "1.2.4")
// semVerCheck2: Boolean = true
val semVerCheck3 = VersionCompatibility.EarlySemVer.isCompatible("2.1.3", "1.2.4")
// semVerCheck3: Boolean = false
```


```scala
val strictCheck1 = VersionCompatibility.Strict.isCompatible("1.2.0", "1.2.4")
// strictCheck1: Boolean = false
val strictCheck2 = VersionCompatibility.Strict.isCompatible("1.2+", "1.2.4")
// strictCheck2: Boolean = true
val strictCheck3 = VersionCompatibility.Strict.isCompatible("2.1.3", "1.2.4")
// strictCheck3: Boolean = false
```

