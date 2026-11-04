package coursier.version.internal

object Compatibility {

  implicit class RichChar(private val c: Char) extends AnyVal {
    def letter = c.isLetter
    def letterOrDigit = c.isLetterOrDigit
  }

  def toLowerCase(s: String): String = s.toLowerCase(java.util.Locale.ROOT)

  // unused, kept for binary compatibility
  def regexLookbehind: String = "<="

}
