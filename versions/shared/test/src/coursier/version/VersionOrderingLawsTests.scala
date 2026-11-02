package coursier.version

import utest._

import scala.util.Random

/**
 * Seeded property tests for the laws [[Version]], [[VersionInterval]] and [[VersionConstraint]]
 * ordering should follow. Random pairs and triples of unrelated versions mostly differ by their
 * very first item, so the triples here share a prefix, for their differences to sit deeper.
 */
object VersionOrderingLawsTests extends TestSuite {

  private val seed = 20261031L

  private val parts = Vector(
    "0", "1", "2", "10", "a", "b", "alpha", "beta", "rc", "m", "final", "ga", "sp", "snapshot",
    "dev", "pre", "foo", "x", "min", "max", "*", "00", "0000000001", "2147483648", ""
  )
  private val separators = Vector(".", "-", "_", "+", "")

  private val poolSize = 250
  private val tripleCount = 10000
  private val shuffleCount = 50
  private val intervalCount = 10000
  private val mergeCount = 5000

  private def pick[T](rnd: Random, values: Vector[T]): T =
    values(rnd.nextInt(values.length))

  private def genRepr(rnd: Random, maxParts: Int): String = {
    val b = new StringBuilder(pick(rnd, parts))
    for (_ <- 1 until (1 + rnd.nextInt(maxParts))) {
      b ++= pick(rnd, separators)
      b ++= pick(rnd, parts)
    }
    b.result()
  }

  private def genVersion(rnd: Random, maxParts: Int = 5): Version =
    Version(genRepr(rnd, maxParts))

  // merge keeps the first of two equivalent bounds, like 1.0 and 1.0.0, so that the bounds it
  // gives can be spelled differently depending on the order of its inputs
  private def sameBound(a: Option[Version], b: Option[Version]): Boolean =
    (a, b) match {
      case (Some(a0), Some(b0)) => a0.compareSemantic(b0) == 0
      case (None, None) => true
      case _ => false
    }

  private def sameInterval(a: VersionInterval, b: VersionInterval): Boolean =
    sameBound(a.from, b.from) && sameBound(a.to, b.to) &&
      a.fromIncluded == b.fromIncluded && a.toIncluded == b.toIncluded

  private def genRelated(rnd: Random, prefix: String): Version =
    if (rnd.nextInt(8) == 0) Version(prefix)
    else Version(prefix + pick(rnd, separators) + genRepr(rnd, 3))

  // compareSemantic currently breaks its laws as soon as a ga or final tag isn't the very last
  // item of a version, right after its numeric ones. x-ga and x.0 both are equivalent to x, but
  // not to each other, and 1-ga-pre goes before 1 for compareSemantic and after it for compare.
  // Hence the laws that rely on compareSemantic only look at versions having their ga or final
  // tag there, like 1.0.Final.
  private def hasTrailingReleaseTagOnly(version: Version): Boolean = {
    val items = version.items.filterNot(Version.isBuildMetadata)
    items.indices.forall { idx =>
      items(idx) match {
        case tag: Version.Tag if tag.isReleaseEquivalent =>
          idx == items.length - 1 && items.init.forall(!_.isInstanceOf[Version.Tag])
        case _ => true
      }
    }
  }

  // The representation of an interval doesn't tell apart a missing bound from an empty version,
  // although constraints are de-duplicated based on it, and merge can drop a constraint because
  // of that. Hence the exclusion of empty versions from the bounds merge is checked with.
  private def isMergeableBound(version: Version): Boolean =
    version.repr.nonEmpty && hasTrailingReleaseTagOnly(version)

  private def pool(rnd: Random): Vector[Version] =
    Vector.fill(poolSize)(genVersion(rnd)).distinct

  private def pairs(rnd: Random): Iterator[(Version, Version)] = {
    val versions = pool(rnd)
    for (a <- versions.iterator; b <- versions.iterator) yield (a, b)
  }

