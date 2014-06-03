package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.logging.Level.INFO;
import static org.opensha.eq.forecast.SourceAttribute.MAG_DEPTH_MAP;
import static org.opensha.eq.forecast.SourceAttribute.MAG_SCALING;
import static org.opensha.eq.forecast.SourceAttribute.FOCAL_MECH_MAP;
import static org.opensha.eq.forecast.SourceAttribute.NAME;
import static org.opensha.eq.forecast.SourceAttribute.STRIKE;
import static org.opensha.eq.forecast.SourceAttribute.TYPE;
import static org.opensha.eq.forecast.SourceAttribute.WEIGHT;
import static org.opensha.util.Parsing.readDouble;
import static org.opensha.util.Parsing.readEnum;
import static org.opensha.util.Parsing.readString;
import static org.opensha.util.Parsing.stringToEnumWeightMap;
import static org.opensha.util.Parsing.stringToValueValueWeightMap;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha.eq.fault.FocalMech;
import org.opensha.eq.fault.scaling.MagScalingType;
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
 * Non-validating grid source parser. SAX parser 'Attributes' are stateful and
 * cannot be stored. This class is not thread safe.
 * 
 * @author Peter Powers
 */
class GridSourceParser extends DefaultHandler {

	private static final Logger log = Logging.create(GridSourceParser.class);
	private final SAXParser sax;
	private Locator locator;

	private double setWeight;

	// Default MFD data
	private boolean parsingDefaultMFDs = false;
	private MFD_Helper mfdHelper;

	private GridSourceSet.Builder sourceBuilder;
	
	// Node locations are the only text content in source files
	private boolean readingLoc = false;
	private StringBuilder locBuilder = null;
	
	// Per-node MFD
	IncrementalMFD nodeMFD = null;
	
	
	private GridSourceParser(SAXParser sax) {
		this.sax = checkNotNull(sax);
	}
	
	static GridSourceParser create(SAXParser sax) {
		return new GridSourceParser(sax);
	}
	
	GridSourceSet parse(File f) throws SAXException, IOException {
		sax.parse(f, this);
		return sourceBuilder.build();
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
				String srcName = readString(NAME, atts);
				setWeight = readDouble(WEIGHT, atts); // need this to scale MFDs
				sourceBuilder = new GridSourceSet.Builder();
				sourceBuilder.name(srcName);
				sourceBuilder.weight(setWeight);
				if (log.isLoggable(INFO)) {
					log.info("Building grid set: " + srcName + " weight=" + setWeight);
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
				sourceBuilder.depthMap(stringToValueValueWeightMap(readString(MAG_DEPTH_MAP, atts)));
				String mechMap = readString(FOCAL_MECH_MAP, atts);
				sourceBuilder.mechs(stringToEnumWeightMap(mechMap, FocalMech.class));
				sourceBuilder.magScaling(readEnum(MAG_SCALING, atts, MagScalingType.class));
				sourceBuilder.strike(readDouble(STRIKE, atts));
				break;
				
			case NODE:
				readingLoc = true;
				locBuilder = new StringBuilder();
				nodeMFD = processNode(atts, setWeight);
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
				nodeMFD = null;
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
	
	private IncrementalMFD processNode(Attributes atts, double setWeight) {
		MFD_Type type = readEnum(TYPE, atts, MFD_Type.class);
		
		switch (type) {
			case GR:
				MFD_Helper.GR_Data grData = mfdHelper.getGR(atts);
				int nMag = MFDs.magCount(grData.mMin, grData.mMax, grData.dMag);
				IncrementalMFD mfdGR = MFDs.newGutenbergRichterMFD(grData.mMin, grData.dMag, nMag,
					grData.b, 1.0);
				mfdGR.scaleToIncrRate(grData.mMin, MFDs.incrRate(grData.a, grData.b, grData.mMin));
				mfdGR.scale(setWeight);
				return mfdGR;

			case INCR:
				MFD_Helper.IncrData incrData = mfdHelper.getIncremental(atts);
				IncrementalMFD mfdIncr = MFDs.newIncrementalMFD(incrData.mags, incrData.rates);
				mfdIncr.scale(setWeight);
				return mfdIncr;
				
			case SINGLE:
				MFD_Helper.SingleData singleDat = mfdHelper.getSingle(atts);
				return MFDs.newSingleMFD(singleDat.m, setWeight * singleDat.a, singleDat.floats);
			
			case GR_TAPER:
				throw new UnsupportedOperationException("GR_TAPER not yet implemented");
				
		}
		return null;
	}
		
}
