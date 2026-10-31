package coursier.version.internal

object Compatibility {

  implicit class RichChar(private val c: Char) extends AnyVal {
    def letter: Boolean = c.isLetter
    def letterOrDigit: Boolean = c.isLetterOrDigit
  }

  // not specifying a Locale (error with scala js), toLowerCase isn't locale dependent there
  def toLowerCase(s: String): String = s.toLowerCase()

  // unused, kept for binary compatibility
  def regexLookbehind: String = ":"

}
