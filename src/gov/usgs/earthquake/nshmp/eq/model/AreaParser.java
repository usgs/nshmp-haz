package gov.usgs.earthquake.nshmp.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.eq.model.SourceType.AREA;
import static gov.usgs.earthquake.nshmp.internal.Parsing.readDouble;
import static gov.usgs.earthquake.nshmp.internal.Parsing.readEnum;
import static gov.usgs.earthquake.nshmp.internal.Parsing.readInt;
import static gov.usgs.earthquake.nshmp.internal.Parsing.readString;
import static gov.usgs.earthquake.nshmp.internal.Parsing.stringToEnumWeightMap;
import static gov.usgs.earthquake.nshmp.internal.Parsing.stringToValueValueWeightMap;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.FOCAL_MECH_MAP;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.ID;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.MAG_DEPTH_MAP;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.MAX_DEPTH;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.NAME;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.RUPTURE_SCALING;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.STRIKE;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.TYPE;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.WEIGHT;
import static java.util.logging.Level.FINE;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.NavigableMap;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import gov.usgs.earthquake.nshmp.data.Data;
import gov.usgs.earthquake.nshmp.eq.fault.FocalMech;
import gov.usgs.earthquake.nshmp.eq.fault.surface.RuptureScaling;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.internal.SourceElement;
import gov.usgs.earthquake.nshmp.mfd.IncrementalMfd;
import gov.usgs.earthquake.nshmp.mfd.MfdType;
import gov.usgs.earthquake.nshmp.mfd.Mfds;

/*
 * Non-validating area source parser. SAX parser 'Attributes' are stateful and
 * cannot be stored. This class is not thread safe.
 *
 * @author Peter Powers
 */
@SuppressWarnings("incomplete-switch")
class AreaParser extends DefaultHandler {

  private final Logger log = Logger.getLogger(AreaParser.class.getName());
  private final SAXParser sax;
  private boolean used = false;

  private Locator locator;

  // Default MFD data
  private boolean parsingDefaultMFDs = false;
  private MfdHelper.Builder mfdHelperBuilder;
  private MfdHelper mfdHelper;

  private GmmSet gmmSet;

  private ModelConfig config;

  private AreaSourceSet sourceSet;
  private AreaSourceSet.Builder sourceSetBuilder;
  private AreaSource.Builder sourceBuilder;

  // Node locations are the only text content in source files
  private boolean readingBorder = false;
  private StringBuilder borderBuilder = null;

  // Used to when validating depths in magDepthMap
  SourceType type = AREA;

  private AreaParser(SAXParser sax) {
    this.sax = checkNotNull(sax);
  }

  static AreaParser create(SAXParser sax) {
    return new AreaParser(sax);
  }

  AreaSourceSet parse(
      InputStream in,
      GmmSet gmmSet,
      ModelConfig config) throws SAXException, IOException {

    checkState(!used, "This parser has expired");
    this.gmmSet = gmmSet;
    this.config = config;
    sax.parse(in, this);
    used = true;
    return sourceSet;
  }

