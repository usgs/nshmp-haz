package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;

import static org.opensha2.internal.Parsing.readDouble;
import static org.opensha2.internal.Parsing.readEnum;
import static org.opensha2.internal.Parsing.readInt;
import static org.opensha2.internal.Parsing.readString;
import static org.opensha2.internal.Parsing.toMap;
import static org.opensha2.internal.SourceAttribute.DEPTH;
import static org.opensha2.internal.SourceAttribute.DIP;
import static org.opensha2.internal.SourceAttribute.ID;
import static org.opensha2.internal.SourceAttribute.NAME;
import static org.opensha2.internal.SourceAttribute.RAKE;
import static org.opensha2.internal.SourceAttribute.RUPTURE_SCALING;
import static org.opensha2.internal.SourceAttribute.TYPE;
import static org.opensha2.internal.SourceAttribute.WEIGHT;
import static org.opensha2.internal.SourceAttribute.WIDTH;

import org.opensha2.data.Data;
import org.opensha2.eq.Earthquakes;
import org.opensha2.eq.fault.surface.RuptureScaling;
import org.opensha2.eq.model.MfdHelper.GR_Data;
import org.opensha2.eq.model.MfdHelper.IncrData;
import org.opensha2.eq.model.MfdHelper.SingleData;
import org.opensha2.geo.LocationList;
import org.opensha2.internal.SourceElement;
import org.opensha2.mfd.IncrementalMfd;
import org.opensha2.mfd.MfdType;
import org.opensha2.mfd.Mfds;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.Lists;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

/*
 * Non-validating fault source parser. SAX parser 'Attributes' are stateful and
 * cannot be stored. This class is not thread safe.
 *
 * @author Peter Powers
 */
@SuppressWarnings("incomplete-switch")
class FaultParser extends DefaultHandler {

  private final Logger log = Logger.getLogger(FaultParser.class.getName());
  private final SAXParser sax;
  private boolean used = false;

  private Locator locator;

  private GmmSet gmmSet;

  private ModelConfig config;

  private FaultSourceSet sourceSet;
  private FaultSourceSet.Builder sourceSetBuilder;
  private FaultSource.Builder sourceBuilder;

  private RuptureScaling rupScaling;

  // Data applying to all sourceSet
  private MagUncertainty unc = null;
  private Map<String, String> epiAtts = null;
  private Map<String, String> aleaAtts = null;

  // Default MFD data
  private boolean parsingDefaultMFDs = false;
  private MfdHelper.Builder mfdHelperBuilder;
  private MfdHelper mfdHelper;

  // Traces are the only text content in source files
  private boolean readingTrace = false;
  private StringBuilder traceBuilder = null;

  private FaultParser(SAXParser sax) {
    this.sax = sax;
  }

  static FaultParser create(SAXParser sax) {
    return new FaultParser(checkNotNull(sax));
  }

  FaultSourceSet parse(InputStream in, GmmSet gmmSet, ModelConfig config) throws SAXException,
      IOException {
    checkState(!used, "This parser has expired");
    this.gmmSet = gmmSet;
    this.config = config;
    sax.parse(in, this);
    checkState(sourceSet.size() > 0, "FaultSourceSet is empty");
    used = true;
    return sourceSet;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes atts)
      throws SAXException {

    SourceElement e = null;
    try {
      e = SourceElement.fromString(qName);
    } catch (IllegalArgumentException iae) {
      throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
    }

    try {
      switch (e) {

        case FAULT_SOURCE_SET:
          String name = readString(NAME, atts);
          double weight = readDouble(WEIGHT, atts);
          int id = readInt(ID, atts);
          sourceSetBuilder = new FaultSourceSet.Builder();
          sourceSetBuilder
              .name(name)
              .id(id)
              .weight(weight)
              .gmms(gmmSet);
          if (log.isLoggable(FINE)) {
            log.fine("");
            log.fine("       Name: " + name);
            log.fine("     Weight: " + weight);
          }
          mfdHelperBuilder = MfdHelper.builder();
          // dummy; usually overwritten
          mfdHelper = mfdHelperBuilder.build();
          break;

        case DEFAULT_MFDS:
          parsingDefaultMFDs = true;
          break;

        case EPISTEMIC:
          epiAtts = toMap(atts);
          break;

        case ALEATORY:
          aleaAtts = toMap(atts);
          break;

        case SOURCE_PROPERTIES:
          rupScaling = readEnum(RUPTURE_SCALING, atts, RuptureScaling.class);
          log.fine("Rup scaling: " + rupScaling);
          break;

        case SOURCE:
          String srcName = readString(NAME, atts);
          int srcId = readInt(ID, atts);
          sourceBuilder = new FaultSource.Builder()
              .name(srcName)
              .id(srcId)
              .ruptureScaling(rupScaling)
              .ruptureFloating(config.ruptureFloating)
              .ruptureVariability(config.ruptureVariability)
              .surfaceSpacing(config.surfaceSpacing);
          log.fine("     Source: " + srcName + " [" + srcId + "]");
          if (srcId < 0) {
            log.warning("  Invalid Id [" + srcId + ", " + srcName + "]");
          }
          break;

        case INCREMENTAL_MFD:
          if (parsingDefaultMFDs) {
            mfdHelperBuilder.addDefault(atts);
            break;
          }
          sourceBuilder.mfds(buildMfds(atts));
          break;

        case GEOMETRY:
          sourceBuilder.depth(readDouble(DEPTH, atts))
              .dip(readDouble(DIP, atts))
              .rake(readDouble(RAKE, atts))
              .width(readDouble(WIDTH, atts));
          break;

        case TRACE:
          readingTrace = true;
          traceBuilder = new StringBuilder();
          break;
      }
      // @formatter:on
    } catch (Exception ex) {
      throw new SAXParseException("Error parsing <" + qName + ">", locator, ex);
    }
  }

