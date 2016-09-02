package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.calc.DeaggExport.CONTRIBUTOR_LIMIT;
import static org.opensha2.eq.model.SourceType.SYSTEM;
import static org.opensha2.internal.TextUtils.NEWLINE;

import org.opensha2.data.Data;
import org.opensha2.eq.model.ClusterSource;
import org.opensha2.eq.model.Rupture;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SourceType;
import org.opensha2.geo.Location;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Skeletal class and implementations used for contribution to hazard in a
 * deaggregation. A contribution may be keyed to a {@code SourceSet}, an
 * individual {@code Source}, or some other representation, e.g. optimized
 * (table-based) {@code GridSource}s or {@code SystemSource} fault sections.
 *
 * @author Peter Powers
 */
abstract class DeaggContributor {

  final double rate;
  final double residual;
  final double rScaled;
  final double mScaled;
  final double εScaled;

  DeaggContributor(
      double rate,
      double residual,
      double rScaled,
      double mScaled,
      double εScaled) {

    this.rate = rate;
    this.residual = residual;
    this.rScaled = rScaled;
    this.mScaled = mScaled;
    this.εScaled = εScaled;
  }

  double total() {
    return rate + residual;
  }

  /* Convenience array for use when combining children. */
  private double[] values() {
    return new double[] { rate, residual, rScaled, mScaled, εScaled };
  }

  abstract StringBuilder appendTo(StringBuilder sb, double percentScalar, String indent);

  abstract static class Builder {

    double rate;
    double residual;
    double rScaled;
    double mScaled;
    double εScaled;

    /* Only for use by inner concrete subtypes. */
    private void add(
        double rate,
        double residual,
        double rScaled,
        double mScaled,
        double εScaled) {

      this.rate += rate;
      this.residual += residual;
      this.rScaled += rScaled;
      this.mScaled += mScaled;
      this.εScaled += εScaled;
    }

    /* Only for use by inner concrete subtypes. */
    private void add(double[] values) {
      this.rate += values[0];
      this.residual += values[1];
      this.rScaled += values[2];
      this.mScaled += values[3];
      this.εScaled += values[4];
    }

    /*
     * Optional, single-call operation. Some implementations may throw an
     * UnsupportedOperationException. May only be called once with subsequent
     * calls to addChild() throwing an IllegalStateException.
     */
    abstract Builder multiply(double scale);

    /*
     * Optional operation; some implementations may throw an
     * UnsupportedOperationException.
     */
    abstract Builder addChild(DeaggContributor.Builder contributor);

    /*
     * Build implementations should compile rate and statistic values for this
     * from any children instead of allowing them to be add()-ed directly.
     */
    abstract DeaggContributor build();

  }

  /*
   * Implementations.
   * 
   * NOTE Once logic-tree branches are better supported, we'll likely require
   * contributors keyed to branches. For example, GR and CH branches are
   * currently separated into separate source sets, but this won't be the case
   * in the future.
   * 
   * TODO There is no verification as yet on build calls that builders have all
   * the necessary data to proceed
   */

  /* SourceSet contributor with references to child contributors. */
  static final class SourceSetContributor extends DeaggContributor {

    final SourceSet<? extends Source> sourceSet;
    final List<DeaggContributor> children;

    private SourceSetContributor(
        SourceSet<? extends Source> sourceSet,
        List<DeaggContributor> children,
        double rate,
        double residual,
        double rScaled,
        double mScaled,
        double εScaled) {

      super(rate, residual, rScaled, mScaled, εScaled);
      this.sourceSet = sourceSet;
      this.children = children;
    }

    @Override
    public String toString() {
      return "SourceSet contributor: " + sourceSet.name();
    }

    @Override
    public StringBuilder appendTo(StringBuilder sb, double toPercent, String indent) {
      double contribution = total() * toPercent;
      if (contribution < CONTRIBUTOR_LIMIT) {
        return sb;
      }
      sb.append(String.format(
          DeaggExport.CONTRIB_SOURCE_SET_FMT,
          sourceSet.name(), sourceSet.type(), contribution));
      sb.append(NEWLINE);
      for (DeaggContributor child : children) {
        child.appendTo(sb, toPercent, "  ");
      }
      return sb;
    }

    static final class Builder extends DeaggContributor.Builder {

      SourceSet<? extends Source> sourceSet;
      ArrayList<DeaggContributor.Builder> children = new ArrayList<>();

