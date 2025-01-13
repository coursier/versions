package coursier.version

import dataclass._

// Adapted from https://github.com/coursier/coursier/blob/f0b10fb1744e5bdf94bf17857dfb3cb19fda2e5b/modules/coursier/shared/src/main/scala/coursier/util/ModuleMatchers.scala

@data class ModuleMatchers(
  exclude: Set[ModuleMatcher],
  include: Set[ModuleMatcher] = Set(),
  @since
  includeByDefault: Boolean = true
) {

  // If modules are included by default:
  // Those matched by anything in exclude are excluded, but for those also matched by something in include.
  // If modules are excluded by default:
  // Those matched by anything in include are included, but for those also matched by something in exclude.

  def matches(organization: String, name: String): Boolean =
    matches(organization, name, Map.empty)

  def matches(organization: String, name: String, attributes: Map[String, String]): Boolean =
    if (includeByDefault)
      !exclude.exists(_.matches(organization, name, attributes)) ||
        include.exists(_.matches(organization, name, attributes))
    else
      include.exists(_.matches(organization, name, attributes)) &&
        !exclude.exists(_.matches(organization, name, attributes))

}

object ModuleMatchers {
  def all: ModuleMatchers =
    ModuleMatchers(Set.empty, Set.empty)
  def only(organization: String, name: String): ModuleMatchers =
    only(organization, name, Map.empty)
  def only(organization: String, name: String, attributes: Map[String, String]): ModuleMatchers =
    ModuleMatchers(Set.empty, Set(ModuleMatcher(organization, name, attributes)), includeByDefault = false)
}