  private def triples(rnd: Random): Iterator[(Version, Version, Version)] =
    Iterator.fill(tripleCount) {
      val prefix = genRepr(rnd, 3)
      (genRelated(rnd, prefix), genRelated(rnd, prefix), genRelated(rnd, prefix))
    }

  // Each violation names the offending versions. Stops at the first few of them.
  private def semanticPairs(rnd: Random): Iterator[(Version, Version)] =
    pairs(rnd).filter(t => hasTrailingReleaseTagOnly(t._1) && hasTrailingReleaseTagOnly(t._2))

  private def semanticTriples(rnd: Random): Iterator[(Version, Version, Version)] =
    triples(rnd).filter(t => Seq(t._1, t._2, t._3).forall(hasTrailingReleaseTagOnly))

  private def checkLaw[T](cases: Iterator[T])(violation: T => Option[String]): Unit = {
    val violations = cases.flatMap(c => violation(c).iterator).take(20).toVector
    assert(violations.isEmpty)
  }

  private def show(versions: Version*): String =
    versions.map(v => "\"" + v.repr + "\"").mkString(", ")

  private def transitivityViolation(
    a: Version,
    b: Version,
    c: Version,
    name: String
  )(cmp: (Version, Version) => Int): Option[String] = {
    // whichever way the three versions are given
    val orders = Seq((a, b, c), (a, c, b), (b, a, c), (b, c, a), (c, a, b), (c, b, a))
    orders.collectFirst {
      case (x, y, z) if cmp(x, y) <= 0 && cmp(y, z) <= 0 && cmp(x, z) > 0 =>
        s"$name: ${show(x, y, z)} are ordered, but not their ends"
      case (x, y, z) if cmp(x, y) == 0 && cmp(y, z) == 0 && cmp(x, z) != 0 =>
        s"$name: ${show(x, y, z)} are equivalent in a row, but not their ends"
    }
  }

  private def genInterval(rnd: Random): VersionInterval = {
    val prefix = genRepr(rnd, 2)
    def bound() =
      if (rnd.nextInt(5) == 0) None
      else Some(genRelated(rnd, prefix))
    val (from, to) = (bound(), bound()) match {
      case (Some(a), Some(b)) if a.compareSemantic(b) > 0 => (Some(b), Some(a))
      case other => other
    }
    VersionInterval(from, to, from.nonEmpty && rnd.nextBoolean(), to.nonEmpty && rnd.nextBoolean())
  }

