package coursier.version

import coursier.version.internal.Compatibility._

import scala.annotation.tailrec

/**
 *  Used internally by Resolver.
 *
 *  Same kind of ordering as aether-util/src/main/java/org/eclipse/aether/util/version/GenericVersion.java
 */
case class Version(repr: String) extends Ordered[Version] {
  def asString: String = repr
  private var items0: Vector[Version.Item] = null
  def items: Vector[Version.Item] = {
    // no need to guard against concurrent computations, this is not too expensive to compute
    if (items0 == null)
      items0 = Version.items(repr)
    items0
  }
  /**
   * Total order on versions, consistent with `equals` and `hashCode`.
   *
   * `a.compare(b) == 0` if and only if `a == b`. Versions that are semantically
   * equivalent but spelled differently (`1.0` and `1.0.0`, `1.2` and `1.2+foo`,
   * `1.2+bar` and `1.2+foo`, …) are ordered by their representation, so that
   * sorted collections and hash-based ones agree on which versions are distinct.
   *
   * Use [[compareSemantic]] to compare versions up to that equivalence.
   */
  def compare(other: Version): Int = {
    if (repr == other.repr) 0 // fast path
    else {
      val cmp = Version.listCompare(items, other.items)
      // Break ties so that the order is total: versions that only differ by
      // padding, separators, or build metadata are ordered by representation.
      if (cmp == 0) repr.compareTo(other.repr)
      else cmp
    }
  }

  /**
   * Compares versions up to the equivalence induced by version parsing.
   *
   * Returns `0` for versions that have the same meaning but different
   * representations, like `1.0` and `1.0.0`, or `1.2+foo` and `1.2+bar`
   * (Semver § 10: build metadata doesn't take part in precedence).
   *
   * This is *not* consistent with `equals` - it is the order to use to decide
   * whether a version sits in an interval, or whether two versions can be
   * reconciled. Use [[compare]] for sorting or for sorted collections.
   */
  def compareSemantic(other: Version): Int =
    if (repr == other.repr) 0 // fast path
    else Version.listCompare(items, other.items)
  def isEmpty = items.forall(_.isEmpty)

  def withRepr(repr: String): Version =
    copy(repr = repr)

  lazy val isStable: Boolean =
    !repr.endsWith("SNAPSHOT") &&
    !repr.exists(_.isLetter) &&
    repr
      .split(Array('.', '-'))
      .forall(_.lengthCompare(5) <= 0)

  override lazy val hashCode = repr.hashCode()
}

object Version {

  private val zero0 = Version("")

  def zero: Version = zero0

  sealed abstract class Item extends Ordered[Item] {
    def compare(other: Item): Int =
      (this, other) match {
        case (a: Number, b: Number) => a.value.compare(b.value)
        case (a: BigNumber, b: BigNumber) => a.value.compare(b.value)
        case (a: Number, b: BigNumber) => -b.value.compare(a.value)
        case (a: BigNumber, b: Number) => a.value.compare(b.value)
        case (a: Tag, b: Tag) => a.compareTag(b)
        case _ =>
          val rel0 = compareToEmpty
          val rel1 = other.compareToEmpty

          if (rel0 == rel1) order.compare(other.order)
          else rel0.compare(rel1)
      }

    final def isNumber: Boolean =
      this match {
        case _: Numeric => true
        case _ => false
      }

    def order: Int
    def isEmpty: Boolean = compareToEmpty == 0
    def compareToEmpty: Int = 1
  }

  sealed abstract class Numeric extends Item {
    def repr: String
    def next: Numeric
  }
  case class Number(value: Int) extends Numeric {
    val order = 0
    def next: Number = Number(value + 1)
    def repr: String = value.toString
    override def compareToEmpty = value.compare(0)
    def withValue(value: Int): Number =
      copy(value = value)
  }
  case class BigNumber(value: BigInt) extends Numeric {
    val order = 0
    def next: BigNumber = BigNumber(value + 1)
    def repr: String = value.toString
    override def compareToEmpty = value.compare(0)
    def withValue(value: BigInt): BigNumber =
      copy(value = value)
  }

