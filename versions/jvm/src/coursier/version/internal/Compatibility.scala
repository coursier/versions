package coursier.version.internal

object Compatibility {

  implicit class RichChar(private val c: Char) extends AnyVal {
    def letter = c.isLetter
    def letterOrDigit = c.isLetterOrDigit
  }

  def regexLookbehind: String = "<="

}
