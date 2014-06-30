package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;
import static org.opensha.eq.forecast.SourceAttribute.A;
import static org.opensha.eq.forecast.SourceAttribute.DEPTH;
import static org.opensha.eq.forecast.SourceAttribute.DIP;
import static org.opensha.eq.forecast.SourceAttribute.M;
import static org.opensha.eq.forecast.SourceAttribute.MAG_SCALING;
import static org.opensha.eq.forecast.SourceAttribute.NAME;
import static org.opensha.eq.forecast.SourceAttribute.RAKE;
import static org.opensha.eq.forecast.SourceAttribute.TYPE;
import static org.opensha.eq.forecast.SourceAttribute.WEIGHT;
import static org.opensha.eq.forecast.SourceAttribute.WIDTH;
import static org.opensha.util.Parsing.readDouble;
import static org.opensha.util.Parsing.readEnum;
import static org.opensha.util.Parsing.readString;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha.eq.fault.scaling.MagScalingRelationship;
import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.geo.LocationList;
import org.opensha.mfd.IncrementalMfd;
import org.opensha.mfd.MfdType;
import org.opensha.mfd.Mfds;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/*
 * Non-validating cluster source parser. SAX parser 'Attributes' are stateful
 * and cannot be stored. This class is not thread safe.
 * 
 * NOTE: Cluster sources only support SINGLE magnitude frequency distributions
 * and do not support epistemic or aleatory uncertainty on magnitude. These
 * restrictions could be lifted in the future.
 * 
 * NOTE: A ClusterSource wraps a FaultSourceSet that is delegates to for
 * various methods.
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

	private ClusterSourceSet sourceSet;
	private ClusterSourceSet.Builder clusterSetBuilder;
	private ClusterSource.Builder clusterBuilder;
	private FaultSourceSet.Builder faultSetBuilder;
	private FaultSource.Builder faultBuilder;
	private double clusterRate;

	// required, but not used, by FaultSources
	private MagScalingType msrType;
	private MagScalingRelationship msr;

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

	ClusterSourceSet parse(InputStream in, GmmSet gmmSet) throws SAXException, IOException {
		checkState(!used, "This parser has expired");
		this.gmmSet = gmmSet;
		sax.parse(in, this);
		checkState(sourceSet.size() > 0, "ClusterSourceSet is empty");
		used = true;
		return sourceSet;
	}

	@Override public void startElement(String uri, String localName, String qName, Attributes atts)
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
					double weight = readDouble(WEIGHT, atts);
					clusterSetBuilder = new ClusterSourceSet.Builder()
						.name(name)
						.weight(weight);
					if (log.isLoggable(FINE)) {
						log.fine("");
						log.fine("       Name: " + name);
						log.fine("     Weight: " + weight);
					}
					break;

				case MAG_FREQ_DIST_REF:
					parsingDefaultMFDs = true;
					break;

				case MAG_UNCERTAINTY:
					// we could just ignore <MagUncertainty> bu favor explicitely
					// NOT supporting it for now.
					throw new IllegalStateException(
						"Cluster sources do no support magnitude uncertainty");
					
				case SOURCE_PROPERTIES:
					// this isn't really needed for cluster sources,
					// but nested faults can't be built without it
					msrType = readEnum(MAG_SCALING, atts, MagScalingType.class);
					clusterSetBuilder.magScaling(msrType);
					log.fine("Mag scaling: " + msrType + " (not used)");
					msr = msrType.instance();
					break;

				case CLUSTER:
					String clustName = readString(NAME, atts);
					double clustWeight = readDouble(WEIGHT, atts);
					clusterBuilder = new ClusterSource.Builder()
						.rate(clusterRate);
					faultSetBuilder = new FaultSourceSet.Builder()
						.name(clustName)
						.weight(clustWeight)
						.gmms(gmmSet)
						.magScaling(msrType);
					if (log.isLoggable(FINE)) {
						log.fine("");
						log.fine("    Cluster: " + clustName);
						log.fine("     Weight: " + clustWeight);
						log.fine("   Ret. per: " + Math.rint(1.0 / clusterRate));
					}
					break;

				case SOURCE:
					String srcName = readString(NAME, atts);
					faultBuilder = new FaultSource.Builder()
						.name(srcName)
						.magScaling(msr);
					log.finer("      Fault: " + srcName);
					break;

				case MAG_FREQ_DIST:
					if (parsingDefaultMFDs) {
						checkState(readEnum(TYPE, atts, MfdType.class) == MfdType.SINGLE,
							"Only SINGLE MFDs are supported by cluster sources");
						clusterRate = readDouble(A, atts);
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

	@Override public void endElement(String uri, String localName, String qName)
			throws SAXException {

		SourceElement e = null;
		try {
			e = SourceElement.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
		}

		try {
			switch (e) {

				case MAG_FREQ_DIST_REF:
					parsingDefaultMFDs = false;
					break;

				case TRACE:
					readingTrace = false;
					faultBuilder.trace(LocationList.fromString(traceBuilder.toString()));
					break;

				case SOURCE:
					faultSetBuilder.source(faultBuilder.buildFaultSource());
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

	@Override public void characters(char ch[], int start, int length) throws SAXException {
		if (readingTrace) traceBuilder.append(ch, start, length);
	}

	@Override public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}

	private IncrementalMfd buildMFD(Attributes atts) {
		MfdType type = readEnum(TYPE, atts, MfdType.class);
		switch (type) {
			case SINGLE:
				IncrementalMfd mfd = Mfds.newSingleMFD(readDouble(M, atts),
					readDouble(WEIGHT, atts), false);
				log.finer("   MFD type: SINGLE");
				if (log.isLoggable(FINEST)) log.finest(mfd.getMetadataString());
				return mfd;
			default:
				throw new IllegalStateException(type + " not yet implemented");
		}
	}

}
