package coursier.version

import utest._

object VersionCompatibilityTests extends TestSuite {

  def compatible(wanted: String, selected: String)(implicit compat: VersionCompatibility): Unit = {
    val compatible = compat.isCompatible(wanted, selected)
    assert(compatible)
  }
  def incompatible(wanted: String, selected: String)(implicit compat: VersionCompatibility): Unit = {
    val compatible = compat.isCompatible(wanted, selected)
    assert(!compatible)
  }

  def minimumCompatible(version: String, expectedMinimum: String)(implicit compat: VersionCompatibility): Unit = {
    val minimum = compat.minimumCompatibleVersion(version)
    assert(minimum == expectedMinimum)
  }

  val tests = Tests {
    test("semver") {

      implicit val compat = VersionCompatibility.EarlySemVer

      test {
        compatible("1.1.0", "1.2.3")
      }
      test {
        compatible("1.0.0", "1.2.3")
      }
      test {
        compatible("1.1.0", "1.2.3-RC1")
      }
      test {
        incompatible("1.2.3-RC1", "1.2.3-RC2")
      }

      test {
        compatible("0.1.1", "0.1.2")
      }
      test {
        incompatible("0.1.1", "0.2.2")
      }

      test {
        minimumCompatible("1.0.1", "1")
      }
      test {
        minimumCompatible("1.1.2", "1")
      }

      test {
        minimumCompatible("0.0.1", "0.0")
      }
      test {
        minimumCompatible("0.1.2", "0.1")
      }
      test {
        minimumCompatible("1.0.0", "1")
      }
      test {
        minimumCompatible("0.0.0", "0.0")
      }
      test {
        minimumCompatible("0.1.0", "0.1")
      }
    }

    test("semverspec") {

      implicit val compat = VersionCompatibility.SemVerSpec

      test {
        compatible("1.1.0", "1.2.3")
      }
      test {
        compatible("1.0.0", "1.2.3")
      }
      test {
        compatible("1.1.0", "1.2.3-RC1")
      }
      test {
        incompatible("1.2.3-RC1", "1.2.3-RC2")
      }

      test {
        incompatible("0.1.1", "0.1.2")
      }
      test {
        incompatible("0.1.1", "0.2.2")
      }

      test {
        minimumCompatible("1.0.1", "1")
      }
      test {
        minimumCompatible("1.1.2", "1")
      }

      test {
        minimumCompatible("0.0.1", "0.0.1")
      }
      test {
        minimumCompatible("0.1.2", "0.1.2")
      }
      test {
        minimumCompatible("1.0.0", "1")
      }
      test {
        minimumCompatible("0.0.0", "0.0.0")
      }
      test {
        minimumCompatible("0.1.0", "0.1.0")
      }
    }

    test("maven-style qualifiers") {

      // https://github.com/coursier/versions/issues/10
      // Netty and Jetty put a qualifier in a fourth, dot-separated segment. Those are releases,
      // not pre-releases, so compatibility has to be decided on the numeric part.

      test("early semver") {
        implicit val compat = VersionCompatibility.EarlySemVer

        test("netty") {
          compatible("3.7.0.Final", "3.10.1.Final")
          compatible("4.1.100.Final", "4.1.104.Final")
          incompatible("3.10.1.Final", "3.7.0.Final")
          incompatible("3.7.0.Final", "4.0.0.Final")
        }
        test("jetty") {
          compatible("9.2.14.v20151106", "9.4.25.v20191220")
          incompatible("9.4.25.v20191220", "9.2.14.v20151106")
          incompatible("9.2.14.v20151106", "10.0.0.v20200101")
        }
        test("qualifier on one side only") {
          compatible("4.1.100", "4.1.104.Final")
          compatible("4.1.100.Final", "4.1.104")
        }
        test("other release qualifiers") {
          compatible("1.2.3.RELEASE", "1.5.0.RELEASE")
          compatible("1.2.3.GA", "1.5.0.GA")
          compatible("8.0.1.jre8", "8.4.1.jre8")
        }
        test("still significant for 0.x") {
          compatible("0.1.1.Final", "0.1.2.Final")
          incompatible("0.1.1.Final", "0.2.0.Final")
        }
      }

      test("semver spec") {
        implicit val compat = VersionCompatibility.SemVerSpec

        test {
          compatible("3.7.0.Final", "3.10.1.Final")
        }
        test {
          compatible("9.2.14.v20151106", "9.4.25.v20191220")
        }
        test {
          incompatible("3.10.1.Final", "3.7.0.Final")
        }
      }

      test("pre-releases stay incompatible") {
        // a qualifier right after a '-' is a semver pre-release, and a constraint on a
        // pre-release only accepts itself
        val compatibilities = Seq(VersionCompatibility.EarlySemVer, VersionCompatibility.SemVerSpec)
        val preReleases = Seq(
          "1.2.3-RC1",
          "1.2.3-M3",
          "1.2.3-alpha1",
          "1.2.3-beta2",
          "1.2.3-SNAPSHOT",
          "1.2.3-milestone2",
          "1.2.3-pre2",
          // well-known markers count as pre-releases wherever they show up
          "1.2.3.RC1",
          "1.2.3.SNAPSHOT",
          "1.2.3.pre2"
        )
        for (compat <- compatibilities; wanted <- preReleases) {
          val compatible = compat.isCompatible(wanted, "1.9.9")
          Predef.assert(!compatible, s"Expected '1.9.9' not to be compatible with '$wanted' per $compat")
        }
      }
    }

    test("package versioning") {

      implicit val compat = VersionCompatibility.PackVer

      test {
        incompatible("1.1.0", "1.2.3")
      }
      test {
        incompatible("1.0.0", "1.2.3")
      }
      test {
        incompatible("1.1.0", "1.2.3-RC1")
      }
      test {
        compatible("1.0.0", "1.0.1")
      }
      test {
        compatible("0.1.0", "0.1.0+foo")
      }

      test {
        minimumCompatible("1.0.1", "1.0")
      }
      test {
        minimumCompatible("1.1.2", "1.1")
      }

      test {
        minimumCompatible("0.0.1", "0.0")
      }
      test {
        minimumCompatible("0.1.2", "0.1")
      }
      test {
        minimumCompatible("1.0.0", "1.0")
      }
    }

    test("all") {

      val compatibilities = Seq(
        VersionCompatibility.EarlySemVer,
        VersionCompatibility.SemVerSpec,
        VersionCompatibility.PackVer,
        VersionCompatibility.Strict
      )

      def compatible(wanted: String, selected: String): Unit =
        for (compat <- compatibilities) {
          val compatible = compat.isCompatible(wanted, selected)
          Predef.assert(compatible, s"Expected '$selected' to be compatible with '$wanted' per $compat")
        }
      def incompatible(wanted: String, selected: String): Unit =
        for (compat <- compatibilities) {
          val compatible = compat.isCompatible(wanted, selected)
          Predef.assert(!compatible, s"Expected '$selected' not to be compatible with '$wanted' per $compat")
        }

      test {
        incompatible("1.1+", "1.2.3")
      }
      test {
        compatible("[1.1,1.3)", "1.2.3")
      }
      test {
        incompatible("[1.1,1.2)", "1.2.3")
      }
    }
  }

}
