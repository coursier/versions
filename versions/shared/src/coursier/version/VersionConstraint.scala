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

  def withLatest(latestOpt: Option[Latest]): VersionConstraint
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
    if (preferred.isEmpty && latest.isEmpty)
      if (interval == VersionInterval.empty) ""
      else interval.repr
    else {
      val nonIntervalPart = (preferred.iterator.map(_.asString) ++ latest.iterator.map(_.asString)).mkString(";")
      if (interval == VersionInterval.zero) nonIntervalPart
      else s"${interval.repr}&$nonIntervalPart"
    }

  def fromVersion(version: Version): VersionConstraint =
    if (version.repr.isEmpty)
      empty
    else
      Eager(
        version.repr,
        VersionInterval.zero,
        Some(version),
        None
      )

  def merge(constraints: VersionConstraint*): Option[VersionConstraint] =
    if (constraints.isEmpty) Some(empty)
    else if (constraints.lengthCompare(1) == 0) Some(constraints.head).filter(_.isValid)
    else {
      val intervals = constraints.map(_.interval)

      val intervalOpt =
        intervals.foldLeft(Option(VersionInterval.zero)) {
          case (acc, itv) =>
            acc.flatMap(_.merge(itv))
        }

      val constraintOpt = intervalOpt.map { interval =>
        val preferreds = {
          val allPreferred = constraints.flatMap(_.preferred)
          interval.from match {
            case Some(from) =>
              allPreferred.filter { v =>
                val cmp = from.compare(v)
                cmp < 0 || (cmp == 0 && interval.fromIncluded)
              }
            case None =>
              allPreferred
          }
        }
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
  def relaxedMerge(constraints: VersionConstraint*): VersionConstraint =
    constraints.toList.sorted.reverse match {
      case Nil      => VersionConstraint.empty
      case h :: Nil => h
      case h :: t   =>

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

        val latestOpt = {
          val it = constraints.iterator.flatMap(_.latest.iterator)
          if (it.hasNext) Some(it.max) else None
        }

        val merged = mergeByTwo(h, t)

        if (merged.latest == latestOpt) merged
        else merged.withLatest(latestOpt)
    }


  private[version] def fromPreferred(input: String, version: Version): Eager =
    if (input.isEmpty) empty0
    else Eager(input, VersionInterval.zero, Some(version), None)
  private[version] def fromInterval(input: String, interval: VersionInterval): Eager =
    Eager(input, interval, None, None)
  private[version] def fromLatest(input: String, latest: Latest): Eager =
    Eager(input, VersionInterval.zero, None, Some(latest))

  private[version] val empty0 = Eager("", VersionInterval.empty, None, None)

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

    def withLatest(latestOpt: Option[Latest]): VersionConstraint =
      parsed.withLatest(latestOpt)
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