  @Override
  public void endElement(String uri, String localName, String qName)
      throws SAXException {

    SourceElement e = null;
    try {
      e = SourceElement.fromString(qName);
    } catch (IllegalArgumentException iae) {
      throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
    }

    try {
      switch (e) {

        case DEFAULT_MFDS:
          parsingDefaultMFDs = false;
          mfdHelper = mfdHelperBuilder.build();
          break;

        case SETTINGS:
          // may not have mag uncertainty element so create the
          // uncertainty container upon leaving 'Settings'
          unc = MagUncertainty.create(epiAtts, aleaAtts);
          if (log.isLoggable(FINE)) {
            log.fine(unc.toString());
          }
          break;

        case TRACE:
          readingTrace = false;
          sourceBuilder.trace(LocationList.fromString(traceBuilder.toString()));
          break;

        case SOURCE:
          sourceSetBuilder.source(sourceBuilder.buildFaultSource());
          log.finer(""); // insert blank line for detailed output
          break;

        case FAULT_SOURCE_SET:
          sourceSet = sourceSetBuilder.buildFaultSet();
          break;
      }

    } catch (Exception ex) {
      throw new SAXParseException("Error parsing <" + qName + ">", locator, ex);
    }
  }

  @Override
  public void characters(char ch[], int start, int length) throws SAXException {
    if (readingTrace) {
      traceBuilder.append(ch, start, length);
    }
  }

  @Override
  public void setDocumentLocator(Locator locator) {
    this.locator = locator;
  }

  private List<IncrementalMfd> buildMfds(Attributes atts) {
    MfdType type = readEnum(TYPE, atts, MfdType.class);
    switch (type) {
      case GR:
        return buildGR(atts);
      case SINGLE:
        return buildSingle(atts);
      case INCR:
        return buildIncremental(atts);
      default:
        throw new IllegalStateException(type + " not supported");
    }
  }

  /*
   * FaultSource.Builder creates an ImmutableList; so no need for one in methods
   * below.
   */

  /*
   * Build INCR MFDs. NOTE Incremental ignores uncertainty models.
   */
  private List<IncrementalMfd> buildIncremental(Attributes atts) {
    List<IncrementalMfd> mfdList = new ArrayList<>();
    for (IncrData incrData : mfdHelper.incrementalData(atts)) {
      mfdList.addAll(buildIncremental(incrData));
    }
    return mfdList;
  }

  private List<IncrementalMfd> buildIncremental(IncrData data) {
    List<IncrementalMfd> mfds = Lists.newArrayList();
    IncrementalMfd mfd = Mfds.newIncrementalMFD(data.mags,
        Data.multiply(data.weight, data.rates));
    mfds.add(mfd);
    return mfds;
  }

  /*
   * Build GR MFDs. Method throws IllegalStateException if attribute values
   * yield an MFD with no magnitude bins.
   */
  private List<IncrementalMfd> buildGR(Attributes atts) {
    List<IncrementalMfd> mfdList = new ArrayList<>();
    for (GR_Data grData : mfdHelper.grData(atts)) {
      mfdList.addAll(buildGR(grData));
    }
    return mfdList;
  }

