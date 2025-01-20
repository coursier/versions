package coursier.version

import dataclass.data

import scala.annotation.tailrec

sealed abstract class VersionConstraint extends Product with Serializable with Ordered[VersionConstraint] {
  def asString: String
  def interval: VersionInterval
  def preferred: Seq[Version]

  def generateString: String =
    VersionConstraint.generateString(interval, preferred)

  private lazy val compareKey = preferred.headOption.orElse(interval.from).getOrElse(Version.zero)
  def compare(other: VersionConstraint): Int =
    compareKey.compare(other.compareKey)

  def isValid: Boolean =
    interval.isValid && preferred.forall { v =>
      interval.contains(v) ||
        interval.to.forall { to =>
          val cmp = v.compare(to)
          cmp < 0 || (cmp == 0 && interval.toIncluded)
        }
    }
}

object VersionConstraint {
  def apply(version: String): VersionConstraint =
    Lazy(version)
  def from(interval: VersionInterval, preferred: Seq[Version]): VersionConstraint =
    VersionConstraint.Eager(
      generateString(interval, preferred),
      interval,
      preferred
    )

  def empty: VersionConstraint =
    empty0

  private def generateString(interval: VersionInterval, preferred: Seq[Version]): String =
    if (interval == VersionInterval.zero && preferred.isEmpty)
      ""
    else if (interval == VersionInterval.zero && preferred.length == 1)
      preferred.head.repr
    else if (preferred.isEmpty)
      interval.repr
    else if (interval == VersionInterval.zero)
      preferred.map(_.repr).mkString(";")
    else
      interval.repr + "&" + preferred.map(_.repr).mkString(";")
      // sys.error("TODO / string representation of interval and preferred versions together")

  def fromVersion(version: Version): VersionConstraint =
    Eager(
      version.repr,
      VersionInterval.zero,
      Seq(version)
    )

  def merge(constraints: VersionConstraint*): Option[VersionConstraint] =
    if (constraints.isEmpty) Some(empty)
    else if (constraints.length == 1) Some(constraints.head).filter(_.isValid)
    else {
      val intervals = constraints.map(_.interval)

      val intervalOpt =
        intervals.foldLeft(Option(VersionInterval.zero)) {
          case (acc, itv) =>
            acc.flatMap(_.merge(itv))
        }

      val constraintOpt = intervalOpt.map { interval =>
        val preferreds = constraints.flatMap(_.preferred).distinct
        val repr =
          if (interval == VersionInterval.zero && preferreds.length == 1)
            preferreds.head.repr
          else if (preferreds.isEmpty)
            interval.repr
          else if (interval == VersionInterval.zero)
            preferreds.map(_.repr).mkString(";")
          else
            interval.repr + "&" + preferreds.map(_.repr).mkString(";")
            // sys.error("TODO / string representation of interval and preferred versions together")
        VersionConstraint.Eager(repr, interval, preferreds)
      }

      constraintOpt.filter(_.isValid)
    }

  // 1. sort constraints in ascending order.
  // 2. from the right, merge them two-by-two with the merge method above
  // 3. return the last successful merge
  def relaxedMerge(constraints: VersionConstraint*): VersionConstraint = {

    @tailrec
    def mergeByTwo(head: VersionConstraint, rest: List[VersionConstraint]): VersionConstraint =
      rest match {
        case next :: xs =>
          merge(head, next) match {
            case Some(success) => mergeByTwo(success, xs)
            case _             => head
          }
        case Nil => head
      }

    val cs = constraints.toList
    cs match {
      case Nil => VersionConstraint.empty
      case h :: Nil => h
      case _ =>
        val sorted = cs.sortBy { c =>
          c.preferred.headOption
            .orElse(c.interval.from)
            .getOrElse(Version.zero)
        }
        val reversed = sorted.reverse
        mergeByTwo(reversed.head, reversed.tail)
    }
  }


  private[version] def fromPreferred(input: String, version: Version): VersionConstraint =
    Eager(input, VersionInterval.zero, Seq(version))
  private[version] def fromInterval(input: String, interval: VersionInterval): VersionConstraint =
    Eager(input, interval, Nil)

  private val empty0 = Eager("", VersionInterval.zero, Nil)

  private[coursier] val parsedValueAsToString: ThreadLocal[Boolean] = new ThreadLocal[Boolean] {
    override protected def initialValue(): Boolean =
      false
  }

  @data class Lazy(asString: String) extends VersionConstraint {
    private var parsed0: VersionConstraint = null
    private def parsed = {
      if (parsed0 == null)
        parsed0 = VersionParse.versionConstraint(asString)
      parsed0
    }
    def interval: VersionInterval = parsed.interval
    def preferred: Seq[Version] = parsed.preferred

    override def toString: String =
      if (parsedValueAsToString.get()) asString
      else
        s"VersionConstraint.Lazy($asString, ${if (parsed0 == null) "[unparsed]" else s"$interval, $preferred"})"
    override def hashCode(): Int =
      (VersionConstraint, asString).hashCode()
    override def equals(obj: Any): Boolean =
      obj.isInstanceOf[VersionConstraint] && {
        val other = obj.asInstanceOf[VersionConstraint]
        asString == other.asString
      }
  }
  @data class Eager(
    asString: String,
    interval: VersionInterval,
    preferred: Seq[Version]
  ) extends VersionConstraint {
    override def toString: String =
      if (parsedValueAsToString.get()) asString
      else
        s"VersionConstraint.Eager($asString, $interval, $preferred)"
    override def hashCode(): Int =
      (VersionConstraint, asString).hashCode()
    override def equals(obj: Any): Boolean =
      obj.isInstanceOf[VersionConstraint] && {
        val other = obj.asInstanceOf[VersionConstraint]
        asString == other.asString
      }
  }
}
