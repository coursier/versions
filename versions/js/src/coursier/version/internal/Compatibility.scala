package coursier.version.internal

object Compatibility {

  private def between(c: Char, lower: Char, upper: Char) = lower <= c && c <= upper

  implicit class RichChar(private val c: Char) extends AnyVal {
    def letter: Boolean = between(c, 'a', 'z') || between(c, 'A', 'Z')
    def letterOrDigit: Boolean = between(c, '0', '9') || letter
  }

  def regexLookbehind: String = ":"

}
