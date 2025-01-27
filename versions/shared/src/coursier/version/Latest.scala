package coursier.version

sealed abstract class Latest(val name: String, private val order: Int) extends Product with Serializable with Ordered[Latest] {
  final def asString: String = s"latest.$name"

  def compare(that: Latest): Int =
    order.compare(that.order)
}

object Latest {
  case object Integration extends Latest("integration", 2)
  case object Release extends Latest("release", 1)
  case object Stable extends Latest("stable", 0)

  def apply(s: String): Option[Latest] =
    s match {
      case "latest.integration" => Some(Latest.Integration)
      case "latest.release"     => Some(Latest.Release)
      case "latest.stable"      => Some(Latest.Stable)
      case _                    => None
    }
}
