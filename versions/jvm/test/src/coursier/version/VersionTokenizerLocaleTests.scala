package coursier.version

import java.util.Locale

import coursier.version.Version._
import utest._

// JVM only: java.util.Locale doesn't link on Scala.js and Scala Native
object VersionTokenizerLocaleTests extends TestSuite {

  private def withDefaultLocale[T](locale: Locale)(f: => T): T = {
    val previous = Locale.getDefault
    Locale.setDefault(locale)
    try f
    finally Locale.setDefault(previous)
  }

  private def check(tag: String): Unit =
    withDefaultLocale(Locale.forLanguageTag(tag)) {
      assert(Version("1.0.FINAL").compareSemantic(Version("1.0")) == 0)
      assert(Version.items("1.0.FINAL") == Vector(Number(1), Number(0), Tag("final")))
      assert(Version.items("1.MIN") == Vector(Number(1), Min))
      assert(Version.items("1-~\u0130") == Vector(Number(1), Tag("~"), Tag("i\u0307")))
    }

  val tests = Tests {
    test("turkish") {
      // ensures the default locale does change how strings are lower-cased here
      withDefaultLocale(Locale.forLanguageTag("tr-TR")) {
        assert("FINAL".toLowerCase != "final")
      }
      check("tr-TR")
    }
    test("azeri") {
      check("az-AZ")
    }
    test("lithuanian") {
      check("lt-LT")
    }
  }
}
