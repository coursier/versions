package coursier.version

import java.util.regex.Pattern.quote

import coursier.version.internal.Compatibility._

object VersionParse {

  def version(s: String): Option[Version] = {
    val trimmed = s.trim
    if (trimmed.isEmpty || trimmed.exists(c => c != '.' && c != '-' && c != '_' && c != '+' && !c.letterOrDigit)) None
    else Some(Version(trimmed))
  }

  // matches revisions with a '+' appended, e.g. "1.2.+", "1.2+" or "1.2.3-+"
  private val latestSubRevision = "(.*[^.-])([.-]?)[+]".r

  def ivyLatestSubRevisionInterval(s: String): Option[VersionInterval] =
    s match {
      case latestSubRevision(prefix, delim) =>
        for {
          from <- version(prefix)
          if from.items.nonEmpty
          max = (if (delim.isEmpty) "." else delim) + "max"
          to <- version(prefix + max)
          // the contrary would mean something went wrong in the loose substitution above
          if from.items.init == to.items.dropRight(1).init
        } yield VersionInterval(Some(from), Some(to), fromIncluded = true, toIncluded = true)
      case _ =>
        None
    }

  def versionInterval(s: String): Option[VersionInterval] = {

    def parseBounds(fromIncluded: Boolean, toIncluded: Boolean, s: String) = {

      val commaIdx = s.indexOf(',')

      if (commaIdx >= 0) {
        val strFrom = s.take(commaIdx)
        val strTo = s.drop(commaIdx + 1)

        for {
          from <- if (strFrom.isEmpty) Some(None) else version(strFrom).map(Some(_))
          to <- if (strTo.isEmpty) Some(None) else version(strTo).map(Some(_))
        } yield VersionInterval(from.filterNot(_.isEmpty), to.filterNot(_.isEmpty), from.forall(!_.isEmpty) && fromIncluded, toIncluded)
      } else if (s.nonEmpty && fromIncluded && toIncluded)
        for (v <- version(s) if !v.isEmpty)
          yield VersionInterval(Some(v), Some(v), fromIncluded, toIncluded)
      else
        None
    }

    for {
      fromIncluded <- if (s.startsWith("[")) Some(true) else if (s.startsWith("(")) Some(false) else None
      toIncluded <- if (s.endsWith("]")) Some(true) else if (s.endsWith(")")) Some(false) else None
      s0 = s.drop(1).dropRight(1)
      itv <- parseBounds(fromIncluded, toIncluded, s0)
    } yield itv
  }

  private val multiVersionIntervalSplit = ("(?" + regexLookbehind + "[" + quote("])") + "]),(?=[" + quote("([") + "])").r

  def multiVersionInterval(s: String): Option[VersionInterval] = {

    // TODO Use a full-fledged (fastparsed-based) parser for this and versionInterval above

    val openCount = s.count(c => c == '[' || c == '(')
    val closeCount = s.count(c => c == ']' || c == ')')

    if (openCount == closeCount && openCount >= 1)
      versionInterval(multiVersionIntervalSplit.split(s).last)
    else
      None
  }

  def versionConstraint(s: String): VersionConstraint =
    eagerVersionConstraint(s)

  private[version] def eagerVersionConstraint(s: String): VersionConstraint.Eager = {
    def noConstraint = if (s.isEmpty) Some(VersionConstraint.empty0) else None

    noConstraint
      .orElse(Latest(s).map(VersionConstraint.fromLatest(s, _)))
      .orElse(ivyLatestSubRevisionInterval(s).map(VersionConstraint.fromInterval(s, _)))
      .orElse(versionInterval(s).orElse(multiVersionInterval(s)).map(VersionConstraint.fromInterval(s, _)))
      .getOrElse(VersionConstraint.fromPreferred(s, Version(s)))
  }

}
