package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.eq.forecast.SourceAttribute.*;
import static org.opensha.util.Parsing.readBoolean;
import static org.opensha.util.Parsing.readDouble;
import static org.opensha.util.Parsing.readEnum;
import static org.opensha.util.Parsing.readString;
import static java.util.logging.Level.*;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha.eq.fault.scaling.MagScalingRelationship;
import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.geo.LocationList;
import org.opensha.mfd.GutenbergRichterMFD;
import org.opensha.mfd.IncrementalMFD;
import org.opensha.mfd.MFD_Type;
import org.opensha.mfd.MFDs;
import org.opensha.util.Logging;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/*
 * Non-validating subduction source parser. SAX parser 'Attributes' are stateful and
 * cannot be stored. This class is not thread safe.
 * 
 * @author Peter Powers
 */
class SubductionSourceParser extends DefaultHandler {

	private static final Logger log = Logging.create(SubductionSourceParser.class);
	private final SAXParser sax;

	private Locator locator;

	private SubductionSourceSet.Builder sourceSetBuilder;
	private SubductionSourceSet sourceSet;

	private MagScalingRelationship msr;

	private SubductionSource.Builder sourceBuilder;
	
	// Traces are the only text content in source files
	private boolean readingTrace = false;
	private StringBuilder traceBuilder = null;
	
	
	private SubductionSourceParser(SAXParser sax) {
		this.sax = sax;
	}
	
	static SubductionSourceParser create(SAXParser sax) {
		return new SubductionSourceParser(checkNotNull(sax));
	}
	
	SubductionSourceSet parse(File f) throws SAXException, IOException {
		sax.parse(f, this);
		checkState(sourceSet.size() > 0, "SubductionSourceSet is empty");
		return sourceSet;
	}
	
	@Override
	@SuppressWarnings("incomplete-switch")
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {

		SourceElement e = null;
		try {
			e = SourceElement.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">",
				locator, iae);
		}

		try {
			switch (e) {
	
				case SUBDUCTION_SOURCE_SET:
					String name = readString(NAME, atts);
					double weight = readDouble(WEIGHT, atts);
					sourceSetBuilder = new SubductionSourceSet.Builder();
					sourceSetBuilder.name(name);
					sourceSetBuilder.weight(weight);
					if (log.isLoggable(INFO)) {
						log.info("Building interface set: " + name + " weight=" + weight);
					}
					break;
	
				case SOURCE_PROPERTIES:
					MagScalingType msrType = readEnum(MAG_SCALING, atts, MagScalingType.class);
					sourceSetBuilder.magScaling(msrType);
					msr = msrType.instance();
					sourceSet = sourceSetBuilder.buildSubductionSet();
					break;

				case SOURCE:
					String srcName = readString(NAME, atts);
					sourceBuilder = new SubductionSource.Builder();
					sourceBuilder.name(srcName);
					sourceBuilder.magScaling(msr);
					if (log.isLoggable(INFO)) log.info("Building: " + srcName);
					break;
		
				case MAG_FREQ_DIST:
					sourceBuilder.mfd(buildMFD(atts));
					break;
	
				case GEOMETRY:
					sourceBuilder.rake(readDouble(RAKE, atts));
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
			throw new SAXParseException("Error parsing <" + qName + ">",
				locator, ex);
		}
	}

	@Override
	@SuppressWarnings("incomplete-switch")
	public void endElement(String uri, String localName, String qName)
			throws SAXException {

		SourceElement e = null;
		try {
			e = SourceElement.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">",
				locator, iae);
		}

		try {
			switch (e) {
	
				case TRACE:
					readingTrace = false;
					sourceBuilder.trace(LocationList.fromString(traceBuilder.toString()));
					break;
					
				case LOWER_TRACE:
					readingTrace = false;
					sourceBuilder.lowerTrace(LocationList.fromString(traceBuilder.toString()));
					break;

				case SOURCE:
					sourceSet.add(sourceBuilder.buildSubductionSource());
					break;
					
			}
			
		} catch (Exception ex) {
			throw new SAXParseException("Error parsing <" + qName + ">",
				locator, ex);
		}
	}

	@Override
	public void characters(char ch[], int start, int length)
			throws SAXException {
		if (readingTrace) traceBuilder.append(ch, start, length);
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}
	
	private static IncrementalMFD buildMFD(Attributes atts) {
		// TODO revisit, clean, and handle exceptions
		MFD_Type type = MFD_Type.valueOf(atts.getValue("type"));
		switch (type) {
			case GR:
				return buildGR(atts);
			case INCR:
				throw new UnsupportedOperationException(
					"INCR not yet implemented");
			case SINGLE:
				return buildSingle(atts);
			case GR_TAPER:
				throw new UnsupportedOperationException(
					"GR_TAPER not yet implemented");
			default:
				throw new IllegalStateException(
					"Unhandled MFD type: " + type);
		}
	}

	/*
	 * Builds GR MFD. Method will throw IllegalStateException if attribute
	 * values yield an MFD with no magnitude bins.
	 */
	private static IncrementalMFD buildGR(Attributes atts) {
		double a = readDouble(A, atts);
		double b = readDouble(B, atts);
		double mMin = readDouble(M_MIN, atts);
		double mMax = readDouble(M_MAX, atts);
		double dMag = readDouble(D_MAG, atts);
		double weight = readDouble(WEIGHT, atts);

		int nMag = MFDs.magCount(mMin, mMax, dMag);
		checkState(nMag > 0, "GR MFD with no mags");
		double tmr = MFDs.totalMoRate(mMin, nMag, dMag, a, b);

		if (log.isLoggable(INFO)) log.info("MFD: GR");
		
		GutenbergRichterMFD mfd = MFDs.newGutenbergRichterMoBalancedMFD(mMin,
			dMag, nMag, b, tmr * weight);

		if (log.isLoggable(FINE)) {
			log.fine(new StringBuilder().append(mfd.getMetadataString())
				.toString());
		}
		return mfd;
	}

	/* Builds single MFD */
	private static IncrementalMFD buildSingle(Attributes atts) {
		
		double a = readDouble(A, atts);
		double m = readDouble(M, atts);
		boolean floats = readBoolean(FLOATS, atts);
		double weight = readDouble(WEIGHT, atts);

		if (log.isLoggable(INFO)) log.info("MFD: SINGLE");

		IncrementalMFD mfd = MFDs.newSingleMoBalancedMFD(m, weight * a, floats);
		
		if (log.isLoggable(FINE)) {
			log.fine(new StringBuilder().append(mfd.getMetadataString())
				.toString());
		}
		return mfd;
	}

}
