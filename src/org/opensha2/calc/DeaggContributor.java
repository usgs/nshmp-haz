package org.opensha2.calc;

import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.eq.model.SourceType.SYSTEM;
import static org.opensha2.internal.TextUtils.NEWLINE;

import org.opensha2.calc.DeaggExport.ContributionFilter;
import org.opensha2.data.Data;
import org.opensha2.data.IntervalArray;
import org.opensha2.data.XySequence;
import org.opensha2.eq.model.ClusterSource;
import org.opensha2.eq.model.Rupture;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;
import org.opensha2.eq.model.SourceType;
import org.opensha2.geo.Location;
import org.opensha2.internal.Parsing;
import org.opensha2.util.Maths;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
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

  abstract StringBuilder appendTo(
      StringBuilder sb,
      String indent,
      ContributionFilter filter);

  abstract List<JsonContributor> toJson(ContributionFilter filter);

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
    public StringBuilder appendTo(
        StringBuilder sb,
        String indent,
        ContributionFilter filter) {

      double contribution = filter.toPercent(total());
      sb.append(String.format(
          DeaggExport.CONTRIB_SOURCE_SET_FMT,
          sourceSet.name(), sourceSet.type(), contribution));
      sb.append(NEWLINE);
      for (DeaggContributor child : children) {
        if (filter.apply(child)) {
          child.appendTo(sb, "  ", filter);
          continue;
        }
        break;
      }
      return sb;
    }

    @Override
    List<JsonContributor> toJson(ContributionFilter filter) {
      ArrayList<JsonContributor> jsonList = new ArrayList<>();
      JsonContributor jc = JsonContributor.createMulti(
          sourceSet.name(),
          sourceSet.type(),
          Maths.round(filter.toPercent(total()), 2));
      jsonList.add(jc);
      for (DeaggContributor child : children) {
        if (filter.apply(child)) {
          jsonList.addAll(child.toJson(filter));
          continue;
        }
        break;
      }
      return jsonList;
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
   * TODO Builders should have better state checking. e.g. once the source has
   * been set for a source contributor, it may not be set again. Likewise, add()
   * can not be called unless source is set.
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
    StringBuilder appendTo(
        StringBuilder sb,
        String indent,
        ContributionFilter filter) {

      double total = total();
      double rBar = rScaled / total;
      double mBar = mScaled / total;
      double εBar = εScaled / total;
      sb.append(String.format(
          DeaggExport.CONTRIB_SOURCE_FMT,
          indent + source.name(),
          rBar, mBar, εBar,
          location.lon(), location.lat(), azimuth,
          filter.toPercent(total)));
      sb.append(NEWLINE);
      return sb;
    }

    @Override
    List<JsonContributor> toJson(ContributionFilter filter) {
      double total = total();
      double rBar = rScaled / total;
      double mBar = mScaled / total;
      double εBar = εScaled / total;
      JsonContributor jc = JsonContributor.createSingle(
          source.name(),
          source.type(),
          Maths.round(filter.toPercent(total()), 2),
          -1,
          rBar,
          mBar,
          εBar,
          azimuth,
          location.lat(),
          location.lon());
      return ImmutableList.of(jc);
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
    final Location location;
    final double azimuth;

    ClusterContributor(
        ClusterSource cluster,
        List<DeaggContributor> faults,
        Location location,
        double azimuth,
        double rate,
        double residual,
        double rScaled,
        double mScaled,
        double εScaled) {

      super(rate, residual, rScaled, mScaled, εScaled);
      this.cluster = cluster;
      this.faults = faults;
      this.location = location;
      this.azimuth = azimuth;
    }

    @Override
    public String toString() {
      return "Cluster contributor: " + cluster.name();
    }

    @Override
    StringBuilder appendTo(
        StringBuilder sb,
        String indent,
        ContributionFilter filter) {

      double total = total();
      double rBar = rScaled / total;
      double mBar = mScaled / total;
      double εBar = εScaled / total;
      sb.append(String.format(
          DeaggExport.CONTRIB_SOURCE_FMT,
          indent + cluster.name(),
          rBar, mBar, εBar,
          location.lon(), location.lat(), azimuth,
          filter.toPercent(total)));
      sb.append(NEWLINE);
      for (DeaggContributor fault : faults) {
        if (filter.apply(fault)) {
          fault.appendTo(sb, "    ", filter);
          continue;
        }
        break;
      }
      return sb;
    }

    @Override
    List<JsonContributor> toJson(ContributionFilter filter) {
      double total = total();
      double rBar = rScaled / total;
      double mBar = mScaled / total;
      double εBar = εScaled / total;
      JsonContributor jc = JsonContributor.createSingle(
          cluster.name(),
          cluster.type(),
          Maths.round(filter.toPercent(total()), 2),
          -2,
          rBar,
          mBar,
          εBar,
          azimuth,
          location.lat(),
          location.lon());
      return ImmutableList.of(jc);
    }

    static final class Builder extends DeaggContributor.Builder {

      ClusterSource cluster;
      ArrayList<DeaggContributor.Builder> faults = new ArrayList<>();
      Location location;
      double azimuth;

      Builder cluster(ClusterSource cluster, Location location, double azimuth) {
        this.cluster = cluster;
        this.location = location;
        this.azimuth = azimuth;
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

  private static double[] sumChildValues(Collection<DeaggContributor> contributors) {
    double[] sum = new double[5];
    for (DeaggContributor contributor : contributors) {
      Data.add(sum, contributor.values());
    }
    return sum;
  }

  static final class SystemContributor extends DeaggContributor {

    final SectionSource section;
    final Location location;
    final double azimuth;
    final IntervalArray mfd;

    SystemContributor(
        SectionSource section,
        Location location,
        double azimuth,
        IntervalArray mfd,
        double rate,
        double residual,
        double rScaled,
        double mScaled,
        double εScaled) {

      super(rate, residual, rScaled, mScaled, εScaled);
      this.section = section;
      this.location = location;
      this.azimuth = azimuth;
      this.mfd = mfd;
    }

    @Override
    public String toString() {
      return "System contributor: Section " + section.index;
    }

    @Override
    StringBuilder appendTo(
        StringBuilder sb,
        String indent,
        ContributionFilter filter) {

      double total = total();
      double rBar = rScaled / total;
      double mBar = mScaled / total;
      double εBar = εScaled / total;
      sb.append(String.format(
          DeaggExport.CONTRIB_SOURCE_FMT,
          indent + section.name(),
          rBar, mBar, εBar,
          location.lon(), location.lat(), azimuth,
          filter.toPercent(total)));
      sb.append(NEWLINE);
      return sb;
    }

    @Override
    List<JsonContributor> toJson(ContributionFilter filter) {
      double total = total();
      double rBar = rScaled / total;
      double mBar = mScaled / total;
      double εBar = εScaled / total;
      JsonContributor jc = JsonContributor.createSingle(
          section.name(),
          section.type(),
          Maths.round(filter.toPercent(total()), 2),
          -3,
          rBar,
          mBar,
          εBar,
          azimuth,
          location.lat(),
          location.lon());
      return ImmutableList.of(jc);
    }

    StringBuilder appendMfd(StringBuilder sb) {
      sb.append(String.format(DeaggExport.SYSTEM_MFD_FORMAT, section.index, section.name()));
      sb.append(Parsing.toString(mfd.values().yValues(), "%9.3g", ",", false, false));
      sb.append(NEWLINE);
      return sb;
    }

    static final class Builder extends DeaggContributor.Builder {

      SectionSource section;
      Location location;
      double azimuth;
      IntervalArray.Builder mfd;

      Builder section(
          SectionSource section,
          Location location,
          double azimuth,
          IntervalArray.Builder mfd) {

        this.section = section;
        this.location = location;
        this.azimuth = azimuth;
        this.mfd = mfd;
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

      /*
       * The mfd data could be backed out on every call to add, but this likely
       * requires a bin index lookup to be repeated across all relevant Gmms.
       */
      Builder addToMfd(int index, double rate) {
        mfd.add(index, rate);
        return this;
      }

      Builder addMfd(IntervalArray mfd) {
        this.mfd.add(mfd);
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
            location,
            azimuth,
            mfd.build(),
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

    final int index;
    final String name;

    SectionSource(int index, String name) {
      this.index = index;
      this.name = name;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public int size() {
      // TODO perhaps this should return the number of participating ruptures
      return 1;
    }

    @Override
    public int id() {
      return index;
    }

    @Override
    public SourceType type() {
      return SYSTEM;
    }

    @Override
    public Location location(Location site) {
      return null; // unused
    }

    @Override
    public List<XySequence> mfds() {
      return null;
      // TODO this needs to return something, not sure what yet
    }

    @Override
    public Iterator<Rupture> iterator() {
      throw new UnsupportedOperationException();
    }
  }

  /* Wrapper of contributor data suitable for JSON serialization. */
  static final class JsonContributor {
    String name;
    String source;
    JsonContributorType type;
    Double contribution;
    Integer id;
    Double r;
    Double m;
    Double ε;
    Double azimuth;
    Double latitude;
    Double longitude;

    /* Use for SourceSets. */
    static JsonContributor createMulti(
        String name, 
        SourceType source, 
        double contribution) {
      
      JsonContributor jc = new JsonContributor();
      jc.name = name;
      jc.source = source.toString();
      jc.type = JsonContributorType.SET;
      jc.contribution = contribution;
      return jc;
    }

    /* Use for more detailed individual sources. */
    static JsonContributor createSingle(
        String name,
        SourceType source,
        double contribution,
        int id,
        double r,
        double m,
        double ε,
        double azimuth,
        double latitude,
        double longitude) {

      JsonContributor jc = new JsonContributor();
      jc.name = name;
      jc.source = source.toString();
      jc.type = JsonContributorType.SINGLE;
      jc.contribution = contribution;
      jc.id = id;
      jc.r = r;
      jc.m = m;
      jc.ε = ε;
      jc.azimuth = azimuth;
      jc.latitude = latitude;
      jc.longitude = longitude;
      return jc;
    }
  }

  private static enum JsonContributorType {
    SET,
    SINGLE;
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
