package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.eq.model.SourceAttribute.A;
import static org.opensha2.eq.model.SourceAttribute.B;
import static org.opensha2.eq.model.SourceAttribute.C_MAG;
import static org.opensha2.eq.model.SourceAttribute.D_MAG;
import static org.opensha2.eq.model.SourceAttribute.FLOATS;
import static org.opensha2.eq.model.SourceAttribute.M;
import static org.opensha2.eq.model.SourceAttribute.MAGS;
import static org.opensha2.eq.model.SourceAttribute.M_MAX;
import static org.opensha2.eq.model.SourceAttribute.M_MIN;
import static org.opensha2.eq.model.SourceAttribute.RATE;
import static org.opensha2.eq.model.SourceAttribute.RATES;
import static org.opensha2.eq.model.SourceAttribute.WEIGHT;
import static org.opensha2.util.Parsing.readBoolean;
import static org.opensha2.util.Parsing.readDouble;
import static org.opensha2.util.Parsing.readString;
import static org.opensha2.util.Parsing.toDoubleArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opensha2.mfd.MfdType;
import org.xml.sax.Attributes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Doubles;

/*
 * MFD data handler class. Stores default data and creates copies with
 * overridden (non-default) fields. This class ensures that all required
 * attributes for default Mfds are present.
 */
class MfdHelper {

  // @formatter:off
	/*
	 * For documentation:
	 * 
	 * MFD default recombination rules
	 * 
	 * Grid, Slab sources (with spatially varying rates)
	 * 		- a <Node> can only be of one type
	 * 		- will map to the default of that type
	 * 				(e.g. CA in 2008 had downweighted M>6.5 in places that
	 * 				led to a node being either pure GR or INCR, but never both)
	 * 		- only support multiple defaults of the same type
	 * 
	 * Fault, Interface sources
	 * 
	 * System
	 * 		- only supports a single SINGLE default MFD at this time
	 * 
	 * How to manage MFD Type and Id mixing and matching?
	 * 
	 * Other notes:
	 * 
	 * Most source files will have a <settings> block
	 * those that don't will not trigger build() on a 
	 * MfdHelper.Builder so most parsers preempt this possibility
	 * be creating and empty Helper that is then
	 * ocverridden in most cases.
	 * 
	 */
	// @formatter:on

  // mfd data instances
  private final List<SingleData> singleDefaults;
  private final List<GR_Data> grDefaults;
  private final List<IncrData> incrDefaults;
  private final List<TaperData> taperDefaults;

  private MfdHelper(
      List<SingleData> singleDefaults,
      List<GR_Data> grDefaults,
      List<IncrData> incrDefaults,
      List<TaperData> taperDefaults) {

    this.singleDefaults = singleDefaults;
    this.grDefaults = grDefaults;
    this.incrDefaults = incrDefaults;
    this.taperDefaults = taperDefaults;
  }

  static Builder builder() {
    return new Builder();
  }

  List<SingleData> singleData(Attributes atts) {
    if (singleDefaults.isEmpty()) return ImmutableList.of(new SingleData(atts));
    List<SingleData> dataList = new ArrayList<>();
    for (SingleData singleDefault : singleDefaults) {
      dataList.add(new SingleData(atts, singleDefault));
    }
    Collections.sort(dataList);
    return ImmutableList.copyOf(dataList);
  }

  List<GR_Data> grData(Attributes atts) {
    if (grDefaults.isEmpty()) return ImmutableList.of(new GR_Data(atts));
    ImmutableList.Builder<GR_Data> builder = ImmutableList.builder();
    for (GR_Data grDefault : grDefaults) {
      builder.add(new GR_Data(atts, grDefault));
    }
    return builder.build();
  }

  List<IncrData> incrementalData(Attributes atts) {
    if (incrDefaults.isEmpty()) return ImmutableList.of(new IncrData(atts));
    ImmutableList.Builder<IncrData> builder = ImmutableList.builder();
    for (IncrData incrDefault : incrDefaults) {
      builder.add(new IncrData(atts, incrDefault));
    }
    return builder.build();
  }

  List<TaperData> taperData(Attributes atts) {
    if (taperDefaults.isEmpty()) return ImmutableList.of(new TaperData(atts));
    ImmutableList.Builder<TaperData> builder = ImmutableList.builder();
    for (TaperData taperDefault : taperDefaults) {
      builder.add(new TaperData(atts, taperDefault));
    }
    return builder.build();
  }

