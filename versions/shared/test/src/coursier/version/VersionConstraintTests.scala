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
          VersionParse.versionConstraint("2.8")).preferred.head.repr
        assert(s0 == "2.8")
      }
    }
  }

}
