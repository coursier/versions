package coursier.version

import dataclass.data

import scala.annotation.tailrec

sealed abstract class VersionConstraint extends Product with Serializable with Ordered[VersionConstraint] {
  def asString: String
  def interval: VersionInterval

  /**
   * Preferred version, if any
   */
  def preferred: Option[Version]

  def latest: Option[Latest]

  def generateString: String =
    VersionConstraint.generateString(interval, preferred, latest)

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
  def from(interval: VersionInterval, preferred: Option[Version], latest: Option[Latest]): VersionConstraint =
    Eager(
      generateString(interval, preferred, latest),
      interval,
      preferred,
      latest
    )

  def empty: VersionConstraint =
    empty0

  private def generateString(interval: VersionInterval, preferred: Option[Version], latest: Option[Latest]): String =
    // FIXME Might be buggy with the addition of latest
    if (interval == VersionInterval.zero && preferred.isEmpty && latest.isEmpty)
      ""
    else if (interval == VersionInterval.zero && preferred.nonEmpty && latest.isEmpty)
      preferred.get.asString
    else if (interval == VersionInterval.zero && preferred.isEmpty && latest.nonEmpty)
      latest.get.asString
    else if (preferred.isEmpty && latest.isEmpty)
      interval.repr
    else if (interval == VersionInterval.zero && preferred.nonEmpty && latest.isEmpty)
      preferred.get.asString
    else if (interval == VersionInterval.zero && preferred.isEmpty && latest.nonEmpty)
      latest.get.asString
    else
      interval.repr + "&" + (preferred.get.asString +: latest.toSeq.map(_.asString)).mkString(";")
      // sys.error("TODO / string representation of interval and preferred versions together")

  def fromVersion(version: Version): VersionConstraint =
    Eager(
      version.repr,
      VersionInterval.zero,
      Some(version),
      None
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
        val preferreds = constraints.flatMap(_.preferred)
        val latests = constraints.flatMap(_.latest)
        from(
          interval,
          Some(preferreds).filter(_.nonEmpty).map(_.max),
          Some(latests).filter(_.nonEmpty).map(_.max)
        )
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


  private[version] def fromPreferred(input: String, version: Version): Eager =
    Eager(input, VersionInterval.zero, Some(version), None)
  private[version] def fromInterval(input: String, interval: VersionInterval): Eager =
    Eager(input, interval, None, None)
  private[version] def fromLatest(input: String, latest: Latest): Eager =
    Eager(input, VersionInterval.zero, None, Some(latest))

  private[version] val empty0 = Eager("", VersionInterval.zero, None, None)

  private[coursier] val parsedValueAsToString: ThreadLocal[Boolean] = new ThreadLocal[Boolean] {
    override protected def initialValue(): Boolean =
      false
  }

  @data class Lazy(asString: String) extends VersionConstraint {
    private var parsed0: Eager = null
    private def parsed = {
      if (parsed0 == null)
        parsed0 = VersionParse.eagerVersionConstraint(asString)
      parsed0
    }
    def interval: VersionInterval = parsed.interval
    def preferred: Option[Version] = parsed.preferred
    def latest: Option[Latest] = parsed.latest

    override def toString: String =
      if (parsedValueAsToString.get()) asString
      else
        s"VersionConstraint.Lazy($asString, ${if (parsed0 == null) "[unparsed]" else s"$interval, $preferred, $latest"})"
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
    preferred: Option[Version],
    latest: Option[Latest]
  ) extends VersionConstraint {
    override def toString: String =
      if (parsedValueAsToString.get()) asString
      else
        s"VersionConstraint.Eager($asString, $interval, $preferred, $latest)"
    override def hashCode(): Int =
      (VersionConstraint, asString).hashCode()
    override def equals(obj: Any): Boolean =
      obj.isInstanceOf[VersionConstraint] && {
        val other = obj.asInstanceOf[VersionConstraint]
        asString == other.asString
      }
  }
}
