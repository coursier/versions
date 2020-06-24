package coursier.version

import utest._

object VersionCompatibilityTests extends TestSuite {

  val tests = Tests {
    "semver" - {

      def compatible(wanted: String, selected: String): Unit = {
        val compatible = VersionCompatibility.SemVer.isCompatible(wanted, selected)
        assert(compatible)
      }
      def incompatible(wanted: String, selected: String): Unit = {
        val compatible = VersionCompatibility.SemVer.isCompatible(wanted, selected)
        assert(!compatible)
      }

      * - compatible("1.1.0", "1.2.3")
      * - compatible("1.1.0", "1.2.3-RC1")

      * - compatible("0.1.1", "0.1.2")
      * - incompatible("0.1.1", "0.2.2")
    }

    "semverspec" - {

      def compatible(wanted: String, selected: String): Unit = {
        val compatible = VersionCompatibility.SemVerSpec.isCompatible(wanted, selected)
        assert(compatible)
      }
      def incompatible(wanted: String, selected: String): Unit = {
        val compatible = VersionCompatibility.SemVerSpec.isCompatible(wanted, selected)
        assert(!compatible)
      }

      * - compatible("1.1.0", "1.2.3")
      * - compatible("1.1.0", "1.2.3-RC1")

      * - incompatible("0.1.1", "0.1.2")
      * - incompatible("0.1.1", "0.2.2")
    }

    "package versioning" - {

      def compatible(wanted: String, selected: String): Unit = {
        val compatible = VersionCompatibility.PackVer.isCompatible(wanted, selected)
        assert(compatible)
      }
      def incompatible(wanted: String, selected: String): Unit = {
        val compatible = VersionCompatibility.PackVer.isCompatible(wanted, selected)
        assert(!compatible)
      }

      * - incompatible("1.1.0", "1.2.3")
      * - incompatible("1.1.0", "1.2.3-RC1")
      * - compatible("0.1.0", "0.1.0+foo")
    }
  }

}
