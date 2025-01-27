package coursier.version

import utest._

object PreviousTests extends TestSuite {

  def check(version: String, expectedPrevious: String): Unit = {
    val previousOpt = Previous.previousStableVersion(version)
    assert(previousOpt.contains(expectedPrevious))
  }
  def checkEmpty(version: String): Unit = {
    val previousOpt = Previous.previousStableVersion(version)
    assert(previousOpt.isEmpty)
  }

  val tests = Tests {
    test {
      check("0.1.2", "0.1.1")
    }
    test {
      check("1.1", "1.0")
    }
    test {
      check("1.1+3", "1.1")
    }
    test {
      check("1.1-RC2", "1.0")
    }

    test {
      checkEmpty("1.0-RC2")
    }
    test {
      checkEmpty("1.0-RC2+43")
    }
    test {
      checkEmpty("0.0.0+3-70919203-SNAPSHOT")
    }
  }

}
