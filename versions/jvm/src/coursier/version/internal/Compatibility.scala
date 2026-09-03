package coursier.version.internal

object Compatibility {

  implicit class RichChar(private val c: Char) extends AnyVal {
    def letter = c.isLetter
    def letterOrDigit = c.isLetterOrDigit
  }

  // unused, kept for binary compatibility
  def regexLookbehind: String = "<="

}
