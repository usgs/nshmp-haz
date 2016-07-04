package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;

import static org.opensha2.internal.Parsing.readDouble;
import static org.opensha2.internal.Parsing.readEnum;
import static org.opensha2.internal.Parsing.readInt;
import static org.opensha2.internal.Parsing.readString;
import static org.opensha2.internal.SourceAttribute.DEPTH;
import static org.opensha2.internal.SourceAttribute.DIP;
import static org.opensha2.internal.SourceAttribute.ID;
import static org.opensha2.internal.SourceAttribute.M;
import static org.opensha2.internal.SourceAttribute.NAME;
import static org.opensha2.internal.SourceAttribute.RAKE;
import static org.opensha2.internal.SourceAttribute.RATE;
import static org.opensha2.internal.SourceAttribute.RUPTURE_SCALING;
import static org.opensha2.internal.SourceAttribute.TYPE;
import static org.opensha2.internal.SourceAttribute.WEIGHT;
import static org.opensha2.internal.SourceAttribute.WIDTH;

import org.opensha2.eq.fault.surface.RuptureScaling;
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

import com.google.common.math.DoubleMath;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

/*
 * Non-validating cluster source parser. SAX parser 'Attributes' are stateful
 * and cannot be stored. This class is not thread safe.
 *
 * NOTE: Cluster sources only support SINGLE magnitude frequency distributions
 * and do not support epistemic or aleatory uncertainty on magnitude. These
 * restrictions could be lifted in the future.
 *
 * NOTE: A ClusterSource wraps a FaultSourceSet that it delegates to for various
 * methods.
 *
 * @author Peter Powers
 */
@SuppressWarnings("incomplete-switch")
class ClusterParser extends DefaultHandler {

  private final Logger log = Logger.getLogger(ClusterParser.class.getName());
  private final SAXParser sax;
  private boolean used = false;

  private Locator locator;

  private GmmSet gmmSet;

  private ModelConfig config;

  private ClusterSourceSet sourceSet;
  private ClusterSourceSet.Builder clusterSetBuilder;
  private ClusterSource.Builder clusterBuilder;
  private FaultSourceSet.Builder faultSetBuilder;
  private FaultSource.Builder faultBuilder;
  private double clusterRate;

  // required, but not used, by FaultSources
  private RuptureScaling rupScaling;

  // Default MFD data
  private boolean parsingDefaultMFDs = false;

  // Traces are the only text content in source files
  private boolean readingTrace = false;
  private StringBuilder traceBuilder = null;

  private ClusterParser(SAXParser sax) {
    this.sax = sax;
  }

  static ClusterParser create(SAXParser sax) {
    return new ClusterParser(checkNotNull(sax));
  }