  /**
   * Tags represent the literal items of a version, like the prerelease tags typically
   * appearing after - for SemVer compatible versions.
   *
   * A tag is either a qualifier, when its value has a special meaning (see [[Tag.level]]),
   * or a plain literal. Qualifiers are ordered by level, some of them before the empty item
   * and some after it, and plain literals go after all of them, ordered lexicographically.
   */
  case class Tag(value: String) extends Item {
    val order = -1
    lazy val level: Int =
      value match {
        case "dev"       => Tag.devLevel
        case "alpha"     => Tag.alphaLevel
        case "beta"      => Tag.betaLevel
        case "milestone" => Tag.milestoneLevel
        case "rc" | "cr" => Tag.rcLevel
        case "snapshot"  => Tag.snapshotLevel
        case ""          => Tag.emptyLevel
        case "ga"        => Tag.gaLevel
        case "final"     => Tag.finalLevel
        case "sp"        => Tag.spLevel
        case _           => Tag.otherLevel
      }

    /** Whether this tag is a qualifier, rather than a plain literal item. */
    def isQualifier: Boolean = level != Tag.otherLevel

    override def compareToEmpty = level.compare(0)
    def isPreRelease: Boolean = level < Tag.emptyLevel
    def compareTag(other: Tag): Int = {
      val levelComp = level.compare(other.level)
      if (levelComp == 0 && level == Tag.otherLevel) value.compareToIgnoreCase(other.value)
      else levelComp
    }
    def withValue(value: String): Tag =
      copy(value = value)
  }

  object Tag {
    // Qualifiers, in order. Those below emptyLevel denote pre-releases, those above it
    // denote releases. Unlike Maven, ga and final are distinct, and distinct from the empty
    // item, so that no two of them compare equal and sorting versions stays deterministic.
    // dev isn't part of the documented ordering, it's kept as an extension, below alpha.
    private[version] val devLevel       = -6
    private[version] val alphaLevel     = -5
    private[version] val betaLevel      = -4
    private[version] val milestoneLevel = -3
    private[version] val rcLevel        = -2
    private[version] val snapshotLevel  = -1
    // An empty tag is the only one equivalent to the empty item.
    private[version] val emptyLevel     = 0
    private[version] val gaLevel        = 1
    private[version] val finalLevel     = 2
    private[version] val spLevel        = 3
    // Plain literal items sort after all the qualifiers, and before non-zero numeric items.
    private[version] val otherLevel     = 4

    // alpha, beta and milestone can be abbreviated to their initial, but only when that
    // initial is directly followed by a digit: 1.1a1 is equivalent to 1.1-alpha-1, while
    // 1.1a and 1.1a-1 have a plain literal a in them.
    private[version] def expandAbbreviation(value: String, followedByDigit: Boolean): String =
      if (followedByDigit)
        value match {
          case "a" => "alpha"
          case "b" => "beta"
          case "m" => "milestone"
          case _   => value
        }
      else value
  }
  case class BuildMetadata(value: String) extends Item {
    val order = 1
    override def compareToEmpty = 0
    def withValue(value: String): BuildMetadata =
      copy(value = value)
  }

  case object Min extends Item {
    val order = -8
    override def compareToEmpty = -1
  }
  case object Max extends Item {
    val order = 8
  }

  val empty = Number(0)

  object Tokenizer {
    sealed abstract class Separator
    case object Dot extends Separator
    case object Hyphen extends Separator
    case object Underscore extends Separator
    case object Plus extends Separator
    case object None extends Separator

