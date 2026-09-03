package coursier.version

import utest._

object ModuleMatcherTests extends TestSuite {

  def check(matcher: ModuleMatcher, organization: String, name: String): Unit =
    assert(matcher.matches(organization, name))
  def checkNot(matcher: ModuleMatcher, organization: String, name: String): Unit =
    assert(!matcher.matches(organization, name))

  val tests = Tests {
    test("all") {
      check(ModuleMatcher.all, "org.scala-lang", "scala-library")
      check(ModuleMatcher.all, "io.get-coursier", "versions_2.13")
    }

    test("exact") {
      val matcher = ModuleMatcher("io.get-coursier", "versions_2.13")
      check(matcher, "io.get-coursier", "versions_2.13")
      checkNot(matcher, "io.get-coursier", "versions_2.12")
      checkNot(matcher, "io.get-coursier.thing", "versions_2.13")
    }

    test("specialChars") {
      // '.' and '-' are quoted, not interpreted as regex constructs
      val matcher = ModuleMatcher("io.get-coursier", "versions_2.13")
      checkNot(matcher, "ioaget-coursier", "versions_2.13")
      checkNot(matcher, "io.get-coursier", "versions_2a13")
    }

    test("blob") {
      val matcher = ModuleMatcher("io.get-coursier*", "versions_*")
      check(matcher, "io.get-coursier", "versions_2.13")
      check(matcher, "io.get-coursier.scala-cli", "versions_native0.5_2.13")
      checkNot(matcher, "org.get-coursier", "versions_2.13")
      checkNot(matcher, "io.get-coursier", "coursier_2.13")
    }

    test("leadingBlob") {
      val matcher = ModuleMatcher("*", "*_2.13")
      check(matcher, "io.get-coursier", "versions_2.13")
      checkNot(matcher, "io.get-coursier", "versions_2.12")
    }

    test("attributes") {
      val matcher = ModuleMatcher("io.get-coursier", "versions_2.13", Map("scope" -> "*"))
      assert(matcher.matches("io.get-coursier", "versions_2.13", Map("scope" -> "test")))
      assert(!matcher.matches("io.get-coursier", "versions_2.13", Map.empty))
      assert(!matcher.matches("io.get-coursier", "versions_2.13", Map("other" -> "test")))
    }
  }
}
