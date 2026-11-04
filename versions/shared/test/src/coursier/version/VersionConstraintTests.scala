package coursier.version

import utest._

object VersionConstraintTests extends TestSuite {

  val tests = Tests {
    test("parse") {
      test("empty") {
        val c0 = VersionParse.versionConstraint("")
        assert(c0 == VersionConstraint.empty)
      }
      test("basicVersion") {
        val c0 = VersionParse.versionConstraint("1.2")
        assert(c0 == VersionConstraint.fromPreferred("1.2", Version("1.2")))
      }
      test("basicVersionInterval") {
        val c0 = VersionParse.versionConstraint("(,1.2]")
        assert(c0 == VersionConstraint.fromInterval("(,1.2]", VersionInterval(None, Some(Version("1.2")), false, true)))
      }
      test("latestSubRevision") {
        val c0 = VersionParse.versionConstraint("1.2.3-+")
        assert(c0 == VersionConstraint.fromInterval("1.2.3-+", VersionInterval(Some(Version("1.2.3")), Some(Version("1.2.3-max")), true, true)))
      }
      test("latestSubRevisionWithLiteral") {
        val c0 = VersionParse.versionConstraint("1.2.3-rc-+")
        assert(c0 == VersionConstraint.fromInterval("1.2.3-rc-+", VersionInterval(Some(Version("1.2.3-rc")), Some(Version("1.2.3-rc-max")), true, true)))
      }
      test("latestSubRevisionWithZero") {
        val c0 = VersionParse.versionConstraint("1.0.+")
        assert(c0 == VersionConstraint.fromInterval("1.0.+", VersionInterval(Some(Version("1.0")), Some(Version("1.0.max")), true, true)))
      }
    }

    test("empty") {
      val c0 = VersionConstraint("")
      assert(c0 == VersionConstraint.empty)
      assert(c0.preferred.isEmpty)
      assert(!c0.interval.contains(Version("0")))
      assert(!c0.interval.contains(Version("1")))
    }

    test("interval") {
      test("checkZero") {
        val v103 = Version("1.0.3")
        val v107 = Version("1.0.7")
        val v112 = Version("1.1.2")
        val c0 = VersionInterval(Some(Version("1.0")), Some(Version("1.0.max")), true, true)
        assert(c0.contains(v103))
        assert(c0.contains(v107))
        assert(!c0.contains(v112))
      }
      test("subRevision") {
        val v = Version("1.2.3-rc")
        val c0 = VersionInterval(Some(v),  Some(Version("1.2.3-rc-max")), true, true)
        assert(c0.contains(v))
        assert(c0.contains(Version("1.2.3-rc-500")))
        assert(!c0.contains(Version("1.2.3-final")))
      }
    }

    test("repr") {
      test("empty") {
        val s0 = VersionConstraint.empty.generateString
        assert(s0 == "")
      }
      test("preferred") {
        val s0 = VersionConstraint.fromPreferred("2.1", Version("2.1")).generateString
        assert(s0 == "2.1")
      }
      test("interval") {
        val s0 = VersionConstraint.fromInterval("(,2.1]", VersionInterval(None, Some(Version("2.1")), false, true)).generateString
        assert(s0 == "(,2.1]")
      }
    }

    test("merge") {
      test {
        val s0 = VersionConstraint.merge(
          VersionParse.versionConstraint("[1.0,3.2]"),
          VersionParse.versionConstraint("[3.0,4.0)")).get.generateString
        assert(s0 == "[3.0,3.2]")
      }

      test {
        val c0 = VersionConstraint.merge(
          VersionParse.versionConstraint("[1.0,2.0)"),
          VersionParse.versionConstraint("[3.0,4.0)"))
        assert(c0.isEmpty)
      }

      test {
        val c0 = VersionConstraint.merge(
          VersionParse.versionConstraint("[1.0,2.0)"),
          VersionParse.versionConstraint("[3.0,4.0)"),
          VersionParse.versionConstraint("2.8"))
        assert(c0.isEmpty)
      }

      test {
        val c0 = VersionConstraint.merge(
          VersionParse.versionConstraint("[1.0,3.2)"),
          VersionParse.versionConstraint("[3.0,4.0)"),
          VersionParse.versionConstraint("2.8")).get.generateString
        assert(c0 == "[3.0,3.2)")
      }

      test {
        val c0 = VersionConstraint.merge(
          VersionParse.versionConstraint("[1.0,3.2)"),
          VersionParse.versionConstraint("[3.0,4.0)"),
          VersionParse.versionConstraint("3.0")).get.generateString
        pprint.err.log(c0)
        assert(c0 == "[3.0,3.2)&3.0")
      }

      test {
        val c0 = VersionConstraint.merge(
          VersionParse.versionConstraint("[1.0,3.2)"),
          VersionParse.versionConstraint("[3.0,4.0)"),
          VersionParse.versionConstraint("3.1")).get.generateString
        pprint.err.log(c0)
        assert(c0 == "[3.0,3.2)&3.1")
      }

      test {
        val c0 = VersionConstraint.merge(
          VersionParse.versionConstraint("[1.0,3.2)"),
          VersionParse.versionConstraint("[3.0,4.0)"),
          VersionParse.versionConstraint("3.2"))
        pprint.err.log(c0)
        assert(c0.isEmpty)
      }

      test {
        val c0 = VersionConstraint.merge(
          VersionParse.versionConstraint("[1.0,3.2)"),
          VersionParse.versionConstraint("[3.0,4.0)"),
          VersionParse.versionConstraint("4.0"))
        pprint.err.log(c0)
        assert(c0.isEmpty)
      }

      test("emptyVersionLowerBound") {
        // both render as (,1.0], but parsing that gives the latter
        val withEmpty = VersionConstraint.from(VersionInterval(Some(Version("")), Some(Version("1.0")), false, true), None, None)
        val withNone = VersionConstraint.from(VersionInterval(None, Some(Version("1.0")), false, true), None, None)
        val expected = Some(VersionInterval(None, Some(Version("1.0")), false, true))
        assert(VersionConstraint.merge(withEmpty, withNone).map(_.interval) == expected)
        assert(VersionConstraint.merge(withNone, withEmpty).map(_.interval) == expected)
      }

      test("sameStringDifferentInterval") {
        // built by hand, these don't go through the normalization of from, and only share
        // their string representation
        val below = Version("alpha")
        assert(below.compareSemantic(Version.zero) < 0)
        val bounded = VersionConstraint.Eager("(alpha,)", VersionInterval(Some(below), Some(Version.zero), false, false), None, None)
        val unbounded = VersionConstraint.from(VersionInterval(Some(below), None, false, false), None, None)
        assert(bounded == unbounded)
        val expected = Some(VersionInterval(Some(below), Some(Version.zero), false, false))
        assert(VersionConstraint.merge(bounded, unbounded).map(_.interval) == expected)
        assert(VersionConstraint.merge(unbounded, bounded).map(_.interval) == expected)
      }
    }

    test("from") {
      test("emptyVersionLowerBound") {
        val c0 = VersionConstraint.from(VersionInterval(Some(Version("")), Some(Version("1.0")), true, true), None, None)
        val c1 = VersionConstraint.from(VersionInterval(None, Some(Version("1.0")), false, true), None, None)
        assert(c0.asString == "(,1.0]")
        assert(c0 == c1)
        assert(c0.interval == c1.interval)
        assert(c0.interval == VersionParse.versionConstraint(c0.asString).interval)
      }
      test("emptyVersionLowerBoundWithPreferred") {
        val c0 = VersionConstraint.from(VersionInterval(Some(Version("")), Some(Version("2.0")), false, false), Some(Version("1.0")), None)
        assert(c0.asString == "(,2.0)&1.0")
        assert(c0.interval == VersionInterval(None, Some(Version("2.0")), false, false))
      }
      test("emptyVersionBounds") {
        val c0 = VersionConstraint.from(VersionInterval(Some(Version("")), Some(Version("")), false, false), None, None)
        assert(c0 == VersionConstraint.empty)
        assert(c0.interval == VersionInterval.empty)
      }
      test("empty") {
        val c0 = VersionConstraint.from(VersionInterval.empty, None, None)
        assert(c0 == VersionConstraint.empty)
        assert(c0.interval == VersionInterval.empty)
      }
    }

    test("relaxedMerge") {
      test {
        val s0 = VersionConstraint.relaxedMerge(
          VersionParse.versionConstraint("[1.0,2.0)"),
          VersionParse.versionConstraint("[3.0,4.0)")).generateString
        assert(s0 == "[3.0,4.0)")
      }

      test {
        val s0 = VersionConstraint.relaxedMerge(
          VersionParse.versionConstraint("[1.0,2.0)"),
          VersionParse.versionConstraint("[3.0,4.0)"),
          VersionParse.versionConstraint("2.8")).generateString
        assert(s0 == "[3.0,4.0)")
      }
    }
  }

}
