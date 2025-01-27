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
