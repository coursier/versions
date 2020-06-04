package coursier.version

import utest._

object VersionCompatibilityTests extends TestSuite {

  val tests = Tests {
    "compatible" - {
      * - {
        val compatible = VersionCompatibility.PackVer.isCompatible("0.1.0", "0.1.0+foo")
        assert(compatible)
      }
    }
  }

}
