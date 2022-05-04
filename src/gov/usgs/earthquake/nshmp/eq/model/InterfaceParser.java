package gov.usgs.earthquake.nshmp.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.internal.Parsing.readDouble;
import static gov.usgs.earthquake.nshmp.internal.Parsing.readEnum;
import static gov.usgs.earthquake.nshmp.internal.Parsing.readInt;
import static gov.usgs.earthquake.nshmp.internal.Parsing.readString;
import static gov.usgs.earthquake.nshmp.internal.Parsing.toMap;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.DEPTH;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.DIP;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.ID;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.NAME;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.RAKE;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.RUPTURE_SCALING;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.WEIGHT;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.WIDTH;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.Lists;

import gov.usgs.earthquake.nshmp.eq.Earthquakes;
import gov.usgs.earthquake.nshmp.eq.fault.surface.RuptureScaling;
import gov.usgs.earthquake.nshmp.eq.model.MfdHelper.GR_Data;
import gov.usgs.earthquake.nshmp.eq.model.MfdHelper.SingleData;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.internal.SourceElement;
import gov.usgs.earthquake.nshmp.mfd.IncrementalMfd;
import gov.usgs.earthquake.nshmp.mfd.MfdType;
import gov.usgs.earthquake.nshmp.mfd.Mfds;

/*
 * Non-validating subduction source parser. SAX parser 'Attributes' are stateful
 * and cannot be stored. This class is not thread safe.
 *
 * @author Peter Powers
 */
@SuppressWarnings("incomplete-switch")
class InterfaceParser extends DefaultHandler {

  private final Logger log = Logger.getLogger(InterfaceParser.class.getName());
  private final SAXParser sax;
  private boolean used = false;

  private Locator locator;

  private GmmSet gmmSet;

  private ModelConfig config;

  private InterfaceSourceSet sourceSet;
  private InterfaceSourceSet.Builder sourceSetBuilder;
  private InterfaceSource.Builder sourceBuilder;

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

  private InterfaceParser(SAXParser sax) {
    this.sax = sax;
  }

  static InterfaceParser create(SAXParser sax) {
    return new InterfaceParser(checkNotNull(sax));
  }

  InterfaceSourceSet parse(
      InputStream in,
      GmmSet gmmSet,
      ModelConfig config) throws SAXException, IOException {

    checkState(!used, "This parser has expired");
    this.gmmSet = gmmSet;
    this.config = config;
    sax.parse(in, this);
    checkState(sourceSet.size() > 0, "InterfaceSourceSet is empty");
    used = true;
    return sourceSet;
  }

  @Override
  public void startElement(
      String uri,
      String localName,
      String qName,
      Attributes atts) throws SAXException {

    SourceElement e = null;
    try {
      e = SourceElement.fromString(qName);
    } catch (IllegalArgumentException iae) {
      throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
    }

    try {

      switch (e) {

        case SUBDUCTION_SOURCE_SET:
          String name = readString(NAME, atts);
          int id = readInt(ID, atts);
          double weight = readDouble(WEIGHT, atts);
          sourceSetBuilder = new InterfaceSourceSet.Builder();
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
          mfdHelper = mfdHelperBuilder.build(); // dummy; usually overwritten
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
          sourceBuilder = new InterfaceSource.Builder();
          sourceBuilder.name(srcName);
          sourceBuilder.id(srcId);
          sourceBuilder.ruptureScaling(rupScaling);
          sourceBuilder.ruptureFloating(config.ruptureFloating);
          sourceBuilder.ruptureVariability(config.ruptureVariability);
          sourceBuilder.surfaceSpacing(config.surfaceSpacing);
          log.fine("     Source: " + srcName);
          break;

        case INCREMENTAL_MFD:
          if (parsingDefaultMFDs) {
            mfdHelperBuilder.addDefault(atts);
            break;
          }
          sourceBuilder.mfds(buildMfds(atts));
          break;

        case GEOMETRY:
          sourceBuilder.rake(readDouble(RAKE, atts));

          /*
           * At present, an InterfaceSource geometry may be defined with an
           * upper trace, dip, depth and width, or and upper and lower trace.
           * Rake is always required (above), but the three scalar parameters
           * (dip, depth, and width) must be conditionally read. The
           * InterfaceSource.Builder will check if either construction technique
           * has been satisfied.
           */
          try {
            sourceBuilder.depth(readDouble(DEPTH, atts))
                .dip(readDouble(DIP, atts))
                .width(readDouble(WIDTH, atts));
          } catch (NullPointerException npe) {
            // keep moving, these atts are not necessarily required
          }
          break;

        case TRACE:
          readingTrace = true;
          traceBuilder = new StringBuilder();
          break;

        case LOWER_TRACE:
          readingTrace = true;
          traceBuilder = new StringBuilder();
          break;
      }

    } catch (Exception ex) {
      throw new SAXParseException("Error parsing <" + qName + ">", locator, ex);
    }
  }

  @Override
  public void endElement(
      String uri,
      String localName,
      String qName) throws SAXException {

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

        case LOWER_TRACE:
          readingTrace = false;
          sourceBuilder.lowerTrace(LocationList.fromString(traceBuilder.toString()));
          break;

        case SOURCE:
          sourceSetBuilder.source(sourceBuilder.buildSubductionSource());
          log.finer(""); // insert blank line for detailed source
          // output
          break;

        case SUBDUCTION_SOURCE_SET:
          sourceSet = sourceSetBuilder.buildSubductionSet();

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
    MfdType type = MfdType.valueOf(atts.getValue("type"));
    switch (type) {
      case GR:
        return buildGR(atts);
      case SINGLE:
        return buildSingle(atts);
      default:
        throw new IllegalStateException(type + " not supported");
    }
  }

  /*
   * InterfaceSource.Builder creates an ImmutableList; so no need for one in
   * methods below.
   */

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
    checkState(nMag > 0, "GR MFD with no mags");
    double tmr = Mfds.totalMoRate(data.mMin, nMag, data.dMag, data.a, data.b);
    List<IncrementalMfd> mfds = Lists.newArrayList();

    if (unc.hasEpistemic) {
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
      IncrementalMfd mfd = Mfds.newGutenbergRichterMoBalancedMFD(data.mMin, data.dMag, nMag,
          data.b, tmr * data.weight);
      mfds.add(mfd);
      log.finer("   MFD type: GR");
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
      mfdList.add(buildSingle(singleData));
    }
    return mfdList;
  }

  // only aleatory supported
  private IncrementalMfd buildSingle(SingleData data) {

    if (unc.hasAleatory) {
      double tmr = data.rate * Earthquakes.magToMoment(data.m);
      if (!unc.moBalance) {
        throw new RuntimeException("moBalance must be 'true'");
      }
      IncrementalMfd mfd = Mfds.newGaussianMoBalancedMFD(
          data.m,
          unc.aleaSigma,
          unc.aleaCount,
          data.weight * tmr,
          data.floats);
      log.finer("   MFD type: SINGLE [+alea]");
      if (log.isLoggable(FINEST)) {
        log.finest(mfd.getMetadataString());
      }
      return mfd;
    }

    IncrementalMfd mfd = Mfds.newSingleMFD(data.m, data.weight * data.rate, data.floats);
    log.finer("   MFD type: SINGLE");
    if (log.isLoggable(FINEST)) {
      log.finest(mfd.getMetadataString());
    }
    return mfd;
  }

  private static String epiBranch(int i) {
    return (i == 0) ? "(M-epi)" : (i == 2) ? "(M+epi)" : "(M)";
  }

}
