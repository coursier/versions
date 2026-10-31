package coursier.version

import scala.annotation.nowarn

/**
 * Represents a reconciliation strategy given a dependency conflict.
 */
sealed abstract class VersionCompatibility {
  def isCompatible(constraint: String, version: String): Boolean

  @nowarn
  final def name: String =
    this match {
      case VersionCompatibility.Always => "always compatible"
      case VersionCompatibility.Strict => "strict"
      case VersionCompatibility.SemVerSpec => "strict semantic versioning"
      case VersionCompatibility.EarlySemVer | VersionCompatibility.SemVer =>
        "early semantic versioning"
      case VersionCompatibility.Default | VersionCompatibility.PackVer =>
        "package versioning policy"
    }

  def minimumCompatibleVersion(version: String): String
}

object VersionCompatibility {

  /**
    * Whether `version` carries a pre-release qualifier.
    *
    * Two kinds of items count as pre-releases:
    *   - pre-release qualifiers, wherever they appear (`1.2.3-RC1`, `2.13.0-M3`, `1.2.3.RC1`),
    *   - plain literals right after a `-`, which is where semantic versioning puts
    *     pre-releases (`1.0.0-preview`).
    *
    * Release qualifiers and plain literals in the fourth, dot-separated segment of a
    * Maven-style version are deliberately *not* pre-releases: `3.10.1.Final` and
    * `9.4.25.v20191220` are releases.
    */
  private def hasPreReleaseQualifier(version: Version): Boolean = {
    val (first, rest) = Version.Tokenizer(version.repr)
    def preRelease(item: Version.Item, afterHyphen: Boolean): Boolean =
      item match {
        case t: Version.Tag =>
          if (t.isQualifier) t.isPreRelease else afterHyphen
        case _ => false
      }
    preRelease(first, afterHyphen = false) ||
    rest.exists {
      case (sep, item) => preRelease(item, sep == Version.Tokenizer.Hyphen)
    }
  }

  /**
    * The items of the `length` first segments of `version` that matter when comparing it.
    *
    * Build metadata and the tags that stand for the absence of a qualifier end the significant
    * part early, like the end of the version does: `1.Final`, `1+foo` and `1` all have the same
    * significant part, whatever `length` is.
    */
  private def significantItems(version: Version, length: Int): Vector[Version.Item] = {
    val items = version.items.take(length)
    items.take(1) ++ items.drop(1).takeWhile {
      case _: Version.BuildMetadata => false
      case t: Version.Tag => !t.isReleaseEquivalent
      case _ => true
    }
  }

  /**
    * Whether the significant parts of two versions are the same, up to padding (a missing numeric
    * item counts as 0) and to the representation of their items.
    */
  private def sameSignificantPart(first: Version, second: Version, length: Int): Boolean =
    Version.listCompare(significantItems(first, length), significantItems(second, length)) == 0

  /**
    * Whether `version` can take part in a semantic versioning compatibility check as a constraint.
    *
    * Its significant part (the major number, or major and minor for 0.x versions) must be numeric,
    * and can be shorter than `significantPartLength` (`0` stands for `0.0`). The rest must be made
    * of numbers, qualifiers and build metadata, and it must not be a pre-release - per semantic
    * versioning, a constraint like `1.2.3-RC1` only accepts itself.
    */
  private def isSemVerComparable(version: Version, significantPartLength: Int): Boolean = {
    val significant = significantItems(version, significantPartLength)
    significant.forall(_.isNumber) &&
    version.items.drop(significant.length).forall {
      case _: Version.Numeric => true
      case _: Version.BuildMetadata => true
      case _: Version.Tag => true
      case _ => false // Min / Max
    } &&
    !hasPreReleaseQualifier(version)
  }

  case object Default extends VersionCompatibility {
    def isCompatible(constraint: String, version: String): Boolean =
      PackVer.isCompatible(constraint, version)
    def minimumCompatibleVersion(version: String): String =
      PackVer.minimumCompatibleVersion(version)
  }

  case object Always extends VersionCompatibility {
    def isCompatible(constraint: String, version: String): Boolean =
      true
    def minimumCompatibleVersion(version: String): String =
      "0"
  }

  /**
    * Strict version reconciliation.
    *
    * This particular instance behaves the same as [[Default]] when used by
    * [[coursier.core.Resolution]]. Actual strict conflict manager is handled
    * by `coursier.params.rule.Strict`, which is set up by `coursier.Resolve`
    * when a strict reconciliation is added to it.
    */
  case object Strict extends VersionCompatibility {
    def isCompatible(constraint: String, version: String): Boolean =
      constraint == version || {
        val c = VersionParse.versionConstraint(constraint)
        val v = Version(version)
        if (c.interval == VersionInterval.zero)
          c.preferred.contains(v)
        else
          c.interval.contains(v)
      }
    def minimumCompatibleVersion(version: String): String =
      version
  }