    def apply(str: String): (Item, Stream[(Separator, Item)]) = {
      def parseItem(s: Stream[Char], prev: Option[Separator]): (Item, Stream[Char]) = {
        if (s.isEmpty) (empty, s)
        else if (s.head.isDigit) {
          def digits(b: StringBuilder, s: Stream[Char]): (String, Stream[Char]) =
            if (s.isEmpty || !s.head.isDigit) (b.result(), s)
            else digits(b += s.head, s.tail)

          val (digits0, rem) = digits(new StringBuilder, s)
          val item =
            if (digits0.length >= 10) BigNumber(BigInt(digits0))
            else Number(digits0.toInt)

          (item, rem)
        } else if (s.head.letter) {
          def letters(b: StringBuilder, s: Stream[Char]): (String, Stream[Char]) =
            if (s.isEmpty || !s.head.letter)
              (b.result().toLowerCase, s) // not specifying a Locale (error with scala js)
            else
              letters(b += s.head, s.tail)

          val (letters0, rem) = letters(new StringBuilder, s)
          val item = letters0 match {
            case "x" if prev == Some(Dot) => Max
            case "min" => Min
            case "max" => Max
            case _     => Tag(Tag.expandAbbreviation(letters0, rem.headOption.exists(_.isDigit)))
          }
          (item, rem)
        } else {
          val (sep, _) = parseSeparator(s)
          (prev, sep) match {
            case (_, None) =>
              def other(b: StringBuilder, s: Stream[Char]): (String, Stream[Char]) =
                if (s.isEmpty || s.head.isLetterOrDigit || parseSeparator(s)._1 != None)
                  (b.result().toLowerCase, s)  // not specifying a Locale (error with scala js)
                else
                  other(b += s.head, s.tail)

              val (item, rem0) = other(new StringBuilder, s)
              // treat .* as .max
              if (prev == Some(Dot) && item == "*") (Max, rem0)
              else (Tag(item), rem0)
            // treat .+ as .max
            case (Some(Dot), Plus) => (Max, s)
            case _                 => (empty, s)
          }
        }
      }

      def parseSeparator(s: Stream[Char]): (Separator, Stream[Char]) = {
        assert(s.nonEmpty)

        s.head match {
          case '.' => (Dot, s.tail)
          case '-' => (Hyphen, s.tail)
          case '_' => (Underscore, s.tail)
          case '+' => (Plus, s.tail)
          case _ => (None, s)
        }
      }

      def helper(s: Stream[Char]): Stream[(Separator, Item)] = {
        if (s.isEmpty) Stream()
        else {
          val (sep, rem0) = parseSeparator(s)
          sep match {
            case Plus =>
              Stream((sep, BuildMetadata(rem0.mkString)))
            case _ =>
              val (item, rem) = parseItem(rem0, Some(sep))
              (sep, item) #:: helper(rem)
          }
        }
      }

      val (first, rem) = parseItem(str.toStream, scala.None)
      (first, helper(rem))
    }
  }

  def isNumeric(item: Item) = item match { case _: Numeric => true; case _ => false }
  private def isNumericOrMinMax(item: Item): Boolean =
    item match {
      case _: Numeric | Min | Max => true
      case _ => false
    }
  def isBuildMetadata(item: Item) = item match { case _: BuildMetadata => true; case _ => false }

  def items(repr: String): Vector[Item] = {
    val (first, tokens) = Tokenizer(repr)
    first +: tokens.toVector.map(_._2)
  }

  // before comparing two versions pad the number parts to the equal number of digits
  // for example, 1-ga, and 1.0.0 comparison will be adjusted first to 1.0.0-ga and 1.0.0.
  def listCompare(first0: Vector[Item], second0: Vector[Item]): Int = {
    // Semver § 10: two versions that differ only in the build metadata, have the same precedence.
    val first = first0.filterNot(isBuildMetadata)
    val second = second0.filterNot(isBuildMetadata)

    def padNum(xs: Vector[Item], original: Int, next: Int): Vector[Item] = {
      val (before, after) = xs.splitAt(original)
      before ++ Vector.fill(next - original)(empty) ++ after
    }
    val num1 = first.prefixLength(isNumericOrMinMax)
    val num2 = second.prefixLength(isNumericOrMinMax)
    (num1, num2) match {
      case (x, y) if x == y =>
        listCompare0(first, second)
      case (x, y) if x > y  =>
        listCompare0(first, padNum(second, y, x))
      case (x, y) if x < y  =>
        listCompare0(padNum(first, x, y), second)
    }
  }

  @tailrec
  private def listCompare0(first: Vector[Item], second: Vector[Item], idx: Int = 0): Int = {
    val firstDone = idx >= first.length
    val secondDone = idx >= second.length
    if (firstDone && secondDone) 0
    else if (firstDone) {
      var i = idx
      while (i < second.length && second(i).isEmpty) i += 1
      if (i < second.length) -second(i).compareToEmpty else 0
    } else if (secondDone) {
      var i = idx
      while (i < first.length && first(i).isEmpty) i += 1
      if (i < first.length) first(i).compareToEmpty else 0
    } else {
      val rel = first(idx).compare(second(idx))
      if (rel == 0) listCompare0(first, second, idx + 1)
      else rel
    }
  }

}
