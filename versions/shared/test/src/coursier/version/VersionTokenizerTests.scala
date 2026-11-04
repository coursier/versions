package coursier.version

import coursier.version.Version._
import coursier.version.Version.Tokenizer.{Dot, Hyphen, Plus, Underscore}
import utest._

object VersionTokenizerTests extends TestSuite {

  private def none = Tokenizer.None

  private def tokens(str: String) = {
    val tokenized = Tokenizer(str)
    (tokenized._1, tokenized._2.toList)
  }

  // Version.items and Tokenizer.apply take different paths, they need to agree
  private def check(str: String)(expected: Item*): Unit = {
    assert(Version.items(str) == expected.toVector)
    val (first, others) = tokens(str)
    assert((first +: others.map(_._2)) == expected.toList)
  }

  val tests = Tests {

    test("separators") {
      assert(tokens("1.a-2_b") == ((
        Number(1),
        List(Dot -> Tag("a"), Hyphen -> Number(2), Underscore -> Tag("b"))
      )))
      assert(tokens("1c2") == ((Number(1), List(none -> Tag("c"), none -> Number(2)))))
    }

    test("empty") {
      check("")(Number(0))
      check(".")(Number(0), Number(0))
      check("1.")(Number(1), Number(0))
      check("1-")(Number(1), Number(0))
      check("1..2")(Number(1), Number(0), Number(2))
      check("-1")(Number(0), Number(1))
    }

    test("numbers") {
      check("00")(Number(0))
      check("999999999")(Number(999999999))
      check("1000000000")(BigNumber(BigInt(1000000000)))
      check("0000000001")(BigNumber(BigInt(1)))
      check("2147483648")(BigNumber(BigInt(2147483648L)))
    }

    test("lowerCase") {
      check("1.0.FINAL")(Number(1), Number(0), Tag("final"))
      check("1-~FoO")(Number(1), Tag("~"), Tag("foo"))
    }

    test("x") {
      check("1.x")(Number(1), Max)
      check("1.X")(Number(1), Max)
      check("1-x")(Number(1), Tag("x"))
      check("1_x")(Number(1), Tag("x"))
      check("1x")(Number(1), Tag("x"))
      check("x")(Tag("x"))
      check("1.xy")(Number(1), Tag("xy"))
    }

    test("star") {
      check("1.*")(Number(1), Max)
      check("1.*.2")(Number(1), Max, Number(2))
      check("1.**")(Number(1), Tag("**"))
      check("1-*")(Number(1), Tag("*"))
      check("1*")(Number(1), Tag("*"))
      check("*")(Tag("*"))
    }

    test("plus") {
      assert(tokens("1.+") == ((Number(1), List(Dot -> Max, Plus -> BuildMetadata("")))))
      check("1.+a")(Number(1), Max, BuildMetadata("a"))
      check("1-+a")(Number(1), Number(0), BuildMetadata("a"))
      check("1_+a")(Number(1), Number(0), BuildMetadata("a"))
      check("+a")(Number(0), BuildMetadata("a"))
    }

    test("minMax") {
      check("1.min")(Number(1), Min)
      check("1-MAX")(Number(1), Max)
      check("max1")(Max, Number(1))
    }

    test("abbreviations") {
      check("1.1a1")(Number(1), Number(1), Tag("alpha"), Number(1))
      check("1.1B1")(Number(1), Number(1), Tag("beta"), Number(1))
      check("1.1m1")(Number(1), Number(1), Tag("milestone"), Number(1))
      check("1.1a")(Number(1), Number(1), Tag("a"))
      check("1.1a-1")(Number(1), Number(1), Tag("a"), Number(1))
      check("1.1a.1")(Number(1), Number(1), Tag("a"), Number(1))
      check("1.1c1")(Number(1), Number(1), Tag("c"), Number(1))
    }

    test("buildMetadata") {
      check("1+")(Number(1), BuildMetadata(""))
      check("1+Foo.BAR-1+x_2")(Number(1), BuildMetadata("Foo.BAR-1+x_2"))
      check("1.0-rc+a+b")(Number(1), Number(0), Tag("rc"), BuildMetadata("a+b"))
    }

    test("nonAscii") {
      check("1.0-éa")(Number(1), Number(0), Tag("éa"))
      check("1.0-ÉA")(Number(1), Number(0), Tag("éa"))
      check("1.0-日本語")(Number(1), Number(0), Tag("日本語"))
      // combining marks aren't letters
      check("1.0-é")(Number(1), Number(0), Tag("e"), Tag("́"))
      test("compare") {
        assert(Version("1.0-éa").compare(Version("1.0-éb")) < 0)
        assert(Version("1.0-ÉA").compareSemantic(Version("1.0-éa")) == 0)
      }
    }
  }
}
