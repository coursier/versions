# coursier-versions

*coursier-versions* is the library parsing and comparing versions of
[coursier](https://get-coursier.io).

It's mainly designed around the needs of coursier itself, but can find uses outside of it.

It aims at not breaking backward binary compatibility, and follows semantic versioning.
While still in `0.x`, backward binary compatibility hasn't been broken since its first release.

## Usage

Add to your `build.sbt`:
```scala
libraryDependencies += "io.get-coursier" %% "versions" % "0.2.1"
```

The latest version is
[![Maven Central](https://img.shields.io/maven-central/v/io.get-coursier/versions_2.13.svg)](https://maven-badges.herokuapp.com/maven-central/io.get-coursier/versions_2.13).

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
and a sequence of `Version`s can be sorted.

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
assert(intervalConstraint == VersionConstraint(VersionInterval(Some(Version("1.2")), Some(Version("1.3")), true, false), Nil))
assert(intervalSelectorConstraint == VersionConstraint(VersionInterval(Some(Version("1.2")), Some(Version("1.2.max")), true, true), Nil))
assert(simpleVersionConstraint == VersionConstraint(VersionInterval(None, None, false, false), Seq(Version("1.4.2"))))
```

Like for versions, parsing is meant to be very loose, trying to make sense of any input.

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
