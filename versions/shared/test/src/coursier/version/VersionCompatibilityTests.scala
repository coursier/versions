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

    test("semantically equal versions") {

      test("early semver") {
        implicit val compat = VersionCompatibility.EarlySemVer

        test("padding") {
          compatible("1.2.0", "1.2")
          compatible("1.2", "1.2.0")
          compatible("1.0", "1")
          compatible("1", "1.0.0")
          compatible("1.2", "1.3")
          compatible("1.2.0", "1.3")
          incompatible("1.3", "1.2.0")
          incompatible("1.2.1", "1.2")
        }
        test("release qualifiers") {
          compatible("1.2.Final", "1.2")
          compatible("1.2", "1.2.Final")
          compatible("1.2.GA", "1.2.0.Final")
          compatible("1.2.0.Final", "1.2.1")
          compatible("1.Final", "1.0")
          incompatible("1.2.1.Final", "1.2.0")
          incompatible("1.2.SP1", "1.2")
        }
        test("build metadata") {
          compatible("1.2.0+b", "1.2.0")
          compatible("1.2.0", "1.2.0+b")
          compatible("1.2.0+a", "1.2.0+b")
          compatible("1.2+a", "1.2.0")
          incompatible("1.2.1+a", "1.2.0+b")
        }
        test("number representations") {
          compatible("0000000001.2.3", "1.2.3")
          compatible("1.2.0000000003", "1.2.3")
          incompatible("10000000000.2.3", "10000000001.2.3")
        }
        test("short 0.x versions") {
          compatible("0", "0.0")
          compatible("0.0", "0")
          compatible("0", "0.0.1")
          compatible("0.0.0", "0")
          compatible("0.Final", "0.0")
          compatible("0+a", "0.0")
          compatible("0.0", "0+a")
          compatible("0.1.0", "0.1")
          compatible("0.1.Final", "0.1.1")
          compatible("0.1.0+a", "0.1")
          compatible("0000000000.1", "0.1.2")
          incompatible("0", "0.1")
          incompatible("0.1", "0")
          incompatible("0.0.1", "0")
          incompatible("0.Final", "0.1")
          incompatible("0", "1")
          incompatible("1", "0")
          incompatible("0.1", "0.2")
        }
        test("non numeric significant parts") {
          incompatible("foo", "0")
          incompatible("Final", "0")
          incompatible("0", "Final")
          incompatible("0.foo", "0.0.1")
          incompatible("0.x", "0.0.1")
        }
      }

      test("semver spec") {
        implicit val compat = VersionCompatibility.SemVerSpec

        test {
          compatible("1.2.0", "1.2")
        }
        test {
          compatible("1.2", "1.2.0")
        }
        test {
          compatible("1.0", "1")
        }
        test {
          compatible("1.Final", "1.0")
        }
        test {
          compatible("1.2.Final", "1.2")
        }
        test {
          compatible("1.2.0+a", "1.2.0+b")
        }
        test {
          compatible("0000000001.2.3", "1.2.3")
        }
        test {
          incompatible("1.2.1", "1.2")
        }
        test {
          incompatible("1", "2")
        }

        test("0.x versions stay incompatible") {
          incompatible("0.1.0", "0.1")
          incompatible("0.1", "0.1.0")
          incompatible("0.1.Final", "0.1")
          incompatible("0.1+a", "0.1")
          incompatible("0", "0.0")
          incompatible("0.0", "0")
          incompatible("0", "Final")
        }
      }

      test("package versioning") {
        implicit val compat = VersionCompatibility.PackVer

        test("padding") {
          compatible("1", "1.0")
          compatible("1.0", "1")
          compatible("1", "1.0.3")
          compatible("0", "0.0.1")
          incompatible("1", "1.1")
          incompatible("1.1", "1")
        }
        test("release qualifiers") {
          compatible("1.Final", "1.0")
          compatible("1.0", "1.GA")
          compatible("1.Final", "1.GA")
          compatible("1.Final", "1.0.3")
          incompatible("1.Final", "1.1")
        }
        test("build metadata") {
          compatible("1+a", "1.0")
          compatible("1.0", "1+a")
          compatible("1+a", "1+b")
          incompatible("1+a", "1.1")
        }
        test("number representations") {
          compatible("0000000001.2.3", "1.2.4")
          compatible("1.0000000002", "1.2.4")
        }
        test("no order nor pre-release check") {
          compatible("1.0.5", "1.0.1")
          compatible("1.2-RC1", "1.2")
          compatible("1.2", "1.2-RC1")
        }
        test("literal and qualifiers") {
          compatible("1-RC1", "1-RC2")
          compatible("1.foo.1", "1.foo.2")
          compatible("foo.bar.1", "foo.bar.2")
          incompatible("1-RC1", "1.0")
          incompatible("1", "1-RC1")
          incompatible("1.foo", "1.0")
          incompatible("Final", "GA")
          incompatible("Final", "0")
        }
      }

      test("default") {
        implicit val compat = VersionCompatibility.Default
        compatible("1", "1.0")
        compatible("1.Final", "1.0")
      }

      test("pre-releases") {
        val compatibilities = Seq(VersionCompatibility.EarlySemVer, VersionCompatibility.SemVerSpec)

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

        test("constraints only accept themselves") {
          compatible("1.2.3-RC1", "1.2.3-RC1")
          incompatible("1.2.3-RC1", "1.2.3")
          incompatible("1.2-RC1", "1.2")
          incompatible("1-RC1", "1")
          incompatible("1-RC1", "1.0")
          incompatible("1.2.3-RC1", "1.2.3-RC1+b")
          incompatible("1.2-RC1", "1.2.0-RC1")
        }
        test("come before their release") {
          // pre-releases of a later version are accepted, but not those of the wanted one
          compatible("1.2.3", "1.2.4-RC1")
          compatible("1.2", "1.2.1-RC1")
          incompatible("1.2.3", "1.2.3-RC1")
          incompatible("1.2", "1.2-RC1")
          incompatible("1.2", "1.2.0-RC1")
          incompatible("1.2.3", "1.2.3-SNAPSHOT")
          incompatible("1", "1-RC1")
        }
      }

      test("minimum compatible version") {
        val versions = Seq(
          "0", "0.0", "0.0.0", "0.0.1", "0.1", "0.1.0", "0.1.2", "0.Final", "0.0.Final", "0+a",
          "0.1.0+a", "0.1.0-RC1", "0-RC1",
          "1", "1.0", "1.0.0", "1.2", "1.2.0", "1.2.3", "1.Final", "1.GA", "1.2.Final", "1.2.GA",
          "1+a", "1.2+a", "1.2.0+b", "1.2.3-RC1", "1.2-RC1", "1-RC1", "0000000001.2.3",
          "foo", "1.foo"
        )
        val compatibilities = Seq(
          VersionCompatibility.EarlySemVer,
          VersionCompatibility.SemVerSpec,
          VersionCompatibility.PackVer
        )

        test("minimums") {
          {
            implicit val compat = VersionCompatibility.EarlySemVer
            minimumCompatible("0", "0")
            minimumCompatible("1", "1")
            minimumCompatible("1.2.0", "1")
            minimumCompatible("1.2.Final", "1")
            minimumCompatible("1.2.0+b", "1")
            minimumCompatible("1.2.0-RC1", "1")
            minimumCompatible("1-RC1", "1-RC1")
          }
          {
            implicit val compat = VersionCompatibility.SemVerSpec
            minimumCompatible("1.2.0", "1")
            minimumCompatible("1.2", "1")
            minimumCompatible("0", "0")
          }
          {
            implicit val compat = VersionCompatibility.PackVer
            minimumCompatible("1", "1")
            minimumCompatible("1.0", "1.0")
            minimumCompatible("1.0-RC1", "1.0-RC1")
          }
        }

        test("accepts the version") {
          for (compat <- compatibilities; version <- versions) {
            val minimum = compat.minimumCompatibleVersion(version)
            Predef.assert(
              compat.isCompatible(minimum, version),
              s"Expected '$version' to be compatible with its minimum '$minimum' per $compat"
            )
          }
        }

        test("accepts what the version accepts") {
          for {
            compat <- compatibilities
            wanted <- versions
            selected <- versions
            if compat.isCompatible(wanted, selected)
          } {
            val minimum = compat.minimumCompatibleVersion(wanted)
            Predef.assert(
              compat.isCompatible(minimum, selected),
              s"Expected '$selected' to be compatible with '$minimum', the minimum of '$wanted', per $compat"
            )
          }
        }

        test("agree on versions compatible both ways") {
          for {
            compat <- compatibilities
            first <- versions
            second <- versions
            if compat.isCompatible(first, second) && compat.isCompatible(second, first)
          } {
            val firstMinimum = compat.minimumCompatibleVersion(first)
            val secondMinimum = compat.minimumCompatibleVersion(second)
            Predef.assert(
              compat.isCompatible(firstMinimum, secondMinimum),
              s"Expected minimums '$firstMinimum' and '$secondMinimum' of '$first' and '$second' to be compatible per $compat"
            )
          }
        }
      }
    }

    test("interval constraints") {
      val compatibilities = Seq(
        VersionCompatibility.EarlySemVer,
        VersionCompatibility.SemVerSpec,
        VersionCompatibility.PackVer
      )
      for (compat <- compatibilities) {
        Predef.assert(compat.isCompatible("[1.0,2.0)", "1.5"))
        Predef.assert(compat.isCompatible("[1.0,2.0)", "1"))
        Predef.assert(!compat.isCompatible("[1.0,2.0)", "2.0"))
        Predef.assert(compat.isCompatible("[0.1,0.3)", "0.2"))
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
