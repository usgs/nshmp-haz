package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;

import static org.opensha2.eq.model.SourceAttribute.DEPTH;
import static org.opensha2.eq.model.SourceAttribute.DIP;
import static org.opensha2.eq.model.SourceAttribute.ID;
import static org.opensha2.eq.model.SourceAttribute.NAME;
import static org.opensha2.eq.model.SourceAttribute.RAKE;
import static org.opensha2.eq.model.SourceAttribute.RUPTURE_SCALING;
import static org.opensha2.eq.model.SourceAttribute.WEIGHT;
import static org.opensha2.eq.model.SourceAttribute.WIDTH;
import static org.opensha2.util.Parsing.readDouble;
import static org.opensha2.util.Parsing.readEnum;
import static org.opensha2.util.Parsing.readInt;
import static org.opensha2.util.Parsing.readString;

import org.opensha2.eq.fault.surface.RuptureScaling;
import org.opensha2.eq.model.MfdHelper.GR_Data;
import org.opensha2.eq.model.MfdHelper.SingleData;
import org.opensha2.geo.LocationList;
import org.opensha2.mfd.IncrementalMfd;
import org.opensha2.mfd.MfdType;
import org.opensha2.mfd.Mfds;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

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

  InterfaceSourceSet parse(InputStream in, GmmSet gmmSet, ModelConfig config) throws SAXException,
      IOException {
    checkState(!used, "This parser has expired");
    this.gmmSet = gmmSet;
    this.config = config;
    sax.parse(in, this);
    checkState(sourceSet.size() > 0, "InterfaceSourceSet is empty");
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
    if (readingTrace) traceBuilder.append(ch, start, length);
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
      mfdList.add(buildGR(grData));
    }
    return mfdList;
  }

  private IncrementalMfd buildGR(GR_Data data) {

    int nMag = Mfds.magCount(data.mMin, data.mMax, data.dMag);
    checkState(nMag > 0, "GR MFD with no mags");
    double tmr = Mfds.totalMoRate(data.mMin, nMag, data.dMag, data.a, data.b);

    IncrementalMfd mfd = Mfds.newGutenbergRichterMoBalancedMFD(data.mMin, data.dMag, nMag,
        data.b, tmr * data.weight);
    log.finer("   MFD type: GR");
    if (log.isLoggable(FINEST)) log.finest(mfd.getMetadataString());
    return mfd;
  }

  /* Build SINGLE MFDs. */
  private List<IncrementalMfd> buildSingle(Attributes atts) {
    List<IncrementalMfd> mfdList = new ArrayList<>();
    for (SingleData singleData : mfdHelper.singleData(atts)) {
      mfdList.add(buildSingle(singleData));
    }
    return mfdList;
  }

  private IncrementalMfd buildSingle(SingleData data) {
    IncrementalMfd mfd = Mfds.newSingleMFD(data.m, data.weight * data.rate, data.floats);
    log.finer("   MFD type: SINGLE");
    if (log.isLoggable(FINEST)) log.finest(mfd.getMetadataString());
    return mfd;
  }

}
