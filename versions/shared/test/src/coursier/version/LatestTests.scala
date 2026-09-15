package coursier.version

import utest._

object LatestTests extends TestSuite {

  val tests = Tests {
    test("parse") {
      test("integration") {
        assert(Latest("latest.integration") == Some(Latest.Integration))
      }
      test("release") {
        assert(Latest("latest.release") == Some(Latest.Release))
      }
      test("stable") {
        assert(Latest("latest.stable") == Some(Latest.Stable))
      }
      test("notALatest") {
        assert(Latest("1.0.0").isEmpty)
        assert(Latest("[1.0,2.0)").isEmpty)
        assert(Latest("").isEmpty)
      }
    }

    test("mavenMetaVersion") {
      test("RELEASE") {
        assert(Latest.mavenMetaVersion("RELEASE") == Some(Latest.Release))
        assert(Latest("RELEASE") == Some(Latest.Release))
      }
      test("LATEST") {
        assert(Latest.mavenMetaVersion("LATEST") == Some(Latest.Integration))
        assert(Latest("LATEST") == Some(Latest.Integration))
      }
      test("caseSensitive") {
        assert(Latest.mavenMetaVersion("release").isEmpty)
        assert(Latest.mavenMetaVersion("latest").isEmpty)
        assert(Latest.mavenMetaVersion("Release").isEmpty)
      }
      test("notAMetaVersion") {
        assert(Latest.mavenMetaVersion("1.0.0").isEmpty)
        assert(Latest.mavenMetaVersion("latest.release").isEmpty)
      }
    }

    test("asString") {
      // the canonical coursier spelling, whichever alias was parsed
      assert(Latest("RELEASE").map(_.asString) == Some("latest.release"))
      assert(Latest("LATEST").map(_.asString) == Some("latest.integration"))
    }

    test("versionConstraint") {
      test("RELEASE") {
        val c0 = VersionParse.versionConstraint("RELEASE")
        assert(c0 == VersionConstraint.fromLatest("RELEASE", Latest.Release))
        assert(c0.latest == Some(Latest.Release))
        assert(c0.preferred.isEmpty)
        assert(c0.generateString == "latest.release")
      }
      test("LATEST") {
        val c0 = VersionParse.versionConstraint("LATEST")
        assert(c0 == VersionConstraint.fromLatest("LATEST", Latest.Integration))
        assert(c0.latest == Some(Latest.Integration))
        assert(c0.preferred.isEmpty)
        assert(c0.generateString == "latest.integration")
      }
      test("lazy") {
        val c0 = VersionConstraint("RELEASE")
        assert(c0.latest == Some(Latest.Release))
        assert(c0.asString == "RELEASE")
      }
      test("merge") {
        // reconciling a meta version with its latest.* counterpart keeps a single latest
        val merged = VersionConstraint.merge(
          VersionConstraint("RELEASE"),
          VersionConstraint("latest.release")
        )
        assert(merged.map(_.latest) == Some(Some(Latest.Release)))
        assert(merged.map(_.asString) == Some("latest.release"))
      }
    }
  }
}