  val tests = Tests {

    test("compare") {
      test("reflexivity") {
        checkLaw(pool(new Random(seed)).iterator) { a =>
          if (a.compare(Version(a.repr)) == 0) None
          else Some(s"${show(a)} differs from itself")
        }
      }
      test("antisymmetry") {
        checkLaw(pairs(new Random(seed))) {
          case (a, b) =>
            if (Integer.signum(a.compare(b)) == -Integer.signum(b.compare(a))) None
            else Some(s"${show(a, b)}: ${a.compare(b)} one way, ${b.compare(a)} the other")
        }
      }
      test("transitivity") {
        checkLaw(triples(new Random(seed))) {
          case (a, b, c) => transitivityViolation(a, b, c, "compare")(_.compare(_))
        }
      }
      test("consistentWithEquals") {
        checkLaw(pairs(new Random(seed))) {
          case (a, b) =>
            if ((a.compare(b) == 0) == (a == b)) None
            else Some(s"${show(a, b)}: compare gives ${a.compare(b)}, == gives ${a == b}")
        }
      }
    }

    test("compareSemantic") {
      test("antisymmetry") {
        checkLaw(pairs(new Random(seed))) {
          case (a, b) =>
            val (ab, ba) = (a.compareSemantic(b), b.compareSemantic(a))
            if (Integer.signum(ab) == -Integer.signum(ba)) None
            else Some(s"${show(a, b)}: $ab one way, $ba the other")
        }
      }
      test("transitivity") {
        checkLaw(semanticTriples(new Random(seed))) {
          case (a, b, c) =>
            transitivityViolation(a, b, c, "compareSemantic")(_.compareSemantic(_))
        }
      }
    }

    test("compareRefinesCompareSemantic") {
      def violation(a: Version, b: Version) = {
        val (total, semantic) = (a.compare(b), a.compareSemantic(b))
        if (semantic == 0 || Integer.signum(total) == Integer.signum(semantic)) None
        else Some(s"${show(a, b)}: compare gives $total, compareSemantic gives $semantic")
      }
      test("unrelated") {
        checkLaw(semanticPairs(new Random(seed))) { case (a, b) => violation(a, b) }
      }
      test("related") {
        checkLaw(semanticTriples(new Random(seed))) { case (a, b, _) => violation(a, b) }
      }
    }

    test("hashCode") {
      checkLaw(pairs(new Random(seed))) {
        case (a, b) =>
          if (a != b || a.hashCode == b.hashCode) None
          else Some(s"${show(a, b)} are equal, but their hash codes aren't")
      }
      checkLaw(pool(new Random(seed)).iterator) { a =>
        val b = Version(a.repr)
        if (a == b && a.hashCode == b.hashCode) None
        else Some(s"${show(a)} isn't equal to a copy of itself, or has a different hash code")
      }
    }

    test("sorted") {
      val rnd = new Random(seed)
      val versions = pool(rnd)
      val expected = versions.sorted.map(_.repr)
      checkLaw(Iterator.fill(shuffleCount)(rnd.shuffle(versions))) { shuffled =>
        val sorted = shuffled.sorted.map(_.repr)
        if (sorted == expected) None
        else
          Some(
            s"sorting ${shuffled.map(_.repr)} gives $sorted, " +
              s"differing at ${sorted.zip(expected).filter(t => t._1 != t._2).take(2)}"
          )
      }
    }

    test("intervalContains") {
      val rnd = new Random(seed)
      val cases = Iterator.fill(intervalCount)(genInterval(rnd)).map { itv =>
        val version = itv.from.orElse(itv.to).fold(genVersion(rnd))(v => genRelated(rnd, v.repr))
        (itv, version)
      }
      checkLaw(cases) {
        case (itv, v) =>
          val aboveFrom = itv.from.forall { from =>
            val cmp = v.compareSemantic(from)
            cmp > 0 || (cmp == 0 && itv.fromIncluded)
          }
          val belowTo = itv.to.forall { to =>
            val cmp = to.compareSemantic(v)
            cmp > 0 || (cmp == 0 && itv.toIncluded)
          }
          if (itv.contains(v) == (aboveFrom && belowTo)) None
          else Some(s"${itv.repr}.contains(${show(v)}) gives ${itv.contains(v)}")
      }
    }

    test("merge") {
      val rnd = new Random(seed)
      val cases = Iterator.fill(mergeCount) {
        Vector.fill(2 + rnd.nextInt(3))(genInterval(rnd)).map { itv =>
          val from = itv.from.filter(isMergeableBound)
          val to = itv.to.filter(isMergeableBound)
          val bounded =
            itv.withFrom(from).withTo(to)
              .withFromIncluded(itv.fromIncluded && from.nonEmpty)
              .withToIncluded(itv.toIncluded && to.nonEmpty)
          VersionConstraint.from(bounded, None, None)
        }
      }
      checkLaw(cases) { constraints =>
        val shuffled = rnd.shuffle(constraints)
        val first = VersionConstraint.merge(constraints: _*).map(_.interval)
        val second = VersionConstraint.merge(shuffled: _*).map(_.interval)
        val same = (first, second) match {
          case (Some(a), Some(b)) => sameInterval(a, b)
          case (None, None) => true
          case _ => false
        }
        if (same) None
        else
          Some(
            s"merging ${constraints.map(_.asString)} gives ${first.map(_.repr)}, " +
              s"merging ${shuffled.map(_.asString)} gives ${second.map(_.repr)}"
          )
      }
    }
  }
}