  @Override
  public void startElement(
      String uri,
      String localName,
      String qName, Attributes atts) throws SAXException {

    SourceElement e = null;
    try {
      e = SourceElement.fromString(qName);
    } catch (IllegalArgumentException iae) {
      throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
    }
    switch (e) {

      case AREA_SOURCE_SET:
        String name = readString(NAME, atts);
        int id = readInt(ID, atts);
        double weight = readDouble(WEIGHT, atts);
        sourceSetBuilder = new AreaSourceSet.Builder()
            .name(name)
            .id(id)
            .weight(weight);

        sourceSetBuilder.gmms(gmmSet);
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

      case SOURCE:
        String srcName = readString(NAME, atts);
        int srcId = readInt(ID, atts);
        sourceBuilder = new AreaSource.Builder()
            .name(srcName)
            .id(srcId)
            .gridScaling(config.areaGridScaling);
        log.fine("     Source: " + srcName);
        break;

      case SOURCE_PROPERTIES:
        // identical to shared source properties in grid sources
        // but with one element for each area source
        String depthMapStr = readString(MAG_DEPTH_MAP, atts);
        NavigableMap<Double, Map<Double, Double>> depthMap =
            stringToValueValueWeightMap(depthMapStr);
        double maxDepth = readDouble(MAX_DEPTH, atts);
        String mechMapStr = readString(FOCAL_MECH_MAP, atts);
        Map<FocalMech, Double> mechMap = stringToEnumWeightMap(mechMapStr, FocalMech.class);
        RuptureScaling rupScaling = readEnum(RUPTURE_SCALING, atts, RuptureScaling.class);
        double strike = readDouble(STRIKE, atts);

        sourceBuilder
            .depthMap(depthMap, type)
            .maxDepth(maxDepth, type)
            .mechs(mechMap)
            .ruptureScaling(rupScaling);

        // first validate strike by setting it in builder
        sourceBuilder.strike(strike);
        // then possibly override type if strike is set
        PointSourceType type = config.pointSourceType;
        if (!Double.isNaN(strike)) {
          type = PointSourceType.FIXED_STRIKE;
        }
        sourceBuilder.sourceType(type);
        if (log.isLoggable(FINE)) {
          log.fine("     Depths: " + depthMap);
          log.fine("  Max depth: " + maxDepth);
          log.fine("Focal mechs: " + mechMap);
          log.fine("Rup scaling: " + rupScaling);
          log.fine("     Strike: " + strike);
          String typeOverride = (type != config.pointSourceType) ? " (" +
              config.pointSourceType + " overridden)" : "";
          log.fine("Source type: " + type + typeOverride);
        }
        break;

      case INCREMENTAL_MFD:
        if (parsingDefaultMFDs) {
          mfdHelperBuilder.addDefault(atts);
          break;
        }
        sourceBuilder.mfd(buildMfd(atts));
        break;

      case BORDER:
        readingBorder = true;
        borderBuilder = new StringBuilder();
        break;

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

    switch (e) {

      case DEFAULT_MFDS:
        parsingDefaultMFDs = false;
        mfdHelper = mfdHelperBuilder.build();
        break;

      case BORDER:
        readingBorder = false;
        sourceBuilder.border(LocationList.fromString(borderBuilder.toString()));
        break;

      case SOURCE:
        AreaSource source = sourceBuilder.build();
        sourceSetBuilder.source(source);

        if (log.isLoggable(FINE)) {
          log.fine("       Size: " + source.size());
          log.finer("  Mag count: " + source.depthModel.magMaster.size());
          log.finer(" Mag master: " + source.depthModel.magMaster);
          log.finer("  MFD index: " + source.depthModel.magDepthIndices);
          log.finer("     Depths: " + source.depthModel.magDepthDepths);
          log.finer("    Weights: " + source.depthModel.magDepthWeights);
          log.fine("");
        }
        break;

      case AREA_SOURCE_SET:

        sourceSet = sourceSetBuilder.build();
        break;
    }
  }

  @Override
  public void characters(char ch[], int start, int length) throws SAXException {
    if (readingBorder) {
      borderBuilder.append(ch, start, length);
    }
  }

  @Override
  public void setDocumentLocator(Locator locator) {
    this.locator = locator;
  }

  /*
   * TODO Area sources should support multiple different mfds TODO Currently
   * taking only the first entry in defaults
   */

  private IncrementalMfd buildMfd(Attributes atts) {
    MfdType type = readEnum(TYPE, atts, MfdType.class);

    switch (type) {
      case GR:
        MfdHelper.GR_Data grData = mfdHelper.grData(atts).get(0);
        int nMagGR = Mfds.magCount(grData.mMin, grData.mMax, grData.dMag);
        IncrementalMfd mfdGR = Mfds.newGutenbergRichterMFD(grData.mMin, grData.dMag,
            nMagGR, grData.b, 1.0);
        mfdGR.scaleToIncrRate(grData.mMin, Mfds.incrRate(grData.a, grData.b, grData.mMin) *
            grData.weight);
        return mfdGR;

      case INCR:
        MfdHelper.IncrData incrData = mfdHelper.incrementalData(atts).get(0);
        IncrementalMfd mfdIncr = Mfds.newIncrementalMFD(incrData.mags,
            Data.multiply(incrData.weight, incrData.rates));
        return mfdIncr;

      case SINGLE:
        MfdHelper.SingleData singleData = mfdHelper.singleData(atts).get(0);
        return Mfds.newSingleMFD(singleData.m, singleData.rate * singleData.weight,
            singleData.floats);

      case GR_TAPER:
        MfdHelper.TaperData taperData = mfdHelper.taperData(atts).get(0);
        int nMagTaper = Mfds.magCount(taperData.mMin, taperData.mMax, taperData.dMag);
        IncrementalMfd mfdTaper = Mfds.newTaperedGutenbergRichterMFD(taperData.mMin,
            taperData.dMag, nMagTaper, taperData.a, taperData.b, taperData.cMag,
            taperData.weight);
        return mfdTaper;

      default:
        throw new IllegalStateException(type + " not yet implemented");

    }
  }
}