  /*
   * Used to by parsers to impose restrictions on the types of default MFDs
   * allowed.
   */
  int typeCount(MfdType type) {
    switch (type) {
      case GR:
        return grDefaults.size();
      case INCR:
        return incrDefaults.size();
      case SINGLE:
        return singleDefaults.size();
      case GR_TAPER:
        return taperDefaults.size();
      default:
        throw new IllegalArgumentException("Unknown MFD type: " + type);
    }
  }

  int size() {
    int size = 0;
    for (MfdType type : MfdType.values()) {
      size += typeCount(type);
    }
    return size;
  }

  /* Re-usable */
  static final class Builder {

    private ImmutableList.Builder<SingleData> singleBuilder = ImmutableList.builder();
    private ImmutableList.Builder<GR_Data> grBuilder = ImmutableList.builder();
    private ImmutableList.Builder<IncrData> incrBuilder = ImmutableList.builder();
    private ImmutableList.Builder<TaperData> taperBuilder = ImmutableList.builder();

    /* Add a default MFD. */
    Builder addDefault(Attributes atts) {
      MfdType type = MfdType.valueOf(atts.getValue("type"));
      switch (type) {
        case GR:
          grBuilder.add(new GR_Data(atts));
          break;
        case INCR:
          incrBuilder.add(new IncrData(atts));
          break;
        case SINGLE:
          singleBuilder.add(new SingleData(atts));
          break;
        case GR_TAPER:
          taperBuilder.add(new TaperData(atts));
          break;
        default:
          throw new IllegalArgumentException("Unknown MFD type: " + type);
      }
      return this;
    }

    MfdHelper build() {
      // MfdHelpers can be empty if no defaults
      // defined for a SourceSet
      return new MfdHelper(
        singleBuilder.build(),
        grBuilder.build(),
        incrBuilder.build(),
        taperBuilder.build());
    }
  }

  static final class SingleData implements Comparable<SingleData> {

    final double rate;
    final double m;
    final boolean floats;
    final double weight;

    /*
     * Requires all attributes be present, but does not throw an exception for
     * extra and unknown attributes.
     */
    private SingleData(Attributes atts) {
      rate = readDouble(RATE, atts);
      m = readDouble(M, atts);
      floats = readBoolean(FLOATS, atts);
      weight = readDouble(WEIGHT, atts);
    }

    /*
     * Iterates supplied attributes; any unkown or extra attributes will result
     * in an exception being thrown.
     */
    private SingleData(Attributes atts, SingleData ref) {

      // set defaults locally
      double rate = ref.rate;
      double m = ref.m;
      boolean floats = ref.floats;
      double weight = ref.weight;

      for (int i = 0; i < atts.getLength(); i++) {
        SourceAttribute att = SourceAttribute.fromString(atts.getQName(i));
        switch (att) {
          case RATE:
            rate = readDouble(RATE, atts);
            break;
          case M:
            m = readDouble(M, atts);
            break;
          case FLOATS:
            floats = readBoolean(FLOATS, atts);
            break;
          case WEIGHT:
            weight = readDouble(WEIGHT, atts);
            break;
          case TYPE:
            break; // ignore
          default:
            throw new IllegalStateException("Invalid attribute for SINGLE MFD: " + att);
        }
      }

      // export final fields
      this.rate = rate;
      this.m = m;
      this.floats = floats;
      this.weight = weight;
    }

    @Override
    public int compareTo(SingleData other) {
      return Doubles.compare(m, other.m);
    }
  }

  // TODO rename to GrData & TaperedGrData
  static final class GR_Data {
    final double a;
    final double b;
    final double dMag;
    final double mMin;
    final double mMax;
    final double weight;

    private GR_Data(Attributes atts) {
      a = readDouble(A, atts);
      b = readDouble(B, atts);
      dMag = readDouble(D_MAG, atts);
      mMax = readDouble(M_MAX, atts);
      mMin = readDouble(M_MIN, atts);
      weight = readDouble(WEIGHT, atts);
    }

