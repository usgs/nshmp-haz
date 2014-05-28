package org.opensha.eq.forecast;

import static org.opensha.util.Parsing.*;
import static org.opensha.eq.forecast.SourceAttribute.*;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.logging.Level.*;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha.eq.fault.FocalMech;
import org.opensha.geo.Location;
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
 * Non-validating grid source parser.
 * 
 * NOTE: SAX parser 'Attributes' are stateful and cannot be stored
 * NOTE: class is not thread safe
 * 
 * @author Peter Powers
 */
class GridSourceParser extends DefaultHandler {

	private static final Logger log = Logging.create(GridSourceParser.class);
	private final SAXParser sax;

	private Locator locator;

	private GridSourceSet sources;

// TODO clean
	//	// Data applying to all sources
//	private Map<MFD_Type, MFD_Data> mfdDataMap = null;

	// Default MFD data
	private boolean parsingDefaultMFDs = false;
	private MFD_Helper mfdHelper;

	// This will build an entire GridSourceSet
	private GridSourceSet.Builder sourceBuilder;
	
	// Node locations are the only text content in source files
	private boolean readingLoc = false;
	private StringBuilder locBuilder = null;
	
	// Per-node MFD
	IncrementalMFD nodeMFD = null;
	
	
	private GridSourceParser(SAXParser sax) {
		this.sax = checkNotNull(sax);
		sources = new GridSourceSet();
	}
	
	static GridSourceParser create(SAXParser sax) {
		return new GridSourceParser(sax);
	}
	
	GridSourceSet parse(File f) throws SAXException, IOException {
		sax.parse(f, this);
		return sources;
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

		switch (e) {

			case GRID_SOURCE_SET:
				String name = readString(NAME, atts);
				double weight = readDouble(WEIGHT, atts);
				sourceBuilder = new GridSourceSet.Builder();
				sourceBuilder.name(name);
				if (log.isLoggable(INFO)) {
					log.info("Building grid set: " + name + " weight=" + weight);
				}
				break;
				
			case MAG_FREQ_DIST_REF:
				mfdHelper = MFD_Helper.create();
				parsingDefaultMFDs = true;
				break;
				
			case MAG_FREQ_DIST:
				if (parsingDefaultMFDs) mfdHelper.addDefault(atts);
				break;
				
			case SOURCE_PROPERTIES:
				sourceBuilder.depthMap(stringToValueValueWeightMap(atts.getValue("depthMap")));
				sourceBuilder.mechs(stringToEnumWeightMap(atts.getValue("mechs"), FocalMech.class));
				break;
				
			case NODE:
				readingLoc = true;
				locBuilder = new StringBuilder();
				nodeMFD = processNode(atts);
				break;
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

		switch (e) {

			case MAG_FREQ_DIST_REF:
				parsingDefaultMFDs = false;
				break;

			case NODE:
				readingLoc = false;
				sourceBuilder.location(Location.fromString(locBuilder.toString()), nodeMFD);
				break;
		}
	}

	@Override
	public void characters(char ch[], int start, int length)
			throws SAXException {
		if (readingLoc) locBuilder.append(ch, start, length);
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}
	
	private IncrementalMFD processNode(Attributes atts) {
		MFD_Type type = MFD_Type.valueOf(atts.getValue("type"));
		
		// TODO need to implement MFD weight consideration
		
		switch (type) {
			case GR:
				MFD_Helper.GR_Data grData = mfdHelper.getGR(atts);
				int nMag = MFDs.magCount(grData.mMin, grData.mMax, grData.dMag);
				IncrementalMFD mfdGR = MFDs.newGutenbergRichterMFD(grData.mMin, grData.dMag, nMag,
					grData.b, 1.0);
				mfdGR.scaleToIncrRate(grData.mMin, MFDs.incrRate(grData.a, grData.b, grData.mMin));
				return mfdGR;

			case INCR:
				MFD_Helper.IncrData incrData = mfdHelper.getIncremental(atts);

				// TODO must deal with magScaling and weight
				return MFDs.newIncrementalMFD(incrData.mags, incrData.rates);
				
			case SINGLE:
				MFD_Helper.SingleData singleDat = mfdHelper.getSingle(atts);
				// TODO must deal with magScaling and weight
				// TODO must validate mag and rate array lengths
				return MFDs.newSingleMFD(singleDat.m, singleDat.a);
			
			case GR_TAPER:
				throw new UnsupportedOperationException("GR_TAPER not yet implemented");
				
		}
		return null;
	}
		
}