      Builder sourceSet(SourceSet<? extends Source> sourceSet) {
        this.sourceSet = sourceSet;
        return this;
      }

      @Override
      Builder addChild(DeaggContributor.Builder contributor) {
        children.add(contributor);
        return this;
      }

      @Override
      Builder multiply(double scale) {
        throw new UnsupportedOperationException();
      }

      @Override
      SourceSetContributor build() {
        List<DeaggContributor> sortedChildren = buildAndSort(children);
        super.add(sumChildValues(sortedChildren));
        return new SourceSetContributor(
            sourceSet,
            sortedChildren,
            rate,
            residual,
            rScaled,
            mScaled,
            εScaled);
      }
    }
  }

  /* 
   * TODO Builders should have better state checking. e.g. once the source
   * has been set for a source contributor, it may not be set again. Likewise,
   * add() can not be called unless source is set.
   */
  
  /* Generic Source contributor. Location and azimuth are site-specific. */
  static class SourceContributor extends DeaggContributor {

    final Source source;
    final Location location;
    final double azimuth;

    private SourceContributor(
        Source source,
        Location location,
        double azimuth,
        double rate,
        double residual,
        double rScaled,
        double mScaled,
        double εScaled) {

      super(rate, residual, rScaled, mScaled, εScaled);
      this.source = source;
      this.location = location;
      this.azimuth = azimuth;
    }

    @Override
    public String toString() {
      return "Source contributor: " + source.name();
    }

    @Override
    StringBuilder appendTo(StringBuilder sb, double toPercent, String indent) {
      double total = total();
      double contribution = total * toPercent;
      if (contribution < CONTRIBUTOR_LIMIT) {
        return sb;
      }
      double rBar = rScaled / total;
      double mBar = mScaled / total;
      double εBar = εScaled / total;
      sb.append(String.format(
          DeaggExport.CONTRIB_SOURCE_FMT,
          indent + source.name(), 
          rBar, mBar, εBar, 
          location.lon(), location.lat(), azimuth, 
          contribution));
      sb.append(NEWLINE);
      return sb;
    }

    static final class Builder extends DeaggContributor.Builder {

      Source source;
      Location location;
      double azimuth;
      boolean scaled = false;

      Builder source(Source source, Location location, double azimuth) {
        this.source = source;
        this.location = location;
        this.azimuth = azimuth;
        return this;
      }

      Builder add(
          double rate,
          double residual,
          double rScaled,
          double mScaled,
          double εScaled) {

        checkState(!scaled);
        super.add(rate, residual, rScaled, mScaled, εScaled);
        return this;
      }

      @Override
      Builder multiply(double scale) {
        checkState(!scaled);
        rate *= scale;
        residual *= scale;
        rScaled *= scale;
        mScaled *= scale;
        εScaled *= scale;
        scaled = true;
        return this;
      }

      @Override
      Builder addChild(DeaggContributor.Builder contributor) {
        throw new UnsupportedOperationException();
      }

      @Override
      SourceContributor build() {
        return new SourceContributor(
            source,
            location,
            azimuth,
            rate,
            residual,
            rScaled,
            mScaled,
            εScaled);
      }
    }
  }

  /* ClusterSource contributor. */
  static final class ClusterContributor extends DeaggContributor {

    final ClusterSource cluster;
    final List<DeaggContributor> faults;

    ClusterContributor(
        ClusterSource cluster,
        List<DeaggContributor> faults,
        double rate,
        double residual,
        double rScaled,
        double mScaled,
        double εScaled) {

      super(rate, residual, rScaled, mScaled, εScaled);
      this.cluster = cluster;
      this.faults = faults;
    }

    @Override
    public String toString() {
      return "Cluster contributor: " + cluster.name();
    }

    @Override
    StringBuilder appendTo(StringBuilder sb, double toPercent, String indent) {
      double total = total();
      double contribution = total() * toPercent;
      if (contribution < CONTRIBUTOR_LIMIT) {
        return sb;
      }
      double rBar = rScaled / total;
      double mBar = mScaled / total;
      double εBar = εScaled / total;
      sb.append(String.format(
          DeaggExport.CONTRIB_SOURCE_FMT,
          indent + cluster.name(), rBar, mBar, εBar, 0.0, 0.0, 0.0, contribution));
      sb.append(NEWLINE);
      for (DeaggContributor fault : faults) {
        fault.appendTo(sb, toPercent, "    ");
      }
      return sb;
    }