  private List<IncrementalMfd> buildGR(GR_Data data) {

    int nMag = Mfds.magCount(data.mMin, data.mMax, data.dMag);
    checkState(nMag > 0, "GR MFD with no mags [%s]", sourceBuilder.name);

    double tmr = Mfds.totalMoRate(data.mMin, nMag, data.dMag, data.a, data.b);

    List<IncrementalMfd> mfds = Lists.newArrayList();

    // this was handled previously by GR_Data.hasMagExceptions()
    // TODO edge cases (Rush Peak in brange.3dip.gr.in) suggest
    // data.mMin should be used instead of unc.epiCutoff
    boolean uncertAllowed = unc.hasEpistemic && (data.mMax + unc.epiDeltas[0]) >= unc.epiCutoff;

    if (unc.hasEpistemic && uncertAllowed) {
      for (int i = 0; i < unc.epiCount; i++) {
        // update mMax and nMag
        double mMaxEpi = data.mMax + unc.epiDeltas[i];
        int nMagEpi = Mfds.magCount(data.mMin, mMaxEpi, data.dMag);
        if (nMagEpi > 0) {
          double weightEpi = data.weight * unc.epiWeights[i];

          // epi branches preserve Mo between mMin and dMag(nMag-1),
          // not mMax to ensure that Mo is 'spent' on earthquakes
          // represented by the epi GR distribution with adj. mMax.

          IncrementalMfd mfd = Mfds.newGutenbergRichterMoBalancedMFD(data.mMin,
              data.dMag, nMagEpi, data.b, tmr * weightEpi);
          mfds.add(mfd);
          log.finer("   MFD type: GR [+epi -alea] " + epiBranch(i));
          if (log.isLoggable(FINEST)) {
            log.finest(mfd.getMetadataString());
          }
        } else {
          log.warning("  GR MFD epi branch with no mags [" + sourceBuilder.name + "]");

        }
      }
    } else {
      IncrementalMfd mfd = Mfds.newGutenbergRichterMoBalancedMFD(data.mMin, data.dMag,
          nMag, data.b, tmr * data.weight);
      mfds.add(mfd);
      log.finer("   MFD type: GR [-epi -alea]");
      if (log.isLoggable(FINEST)) {
        log.finest(mfd.getMetadataString());
      }
    }
    return mfds;
  }

  /* Build SINGLE MFDs. */
  private List<IncrementalMfd> buildSingle(Attributes atts) {
    List<IncrementalMfd> mfdList = new ArrayList<>();
    for (SingleData singleData : mfdHelper.singleData(atts)) {
      mfdList.addAll(buildSingle(singleData));
    }
    return mfdList;
  }

  private List<IncrementalMfd> buildSingle(SingleData data) {

    List<IncrementalMfd> mfds = Lists.newArrayList();

    // total moment rate
    double tmr = data.rate * Earthquakes.magToMoment(data.m);
    // total event rate
    double tcr = data.rate;

    // this was handled previously by GR_Data.hasMagExceptions()
    // need to catch the single floaters that are less than 6.5
    // note: unc.epiDeltas[0] may be null
    double minUncertMag = data.m + (unc.hasEpistemic ? unc.epiDeltas[0] : 0.0);
    boolean uncertAllowed = !((minUncertMag < unc.epiCutoff) && data.floats);

    // loop over epistemic uncertainties
    if (unc.hasEpistemic && uncertAllowed) {
      for (int i = 0; i < unc.epiCount; i++) {

        double epiMag = data.m + unc.epiDeltas[i];
        double mfdWeight = data.weight * unc.epiWeights[i];

        if (unc.hasAleatory) {
          IncrementalMfd mfd = (unc.moBalance)
              ? Mfds.newGaussianMoBalancedMFD(
                  epiMag,
                  unc.aleaSigma,
                  unc.aleaCount,
                  mfdWeight * tmr,
                  data.floats)
              : Mfds.newGaussianMFD(
                  epiMag,
                  unc.aleaSigma,
                  unc.aleaCount,
                  mfdWeight * tcr,
                  data.floats);
          mfds.add(mfd);
          log.finer("   MFD type: SINGLE [+epi +alea] " + epiBranch(i));
          if (log.isLoggable(FINEST)) {
            log.finest(mfd.getMetadataString());
          }
        } else {

          // single Mfds with epi uncertainty are moment balanced at
          // the
          // central/single magnitude of the distribution

          double moRate = tmr * mfdWeight;
          IncrementalMfd mfd = Mfds.newSingleMoBalancedMFD(epiMag, moRate, data.floats);
          mfds.add(mfd);
          log.finer("   MFD type: SINGLE [+epi -alea] " + epiBranch(i));
          if (log.isLoggable(FINEST)) {
            log.finest(mfd.getMetadataString());
          }
        }
      }
    } else {
      if (unc.hasAleatory && uncertAllowed) {
        IncrementalMfd mfd = (unc.moBalance)
            ? Mfds.newGaussianMoBalancedMFD(
                data.m,
                unc.aleaSigma,
                unc.aleaCount,
                data.weight * tmr,
                data.floats)
            : Mfds.newGaussianMFD(
                data.m,
                unc.aleaSigma,
                unc.aleaCount,
                data.weight * tcr,
                data.floats);
        mfds.add(mfd);
        log.finer("   MFD type: SINGLE [-epi +alea]");
        if (log.isLoggable(FINEST)) {
          log.finest(mfd.getMetadataString());
        }
      } else {
        IncrementalMfd mfd =
            Mfds.newSingleMFD(data.m, data.weight * data.rate, data.floats);
        mfds.add(mfd);
        log.finer("   MFD type: SINGLE [-epi -alea]");
        if (log.isLoggable(FINEST)) {
          log.finest(mfd.getMetadataString());
        }
      }
    }
    return mfds;
  }

  private static String epiBranch(int i) {
    return (i == 0) ? "(M-epi)" : (i == 2) ? "(M+epi)" : "(M)";
  }

}
