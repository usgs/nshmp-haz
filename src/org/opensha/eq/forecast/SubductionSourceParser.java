package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import static org.opensha.eq.forecast.MFD_Attribute.*;
import static org.opensha.util.Parsing.readBoolean;
import static org.opensha.util.Parsing.readDouble;
import static org.opensha.util.Parsing.readString;
import static org.opensha.util.Parsing.toMap;

import static java.util.logging.Level.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha.eq.Magnitudes;
import org.opensha.geo.LocationList;
import org.opensha.mfd.GaussianMFD;
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

import com.google.common.collect.Lists;

/*
 * Non-validating fault source parser; results are undefined for multiple
 * entries for which there should only be singletons (e.g. MagUncertainty)
 * 
 * Overriden handler methods check validity of XML
 * ANy Builder or equivalent used to create FaultSources should do value checking
 * 
 * NOTE: parser 'Attributes' are reused and cannot be stored
 * NOTE: not thread safe
 * 
 * @author Peter Powers
 * 
 * TODO still need to check exception throwing/handling for everything above
 * MagUncertainty closing tag.
 * 
 */
class SubductionSourceParser extends DefaultHandler {

	private static final Logger log = Logging.create(SubductionSourceParser.class);
	private static final String LF = LINE_SEPARATOR.value();
	private final SAXParser sax;

	private Locator locator;

	private SubductionSourceSet sources;

	// This will build individual sources in a SubductionSourceSet
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
		return sources;
	}
	
	
	// TODO wrap all operations in a sSAXParseException so it gets passed up to logger in Loader
	
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
					// TODO file name and weight not currently processed
					sources = new SubductionSourceSet();
					break;
	
				case SUBDUCTION_SOURCE:
					String name = readString("name", atts);
					sourceBuilder = new SubductionSource.Builder();
					sourceBuilder.name(name);
					if (log.isLoggable(INFO)) log.info("Building: " + name);
					break;
		
				case MAG_FREQ_DIST:
					sourceBuilder.mfd(buildMFD(atts));
					break;
	
				case GEOMETRY:
					sourceBuilder.rake(readDouble("rake", atts));
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
					sourceBuilder.upperTrace(LocationList
						.fromString(traceBuilder.toString()));
					break;
					
				case LOWER_TRACE:
					readingTrace = false;
					sourceBuilder.lowerTrace(LocationList
						.fromString(traceBuilder.toString()));
					break;

				case SUBDUCTION_SOURCE:
					sources.add(sourceBuilder.build());
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
		double weight = readDouble(WEIGHT, atts, 1.0);

		if (log.isLoggable(INFO)) log.info("MFD: SINGLE");

		IncrementalMFD mfd = MFDs.newSingleMoBalancedMFD(m, weight * a);
		
		if (log.isLoggable(FINE)) {
			log.fine(new StringBuilder().append(mfd.getMetadataString())
				.toString());
		}
		return mfd;
	}

}
