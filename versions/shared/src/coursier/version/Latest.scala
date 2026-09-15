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
      case _                    => mavenMetaVersion(s)
    }

  /**
   * Parses a Maven 2 meta version
   *
   * Maven 2 accepts `RELEASE` and `LATEST` as version of a dependency, standing respectively for
   * the `release` and `latest` fields of the `maven-metadata.xml` file of that module. Those
   * correspond to [[Latest.Release]] and [[Latest.Integration]] here.
   */
  def mavenMetaVersion(s: String): Option[Latest] =
    s match {
      case "RELEASE" => Some(Latest.Release)
      case "LATEST"  => Some(Latest.Integration)
      case _         => None
    }
}