  ClusterSourceSet parse(InputStream in, GmmSet gmmSet, ModelConfig config) throws SAXException,
      IOException {
    checkState(!used, "This parser has expired");
    this.gmmSet = gmmSet;
    this.config = config;
    sax.parse(in, this);
    checkState(sourceSet.size() > 0, "ClusterSourceSet is empty");
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

        case CLUSTER_SOURCE_SET:
          String name = readString(NAME, atts);
          int id = readInt(ID, atts);
          double weight = readDouble(WEIGHT, atts);
          clusterSetBuilder = new ClusterSourceSet.Builder();
          clusterSetBuilder
              .name(name)
              .id(id)
              .weight(weight)
              .gmms(gmmSet);
          if (log.isLoggable(FINE)) {
            log.fine("");
            log.fine("       Name: " + name);
            log.fine("     Weight: " + weight);
          }
          break;

        case DEFAULT_MFDS:
          parsingDefaultMFDs = true;
          break;

        case MAG_UNCERTAINTY:
          // we could just ignore <MagUncertainty> bu favor
          // explicitely
          // NOT supporting it for now.
          throw new IllegalStateException(
              "Cluster sources do no support magnitude uncertainty");

        case SOURCE_PROPERTIES:
          // this isn't really needed for cluster sources,
          // but nested faults can't be built without it
          rupScaling = readEnum(RUPTURE_SCALING, atts, RuptureScaling.class);
          log.fine("Rup scaling: " + rupScaling + " (not used)");
          break;

        case CLUSTER:
          String clustName = readString(NAME, atts);
          double clustWeight = readDouble(WEIGHT, atts);
          int clustId = readInt(ID, atts);
          clusterBuilder = new ClusterSource.Builder();
          clusterBuilder.rate(clusterRate);

          faultSetBuilder = new FaultSourceSet.Builder();
          faultSetBuilder
              .name(clustName)
              .id(clustId)
              .weight(clustWeight)
              .gmms(gmmSet);
          if (log.isLoggable(FINE)) {
            log.fine("");
            log.fine("    Cluster: " + clustName);
            log.fine("     Weight: " + clustWeight);
            log.fine("   Ret. per: " + Math.rint(1.0 / clusterRate));
          }
          break;

        case SOURCE:
          String srcName = readString(NAME, atts);
          int srcId = readInt(ID, atts);
          faultBuilder = new FaultSource.Builder()
              .name(srcName)
              .id(srcId)
              .ruptureScaling(rupScaling)
              .ruptureFloating(config.ruptureFloating)
              .ruptureVariability(config.ruptureVariability)
              .surfaceSpacing(config.surfaceSpacing);
          log.finer("      Fault: " + srcName);
          break;

        case INCREMENTAL_MFD:
          if (parsingDefaultMFDs) {
            checkState(readEnum(TYPE, atts, MfdType.class) == MfdType.SINGLE,
                "Only SINGLE MFDs are supported by cluster sources");
            clusterRate = readDouble(RATE, atts);
            break;
          }
          faultBuilder.mfd(buildMFD(atts));
          break;

        case GEOMETRY:
          faultBuilder.depth(readDouble(DEPTH, atts))
              .dip(readDouble(DIP, atts))
              .width(readDouble(WIDTH, atts))
              .rake(readDouble(RAKE, atts));
          break;

        case TRACE:
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
          break;

        case TRACE:
          readingTrace = false;
          faultBuilder.trace(LocationList.fromString(traceBuilder.toString()));
          break;

        case SOURCE:
          FaultSource faultSource = faultBuilder.buildFaultSource();
          checkMagVariantWeights(faultSource);
          faultSetBuilder.source(faultSource);
          break;

        case CLUSTER:
          clusterBuilder.faults(faultSetBuilder.buildFaultSet());
          clusterSetBuilder.source(clusterBuilder.buildClusterSource());
          break;

        case CLUSTER_SOURCE_SET:
          log.fine("");
          sourceSet = clusterSetBuilder.buildClusterSet();
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

  private IncrementalMfd buildMFD(Attributes atts) {
    MfdType type = readEnum(TYPE, atts, MfdType.class);
    switch (type) {
      case SINGLE:
        IncrementalMfd mfd = Mfds.newSingleMFD(readDouble(M, atts),
            readDouble(WEIGHT, atts), false);
        log.finer("   MFD type: SINGLE");
        if (log.isLoggable(FINEST)) {
          log.finest(mfd.getMetadataString());
        }
        return mfd;
      default:
        throw new IllegalStateException(type + " not yet implemented");
    }
  }

  /*
   * This method ensures that the weights of the mag variants of a source in a
   * cluster (wherein a source weight is stored in the rate field) sum to 1.
   */
  private static void checkMagVariantWeights(FaultSource source) {
    double totalWeight = 0.0;
    for (IncrementalMfd mfd : source.mfds) {
      totalWeight += mfd.getMinY();
    }
    checkState(
        DoubleMath.fuzzyEquals(totalWeight, 1.0, 0.00001),
        "Magnitude variant weights (%s) in a cluster source must sum to 1.0",
        totalWeight);
  }

}
