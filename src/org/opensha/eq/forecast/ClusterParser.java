package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.logging.Level.INFO;
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
import org.opensha.mfd.IncrementalMFD;
import org.opensha.mfd.MFD_Type;
import org.opensha.mfd.MFDs;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/*
 * Non-validating cluster source parser. SAX parser 'Attributes' are stateful
 * and cannot be stored. This class is not thread safe.
 * 
 * @author Peter Powers
 */
@SuppressWarnings("incomplete-switch")
class ClusterParser extends DefaultHandler {

	private final Logger log = Logger.getLogger(ClusterParser.class.getName());
	private final SAXParser sax;
	private boolean used = false;

	private Locator locator;

	private ClusterSourceSet sourceSet;
	private ClusterSourceSet.Builder sourceSetBuilder;
	private ClusterSource.Builder clusterBuilder;
	private FaultSource.Builder faultBuilder;
	private double clusterRate;

	// required, but not used, by FaultSources
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

	ClusterSourceSet parse(InputStream in) throws SAXException, IOException {
		checkState(!used, "This parser has expired");
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
					sourceSetBuilder = new ClusterSourceSet.Builder();
					sourceSetBuilder.name(name);
					sourceSetBuilder.weight(weight);
					if (log.isLoggable(INFO)) {
						log.info("Building cluster set: " + name + " weight=" + weight);
					}
					break;

				case MAG_FREQ_DIST_REF:
					parsingDefaultMFDs = true;
					break;

				case SOURCE_PROPERTIES:
					// this isn't really needed for cluster sources as yet,
					// but nested faults can't be built without it
					MagScalingType msrType = readEnum(MAG_SCALING, atts, MagScalingType.class);
					sourceSetBuilder.magScaling(msrType);
					msr = msrType.instance();
					break;

				case CLUSTER:
					clusterBuilder = new ClusterSource.Builder();
					String clustName = readString(NAME, atts);
					double clustWeight = readDouble(WEIGHT, atts);
					clusterBuilder.name(clustName);
					clusterBuilder.weight(clustWeight);
					clusterBuilder.rate(clusterRate);
					if (log.isLoggable(INFO)) {
						log.info("Creating cluster: " + clustName + " weight=" + clustWeight +
							" retPer=" + Math.rint(1.0 / clusterRate));
					}
					break;

				case SOURCE:
					String srcName = readString(NAME, atts);
					faultBuilder = new FaultSource.Builder();
					faultBuilder.name(srcName);
					faultBuilder.magScaling(msr);
					if (log.isLoggable(INFO)) {
						log.info("      with fault: " + srcName);
					}
					break;

				case MAG_FREQ_DIST:
					if (parsingDefaultMFDs) {
						clusterRate = readDouble(A, atts);
						break;
					}
					faultBuilder.mfd(buildMFD(atts));
					break;

				case GEOMETRY:
					faultBuilder.depth(readDouble(DEPTH, atts));
					faultBuilder.dip(readDouble(DIP, atts));
					faultBuilder.width(readDouble(WIDTH, atts));
					faultBuilder.rake(readDouble(RAKE, atts));
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
					clusterBuilder.fault(faultBuilder.buildFaultSource());
					break;

				case CLUSTER:
					sourceSetBuilder.source(clusterBuilder.buildClusterSource());
					break;

				case CLUSTER_SOURCE_SET:
					sourceSet = sourceSetBuilder.buildClusterSet();
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

	private IncrementalMFD buildMFD(Attributes atts) {
		MFD_Type type = readEnum(TYPE, atts, MFD_Type.class);
		switch (type) {
			case SINGLE:
				return MFDs.newSingleMFD(readDouble(M, atts), readDouble(WEIGHT, atts), false);
			default:
				throw new IllegalStateException(type + " not yet implemented");
		}
	}

}