    static final class Builder extends DeaggContributor.Builder {

      ClusterSource cluster;
      ArrayList<DeaggContributor.Builder> faults = new ArrayList<>();

      Builder cluster(ClusterSource cluster) {
        this.cluster = cluster;
        return this;
      }

      @Override
      Builder multiply(double scale) {
        for (DeaggContributor.Builder fault : faults) {
          fault.multiply(scale);
        }
        return this;
      }

      @Override
      Builder addChild(DeaggContributor.Builder contributor) {
        faults.add(contributor);
        return this;
      }

      @Override
      ClusterContributor build() {
        List<DeaggContributor> sortedFaults = buildAndSort(faults);
        super.add(sumChildValues(sortedFaults));
        return new ClusterContributor(
            cluster,
            sortedFaults,
            rate,
            residual,
            rScaled,
            mScaled,
            εScaled);
      }
    }
  }

  private static double[] sumChildValues(Collection<DeaggContributor> contributors) {
    double[] sum = new double[5];
    for (DeaggContributor contributor : contributors) {
      Data.add(sum, contributor.values());
    }
    return sum;
  }

  static final class SystemContributor extends DeaggContributor {

    final SectionSource section;

    SystemContributor(
        SectionSource section,
        double rate,
        double residual,
        double rScaled,
        double mScaled,
        double εScaled) {

      super(rate, residual, rScaled, mScaled, εScaled);
      this.section = section;
    }

    @Override
    public String toString() {
      return "System contributor: Section " + section.sectionIndex;
    }

    @Override
    StringBuilder appendTo(StringBuilder sb, double toPercent, String indent) {
      double contribution = total() * toPercent;
      if (contribution < CONTRIBUTOR_LIMIT) {
        return sb;
      }
      sb.append(String.format(
          DeaggExport.CONTRIB_SOURCE_FMT,
          indent + section.name(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, contribution));
      sb.append(NEWLINE);
      return sb;
    }

    static final class Builder extends DeaggContributor.Builder {

      SectionSource section;

      Builder section(SectionSource section) {
        this.section = section;
        return this;
      }

      Builder add(
          double rate,
          double residual,
          double rScaled,
          double mScaled,
          double εScaled) {

        super.add(rate, residual, rScaled, mScaled, εScaled);
        return this;
      }

      @Override
      Builder multiply(double scale) {
        throw new UnsupportedOperationException();
      }

      @Override
      Builder addChild(DeaggContributor.Builder contributor) {
        throw new UnsupportedOperationException();
      }

      @Override
      DeaggContributor build() {
        return new SystemContributor(
            section,
            rate,
            residual,
            rScaled,
            mScaled,
            εScaled);
      }
    }
  }

  /*
   * Custom source that encapsulates the fact that relevant data in the
   * deaggregation of SystemSourceSet is associated with individual fault
   * sections rather than sources/ruptures.
   */
  static final class SectionSource implements Source {

    final int sectionIndex;

    SectionSource(int sectionIndex) {
      this.sectionIndex = sectionIndex;
    }

    @Override
    public String name() {
      return "System Section (" + sectionIndex + ")";
    }

    @Override
    public int size() {
      return 1;
    }

    @Override
    public SourceType type() {
      return SYSTEM;
    }

    @Override
    public Location location(Location site) {
      return null;
      // TODO do nothing

    }

    @Override
    public Iterator<Rupture> iterator() {
      throw new UnsupportedOperationException();
    }
  }

  /* Convert a builder list to immutable sorted contributor list. */
  private static List<DeaggContributor> buildAndSort(
      Collection<DeaggContributor.Builder> builders) {
    return SORTER.immutableSortedCopy(Iterables.transform(builders, BUILDER));
  }

  static final Function<DeaggContributor.Builder, DeaggContributor> BUILDER =
      new Function<DeaggContributor.Builder, DeaggContributor>() {
        @Override
        public DeaggContributor apply(Builder builder) {
          return builder.build();
        }
      };

  static final Ordering<DeaggContributor> SORTER = new Ordering<DeaggContributor>() {
    @Override
    public int compare(DeaggContributor left, DeaggContributor right) {
      return Double.compare(right.total(), left.total());
    }
  };

}
