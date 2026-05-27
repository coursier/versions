package coursier.version

import java.util.regex.Pattern

import dataclass.data

import scala.annotation.tailrec
import scala.util.matching.Regex

// Adapted from https://github.com/coursier/coursier/blob/876a6604d0cd0c3783ed729f5399549f52a3a385/modules/coursier/shared/src/main/scala/coursier/util/ModuleMatcher.scala

@data class ModuleMatcher(
  organizationMatcher: String,
  nameMatcher: String,
  attributeMatchers: Map[String, String] = Map.empty
) {

  import ModuleMatcher.blobToPattern

  lazy val orgPattern = blobToPattern(organizationMatcher)
  lazy val namePattern = blobToPattern(nameMatcher)
  lazy val attributesPattern = attributeMatchers
    .iterator
    .map {
      case (k, v) =>
        (k, blobToPattern(v))
    }
    .toMap

  def matches(organization: String, name: String): Boolean =
    matches(organization, name, Map.empty)

  def matches(organization: String, name: String, attributes: Map[String, String]): Boolean =
    orgPattern.pattern.matcher(organization).matches() &&
      namePattern.pattern.matcher(name).matches() &&
      attributes.keySet == attributesPattern.keySet &&
      attributesPattern.forall {
        case (k, p) =>
          attributes.get(k).exists(p.pattern.matcher(_).matches())
      }

}

object ModuleMatcher {

  def all: ModuleMatcher =
    ModuleMatcher("*", "*")

  @tailrec
  private def blobToPattern(s: String, b: StringBuilder = new StringBuilder): Regex =
    if (s.isEmpty)
      b.result().r
    else {
      val idx = s.indexOf('*')
      if (idx < 0) {
        b ++= Pattern.quote(s)
        b.result().r
      } else {
        if (idx > 0)
          b ++= Pattern.quote(s.substring(0, idx))
        b ++= ".*"
        blobToPattern(s.substring(idx + 1), b)
      }
    }

}
