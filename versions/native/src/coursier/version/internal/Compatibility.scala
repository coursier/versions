package coursier.version.internal

object Compatibility {

  implicit class RichChar(private val c: Char) extends AnyVal {
    def letter = c.isLetter
    def letterOrDigit = c.isLetterOrDigit
  }

  // not specifying a Locale (doesn't link without a java.util.Locale implementation),
  // toLowerCase isn't locale dependent there
  def toLowerCase(s: String): String = s.toLowerCase()

}
