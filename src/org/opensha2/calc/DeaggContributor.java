package org.opensha2.calc;

import static org.opensha2.calc.DeaggExport.CONTRIBUTOR_LIMIT;
import static org.opensha2.internal.TextUtils.NEWLINE;

import org.opensha2.eq.model.ClusterSource;
import org.opensha2.eq.model.Rupture;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;

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

  DeaggContributor(double rate, double residual) {
    this.rate = rate;
    this.residual = residual;
  }

  double total() {
    return rate + residual;
  }

  abstract StringBuilder appendTo(StringBuilder sb, double percentScalar, String indent);

  abstract static class Builder {

    double rate;
    double residual;

    Builder add(double rate, double residual) {
      this.rate += rate;
      this.residual += residual;
      return this;
    }

    /*
     * Optional operation; some implementations may throw an
     * UnsupportedOperationException.
     */
    Builder multiply(double scale) {
      rate *= scale;
      residual *= scale;
      return this;
    }

    /*
     * Optional operation; some implementations may throw an
     * UnsupportedOperationException.
     */
    abstract Builder addChild(DeaggContributor.Builder contributor);

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
    final List<DeaggContributor> children; // TODO sorted

    private SourceSetContributor(
        SourceSet<? extends Source> sourceSet,
        double rate,
        double residual,
        List<DeaggContributor> children) {

      super(rate, residual);
      this.sourceSet = sourceSet;
      this.children = children;
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
        return new SourceSetContributor(
            sourceSet,
            rate,
            residual,
            buildAndSort(children));
      }
    }
  }

  /* Generic Source contributor */
  static class SourceContributor extends DeaggContributor {

    final Source source;
    final double rScaled;
    final double mScaled;
    final double εScaled;

    private SourceContributor(
        Source source,
        double rate,
        double residual,
        double rScaled,
        double mScaled,
        double εScaled) {

      super(rate, residual);
      this.source = source;
      this.rScaled = rScaled;
      this.mScaled = mScaled;
      this.εScaled = εScaled;
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
          indent + source.name(), rBar, mBar, εBar, 0.0, 0.0, 0.0, contribution));
      sb.append(NEWLINE);
      return sb;
    }

    static final class Builder extends DeaggContributor.Builder {

      Source source;
      double rScaled;
      double mScaled;
      double εScaled;

      Builder source(Source source) {
        this.source = source;
        return this;
      }

      Builder add(double rate, double residual, double rScaled, double mScaled, double εScaled) {
        super.add(rate, residual);
        this.rScaled += rScaled;
        this.mScaled += mScaled;
        this.εScaled += εScaled;
        return this;
      }

      @Override
      Builder addChild(DeaggContributor.Builder contributor) {
        throw new UnsupportedOperationException();
      }

      @Override
      SourceContributor build() {
        return new SourceContributor(source, rate, residual, rScaled, mScaled, εScaled);
      }
    }
  }

  /* ClusterSource contributor. */
  static final class ClusterContributor extends DeaggContributor {

    final ClusterSource cluster;
    final List<DeaggContributor> faults;

    ClusterContributor(
        ClusterSource cluster,
        double rate,
        double residual,
        List<DeaggContributor> faults) {

      super(rate, residual);
      this.cluster = cluster;
      this.faults = faults;
    }

    @Override
    StringBuilder appendTo(StringBuilder sb, double toPercent, String indent) {
      double contribution = total() * toPercent;
      if (contribution < CONTRIBUTOR_LIMIT) {
        return sb;
      }
      sb.append(String.format(
          DeaggExport.CONTRIB_SOURCE_FMT,
          indent + cluster.name(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, contribution));
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
      Builder addChild(DeaggContributor.Builder contributor) {
        faults.add(contributor);
        return this;
      }

      @Override
      Builder multiply(double scale) {
        super.multiply(scale);
        for (DeaggContributor.Builder fault : faults) {
          fault.multiply(scale);
        }
        return this;
      }

      @Override
      ClusterContributor build() {
        return new ClusterContributor(
            cluster,
            rate,
            residual,
            buildAndSort(faults));
      }
    }
  }

  static final class SystemContributor extends DeaggContributor {

    final SectionSource section;

    SystemContributor(SectionSource section, double rate, double residual) {
      super(rate, residual);
      this.section = section;
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

      @Override
      Builder addChild(DeaggContributor.Builder contributor) {
        throw new UnsupportedOperationException();
      }

      @Override
      DeaggContributor build() {
        return new SystemContributor(section, rate, residual);
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
    public Iterator<Rupture> iterator() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
      return 1;
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
