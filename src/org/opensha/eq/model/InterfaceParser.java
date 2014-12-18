package org.opensha.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;
import static org.opensha.eq.model.SourceAttribute.A;
import static org.opensha.eq.model.SourceAttribute.B;
import static org.opensha.eq.model.SourceAttribute.DEPTH;
import static org.opensha.eq.model.SourceAttribute.DIP;
import static org.opensha.eq.model.SourceAttribute.D_MAG;
import static org.opensha.eq.model.SourceAttribute.FLOATS;
import static org.opensha.eq.model.SourceAttribute.M;
import static org.opensha.eq.model.SourceAttribute.RUPTURE_SCALING;
import static org.opensha.eq.model.SourceAttribute.M_MAX;
import static org.opensha.eq.model.SourceAttribute.M_MIN;
import static org.opensha.eq.model.SourceAttribute.NAME;
import static org.opensha.eq.model.SourceAttribute.RAKE;
import static org.opensha.eq.model.SourceAttribute.WEIGHT;
import static org.opensha.eq.model.SourceAttribute.WIDTH;
import static org.opensha.util.Parsing.readBoolean;
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
import org.opensha.mfd.GutenbergRichterMfd;
import org.opensha.mfd.IncrementalMfd;
import org.opensha.mfd.MfdType;
import org.opensha.mfd.Mfds;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

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

	private InterfaceSourceSet sourceSet;
	private InterfaceSourceSet.Builder sourceSetBuilder;
	private InterfaceSource.Builder sourceBuilder;

	private MagScalingRelationship msr;

	// Default MFD data
	private boolean parsingDefaultMFDs = false;
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

	InterfaceSourceSet parse(InputStream in, GmmSet gmmSet) throws SAXException, IOException {
		checkState(!used, "This parser has expired");
		this.gmmSet = gmmSet;
		sax.parse(in, this);
		checkState(sourceSet.size() > 0, "InterfaceSourceSet is empty");
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
			// @formatter:off
			switch (e) {

				case SUBDUCTION_SOURCE_SET:
					String name = readString(NAME, atts);
					double weight = readDouble(WEIGHT, atts);
					sourceSetBuilder = new InterfaceSourceSet.Builder()
						.name(name)
						.weight(weight)
						.gmms(gmmSet);
					if (log.isLoggable(FINE)) {
						log.fine("");
						log.fine("       Name: " + name);
						log.fine("     Weight: " + weight);
					}
					break;

				case DEFAULT_MFDS:
					mfdHelper = MfdHelper.create();
					parsingDefaultMFDs = true;
					break;

				case SOURCE_PROPERTIES:
					MagScalingType msrType = readEnum(RUPTURE_SCALING, atts, MagScalingType.class);
					sourceSetBuilder.magScaling(msrType);
					msr = msrType.instance();
					log.fine("Mag scaling: " + msrType);
					break;

				case SOURCE:
					String srcName = readString(NAME, atts);
					sourceBuilder = new InterfaceSource.Builder();
					sourceBuilder.name(srcName);
					sourceBuilder.magScaling(msr);
					log.fine("     Source: " + srcName);
					break;

				case INCREMENTAL_MFD:
					if (parsingDefaultMFDs) {
						mfdHelper.addDefault(atts);
						break;
					}
					sourceBuilder.mfd(buildMFD(atts));
					break;

				case GEOMETRY:
					sourceBuilder.rake(readDouble(RAKE, atts));
					
					/*
					 * At present, an InterfaceSource geometry may be defined
					 * with an upper trace, dip, depth and width, or and upper
					 * and lower trace. Rake is always required (above), but the
					 * three scalar parameters (dip, depth, and width) must be
					 * conditionally read. The InterfaceSource.Builder will
					 * check if either construction technique has been
					 * satisfied.
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
			// @formatter:on
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

				case DEFAULT_MFDS:
					parsingDefaultMFDs = false;
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

	@Override public void characters(char ch[], int start, int length) throws SAXException {
		if (readingTrace) traceBuilder.append(ch, start, length);
	}

	@Override public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}

	private IncrementalMfd buildMFD(Attributes atts) {
		// TODO revisit, clean, and handle exceptions
		MfdType type = MfdType.valueOf(atts.getValue("type"));
		switch (type) {
			case GR:
				return buildGR(mfdHelper.getGR(atts));
			case SINGLE:
				return buildSingle(mfdHelper.getSingle(atts));
			default:
				throw new IllegalStateException(type + " not yet implemented");
		}
	}

	/*
	 * Builds GR MFD. Method will throw IllegalStateException if attribute
	 * values yield an MFD with no magnitude bins.
	 */
	private IncrementalMfd buildGR(MfdHelper.GR_Data data) {

		int nMag = Mfds.magCount(data.mMin, data.mMax, data.dMag);
		checkState(nMag > 0, "GR MFD with no mags");
		double tmr = Mfds.totalMoRate(data.mMin, nMag, data.dMag, data.a, data.b);

		GutenbergRichterMfd mfd = Mfds.newGutenbergRichterMoBalancedMFD(data.mMin, data.dMag, nMag,
			data.b, tmr * data.weight);
		log.finer("   MFD type: GR");
		if (log.isLoggable(FINEST)) log.finest(mfd.getMetadataString());
		return mfd;
	}

	/* Builds single MFD */
	private IncrementalMfd buildSingle(MfdHelper.SingleData data) {
		IncrementalMfd mfd = Mfds.newSingleMFD(data.m, data.weight * data.a, data.floats);
		log.finer("   MFD type: SINGLE");
		if (log.isLoggable(FINEST)) log.finest(mfd.getMetadataString());
		return mfd;
	}

}