    private GR_Data(Attributes atts, GR_Data ref) {

      // set defaults locally
      double a = ref.a;
      double b = ref.b;
      double dMag = ref.dMag;
      double mMax = ref.mMax;
      double mMin = ref.mMin;
      double weight = ref.weight;

      for (int i = 0; i < atts.getLength(); i++) {
        SourceAttribute att = SourceAttribute.fromString(atts.getQName(i));
        switch (att) {
          case A:
            a = readDouble(A, atts);
            break;
          case B:
            b = readDouble(B, atts);
            break;
          case D_MAG:
            dMag = readDouble(D_MAG, atts);
            break;
          case M_MIN:
            mMin = readDouble(M_MIN, atts);
            break;
          case M_MAX:
            mMax = readDouble(M_MAX, atts);
            break;
          case WEIGHT:
            weight = readDouble(WEIGHT, atts);
            break;
          case TYPE:
            break; // ignore
          default:
            throw new IllegalStateException("Invalid attribute for GR MFD: " + att);
        }
      }

      // export final fields
      this.a = a;
      this.b = b;
      this.dMag = dMag;
      this.mMax = mMax;
      this.mMin = mMin;
      this.weight = weight;
    }
  }

  static final class TaperData {
    final double a;
    final double b;
    final double cMag;
    final double dMag;
    final double mMin;
    final double mMax;
    final double weight;

    private TaperData(Attributes atts) {
      a = readDouble(A, atts);
      b = readDouble(B, atts);
      cMag = readDouble(C_MAG, atts);
      dMag = readDouble(D_MAG, atts);
      mMax = readDouble(M_MAX, atts);
      mMin = readDouble(M_MIN, atts);
      weight = readDouble(WEIGHT, atts);
    }

    private TaperData(Attributes atts, TaperData ref) {

      // set defaults locally
      double a = ref.a;
      double b = ref.b;
      double cMag = ref.cMag;
      double dMag = ref.dMag;
      double mMax = ref.mMax;
      double mMin = ref.mMin;
      double weight = ref.weight;

      for (int i = 0; i < atts.getLength(); i++) {
        SourceAttribute att = SourceAttribute.fromString(atts.getQName(i));
        switch (att) {
          case A:
            a = readDouble(A, atts);
            break;
          case B:
            b = readDouble(B, atts);
            break;
          case C_MAG:
            dMag = readDouble(D_MAG, atts);
            break;
          case D_MAG:
            dMag = readDouble(D_MAG, atts);
            break;
          case M_MIN:
            mMin = readDouble(M_MIN, atts);
            break;
          case M_MAX:
            mMax = readDouble(M_MAX, atts);
            break;
          case WEIGHT:
            weight = readDouble(WEIGHT, atts);
            break;
          case TYPE:
            break; // ignore
          default:
            throw new IllegalStateException("Invalid attribute for TAPER MFD: " + att);
        }
      }

      // export final fields
      this.a = a;
      this.b = b;
      this.cMag = cMag;
      this.dMag = dMag;
      this.mMax = mMax;
      this.mMin = mMin;
      this.weight = weight;
    }
  }

  static final class IncrData {

    final double[] mags;
    final double[] rates;
    final double weight;

    private IncrData(Attributes atts) {
      mags = toDoubleArray(readString(MAGS, atts));
      rates = toDoubleArray(readString(RATES, atts));
      checkState(mags.length == rates.length,
        "Inconsistent INCR MFD mag[%s] and rate[%s] arrays",
        mags.length, rates.length);
      weight = readDouble(WEIGHT, atts);
    }

    private IncrData(Attributes atts, IncrData ref) {

      // NOTE values in ref arrays are mutable

      // set defaults locally
      double[] mags = ref.mags;
      double[] rates = ref.rates;
      double weight = ref.weight;

      for (int i = 0; i < atts.getLength(); i++) {
        SourceAttribute att = SourceAttribute.fromString(atts.getQName(i));
        switch (att) {
          case MAGS:
            mags = toDoubleArray(readString(MAGS, atts));
            break;
          case RATES:
            rates = toDoubleArray(readString(RATES, atts));
            break;
          case WEIGHT:
            weight = readDouble(WEIGHT, atts);
            break;
          case TYPE:
            break; // ignore
          case FOCAL_MECH_MAP:
            break; // SYSTEM (UCERF3) grid sources; ignore
          default:
            throw new IllegalStateException("Invalid attribute for INCR MFD: " + att);
        }
      }

      checkState(mags.length == rates.length,
        "Inconsistent INCR MFD mag[%s] and rate[%s] arrays",
        mags.length, rates.length);

      // export final fields
      this.mags = mags;
      this.rates = rates;
      this.weight = weight;
    }

  }

}
