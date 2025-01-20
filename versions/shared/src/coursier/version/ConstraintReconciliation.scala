package coursier.version

/**
  * Reconciles a set of version constraints (version intervals, specific versions, …).
  *
  * To be used mainly during resolution.
  */
sealed abstract class ConstraintReconciliation extends Product with Serializable {
  def reconcile(versions: Seq[VersionConstraint]): Option[VersionConstraint]
}

object ConstraintReconciliation {

  private final val LatestIntegration = VersionConstraint("latest.integration")
  private final val LatestRelease = VersionConstraint("latest.release")
  private final val LatestStable = VersionConstraint("latest.stable")

  private def splitStandard(versions: Seq[VersionConstraint]): (Seq[VersionConstraint], Seq[VersionConstraint]) =
    versions.distinct.partition {
      case LatestIntegration => false
      case LatestRelease     => false
      case LatestStable      => false
      case _                 => true
    }

  private def retainLatestOpt(latests: Seq[VersionConstraint]): Option[VersionConstraint] =
    if (latests.isEmpty) None
    else if (latests.lengthCompare(1) == 0) latests.headOption
    else {
      val set = latests.toSet
      val retained =
        if (set(LatestIntegration)) LatestIntegration
        else if (set(LatestRelease)) LatestRelease
        else {
          // at least two distinct latest.* means we shouldn't even reach this else block anyway
          assert(set(LatestStable))
          LatestStable
        }
      Some(retained)
    }


  /**
   * Keeps the intersection of intervals, retains the latest version, etc. as described in the coursier documentation
   *
   * Fails when passed version intervals that don't overlap.
   */
  case object Default extends ConstraintReconciliation {
    def reconcile(versions: Seq[VersionConstraint]): Option[VersionConstraint] =
      if (versions.isEmpty)
        None
      else if (versions.lengthCompare(1) == 0)
        Some(versions.head)
      else {
        val (standard, latests) = splitStandard(versions)
        val retainedStandard =
          if (standard.isEmpty) None
          else if (standard.lengthCompare(1) == 0) standard.headOption
          else
            VersionConstraint.merge(standard: _*)
              .map(_.uniquePreferred.removeUnusedPreferred)
        val retainedLatestOpt = retainLatestOpt(latests)

        if (standard.isEmpty) retainedLatestOpt
        else if (latests.isEmpty) retainedStandard
        else {
          val parsedIntervals = standard
            .filter(_.preferred.isEmpty) // only keep intervals
            .filter(_.interval != VersionInterval.zero) // not interval matching any version

          if (parsedIntervals.isEmpty)
            retainedLatestOpt
          else
            VersionConstraint.merge(parsedIntervals: _*)
              .map(_.uniquePreferred.removeUnusedPreferred) // FIXME Add retainedLatestOpt too
        }
      }
  }

  /**
   * Always succeeds
   *
   * When passed version intervals that don't overlap, the lowest intervals are discarded until the remaining intervals do overlap.
   */
  case object Relaxed extends ConstraintReconciliation {
    def reconcile(versions: Seq[VersionConstraint]): Option[VersionConstraint] =
      if (versions.isEmpty)
        None
      else if (versions.lengthCompare(1) == 0)
        Some(versions.head)
      else {
        val (standard, latests) = splitStandard(versions)
        val retainedStandard =
          if (standard.isEmpty) None
          else if (standard.lengthCompare(1) == 0) standard.headOption
          else {
            val repr = VersionConstraint.merge(standard: _*)
              .getOrElse(VersionConstraint.relaxedMerge(standard: _*))
              .uniquePreferred
              .removeUnusedPreferred
            Some(repr)
          }
        val retainedLatestOpt = retainLatestOpt(latests)
        if (latests.isEmpty) retainedStandard
        else retainedLatestOpt
      }
  }

  /**
   * The [[ConstraintReconciliation]] to be used for this [[VersionCompatibility]]
   *
   * The `Always` version compatibility corresponds to `Relaxed` constraint reconciliation (never fail to reconcile
   * versions during resolution).
   *
   * The other version compatibilities use `Default` as constraint reconciliation (may fail to reconcile versions during
   * resolution).
   */
  def apply(compatibility: VersionCompatibility): ConstraintReconciliation =
    compatibility match {
      case VersionCompatibility.Always => Relaxed
      case _ => Default
    }

  /** Strict version reconciliation.
    *
    * This particular instance behaves the same as [[Default]] when used by
    * [[coursier.core.Resolution]]. Actual strict conflict manager is handled by
    * `coursier.params.rule.Strict`, which is set up by `coursier.Resolve` when a strict
    * reconciliation is added to it.
    */
  case object Strict extends ConstraintReconciliation {
    def reconcile(versions: Seq[VersionConstraint]): Option[VersionConstraint] =
      Default.reconcile(versions)
  }

  /** Semantic versioning version reconciliation.
    *
    * This particular instance behaves the same as [[Default]] when used by
    * [[coursier.core.Resolution]]. Actual semantic versioning checks are handled by
    * `coursier.params.rule.Strict` with field `semVer = true`, which is set up by
    * `coursier.Resolve` when a SemVer reconciliation is added to it.
    */
  case object SemVer extends ConstraintReconciliation {
    def reconcile(versions: Seq[VersionConstraint]): Option[VersionConstraint] =
      Default.reconcile(versions)
  }

  def apply(input: String): Option[ConstraintReconciliation] =
    input match {
      case "default" => Some(Default)
      case "relaxed" => Some(Relaxed)
      case "strict"  => Some(Strict)
      case "semver"  => Some(SemVer)
      case _         => None
    }
}
