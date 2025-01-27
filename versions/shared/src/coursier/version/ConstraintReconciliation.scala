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

  /**
   * Keeps the intersection of intervals, retains the latest version, etc. as described in the coursier documentation
   *
   * Fails when passed version intervals that don't overlap.
   */
  case object Default extends ConstraintReconciliation {
    def reconcile(versions: Seq[VersionConstraint]): Option[VersionConstraint] =
      VersionConstraint.merge(versions: _*)
  }

  /**
   * Always succeeds
   *
   * When passed version intervals that don't overlap, the lowest intervals are discarded until the remaining intervals do overlap.
   */
  case object Relaxed extends ConstraintReconciliation {
    def reconcile(versions: Seq[VersionConstraint]): Option[VersionConstraint] =
      Some {
        VersionConstraint.merge(versions: _*)
          .getOrElse(VersionConstraint.relaxedMerge(versions: _*))
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