  /**
    * Early Semantic Versioning version reconciliation.
    */
  @deprecated("Use EarlySemVer or SemVerSpec instead. This will be removed in the future version.", "0.3.0")
  case object SemVer extends VersionCompatibility {
    def isCompatible(constraint: String, version: String): Boolean =
      EarlySemVer.isCompatible(constraint, version)
    def minimumCompatibleVersion(version: String): String =
      EarlySemVer.minimumCompatibleVersion(version)
  }

  /**
    * Early Semantic Versioning version reconciliation.
    */
  case object EarlySemVer extends VersionCompatibility {
    private def significativePartLength(v: Version): Int =
      if (v.items.headOption.exists(_.isEmpty)) 2 else 1
    def isCompatible(constraint: String, version: String): Boolean =
      constraint == version || {
        val c = VersionParse.versionConstraint(constraint)
        val v = Version(version)
        if (c.interval == VersionInterval.zero)
          c.preferred.exists { wanted =>
            val toCompare = significativePartLength(v)
            isSemVerComparable(wanted, toCompare) &&
            sameSignificantPart(wanted, v, toCompare) &&
            wanted.compareSemantic(v) <= 0
          }
        else
          c.interval.contains(v)
      }
    def minimumCompatibleVersion(version: String): String = {
      val v = Version(version)
      val toCompare = significativePartLength(v)
      val candidateOpt = Some(v.items.take(toCompare))
        .filter(_.forall(_.isNumber))
        .map(_.collect { case n: Version.Numeric => n })
        .map(items => items.map(_.repr).mkString("."))
        .filter(s => Version(s).compareSemantic(v) <= 0)
      candidateOpt.getOrElse(version)
    }
  }

  /**
    * Semantic versioning version reconciliation, closer to the semantic versioning spec.
    *
    * Unlike `SemVer`, assumes 0.x versions are not compatible with each other.
    */
  case object SemVerSpec extends VersionCompatibility {
    def isCompatible(constraint: String, version: String): Boolean =
      constraint == version || {
        val c = VersionParse.versionConstraint(constraint)
        val v = Version(version)
        if (c.interval == VersionInterval.zero)
          c.preferred.exists { wanted =>
            isSemVerComparable(wanted, 1) &&
            sameSignificantPart(wanted, v, 1) &&
            v.items.take(1).exists(!_.isEmpty) &&
            wanted.compareSemantic(v) <= 0
          }
        else
          c.interval.contains(v)
      }
    def minimumCompatibleVersion(version: String): String = {
      val v = Version(version)
      val candidateOpt = Some(v.items.take(1))
        .filter(items => items.nonEmpty && items.forall(_.isNumber) && items.forall(!_.isEmpty))
        .map(_.collect { case n: Version.Numeric => n })
        .map(items => items.map(_.repr).mkString("."))
        .filter(s => Version(s).compareSemantic(v) <= 0)
      candidateOpt.getOrElse(version)
    }
  }

  case object PackVer extends VersionCompatibility {
    def isCompatible(constraint: String, version: String): Boolean =
      constraint == version || {
        val c = VersionParse.versionConstraint(constraint)
        val v = Version(version)
        if (c.interval == VersionInterval.zero)
          c.preferred.exists(sameSignificantPart(_, v, 2))
        else
          c.interval.contains(v)
      }
    def minimumCompatibleVersion(version: String): String = {
      val v = Version(version)
      val candidateOpt = Some(v.items.take(2))
        .filter(_.forall(_.isNumber))
        .map(_.collect { case n: Version.Numeric => n })
        .map(items => items.map(_.repr).mkString("."))
        .filter(s => Version(s).compareSemantic(v) <= 0)
      candidateOpt.getOrElse(version)
    }
  }

  def apply(input: String): Option[VersionCompatibility] =
    input match {
      case "default" => Some(Default)
      case "always" => Some(Always)
      case "strict" => Some(Strict)
      case "early-semver" => Some(EarlySemVer)
      case "semver-spec" => Some(SemVerSpec)
      case "pvp" => Some(PackVer)
      case "semver" => sys.error(s"""'semver' is ambiguous.
                                    |Based on the Semantic Versioning 2.0.0, 0.y.z updates are all initial development and thus
                                    |0.6.0 and 0.6.1 would NOT maintain any compatibility, but in Scala ecosystem it is
                                    |common to start adopting binary compatibility even in 0.y.z releases.
                                    |
                                    |Specify 'early-semver' for the early variant.
                                    |Specify 'semver-spec' for the spec-correct SemVer.""".stripMargin)
      case _ => None
    }
}
