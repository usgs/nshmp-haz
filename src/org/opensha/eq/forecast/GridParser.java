package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.logging.Level.FINE;
import static org.opensha.eq.forecast.SourceAttribute.FOCAL_MECH_MAP;
import static org.opensha.eq.forecast.SourceAttribute.MAG_DEPTH_MAP;
import static org.opensha.eq.forecast.SourceAttribute.MAG_SCALING;
import static org.opensha.eq.forecast.SourceAttribute.NAME;
import static org.opensha.eq.forecast.SourceAttribute.STRIKE;
import static org.opensha.eq.forecast.SourceAttribute.TYPE;
import static org.opensha.eq.forecast.SourceAttribute.WEIGHT;
import static org.opensha.eq.forecast.SourceType.GRID;
import static org.opensha.util.Parsing.readDouble;
import static org.opensha.util.Parsing.readEnum;
import static org.opensha.util.Parsing.readString;
import static org.opensha.util.Parsing.stringToEnumWeightMap;
import static org.opensha.util.Parsing.stringToValueValueWeightMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.NavigableMap;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha.eq.fault.FocalMech;
import org.opensha.eq.fault.scaling.MagScalingType;
import org.opensha.geo.Location;
import org.opensha.mfd.IncrementalMfd;
import org.opensha.mfd.MfdType;
import org.opensha.mfd.Mfds;
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
@SuppressWarnings("incomplete-switch")
class GridParser extends DefaultHandler {

	private final Logger log = Logger.getLogger(GridParser.class.getName());
	private final SAXParser sax;
	private boolean used = false;

	private Locator locator;

	// Default MFD data
	private boolean parsingDefaultMFDs = false;
	private MfdHelper mfdHelper;

	private GmmSet gmmSet;

	private GridSourceSet sourceSet;
	private GridSourceSet.Builder sourceSetBuilder;

	// Node locations are the only text content in source files
	private boolean readingLoc = false;
	private StringBuilder locBuilder = null;
	
	// Per-node MFD and mechMap
	IncrementalMfd nodeMFD = null;
	Map<FocalMech, Double> nodeMechMap = null;
	
	// Used to when validating depths in magDepthMap
	SourceType type = GRID;

	private GridParser(SAXParser sax) {
		this.sax = checkNotNull(sax);
	}

	static GridParser create(SAXParser sax) {
		return new GridParser(sax);
	}

	GridSourceSet parse(InputStream in, GmmSet gmmSet) throws SAXException, IOException {
		checkState(!used, "This parser has expired");
		this.gmmSet = gmmSet;
		sax.parse(in, this);
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
		// @formatter:off
		switch (e) {

			case GRID_SOURCE_SET:
				String name = readString(NAME, atts);
				double weight = readDouble(WEIGHT, atts);
				sourceSetBuilder = new GridSourceSet.Builder()
					.name(name)
					.weight(weight);
				
				sourceSetBuilder.gmms(gmmSet);
				if (log.isLoggable(FINE)) {
					log.fine("");
					log.fine("       Name: " + name);
					log.fine("     Weight: " + weight);
				}
				break;

			case MAG_FREQ_DIST_REF:
				mfdHelper = MfdHelper.create();
				parsingDefaultMFDs = true;
				break;

			case MAG_FREQ_DIST:
				if (parsingDefaultMFDs) mfdHelper.addDefault(atts);
				break;

			case SOURCE_PROPERTIES:
				String depthMapStr = readString(MAG_DEPTH_MAP, atts);
				NavigableMap<Double, Map<Double, Double>> depthMap = stringToValueValueWeightMap(depthMapStr);
				String mechMapStr = readString(FOCAL_MECH_MAP, atts);
				Map<FocalMech, Double> mechMap = stringToEnumWeightMap(mechMapStr, FocalMech.class);
				MagScalingType magScaling = readEnum(MAG_SCALING, atts, MagScalingType.class);
				sourceSetBuilder
					.depthMap(depthMap, type)
					.mechs(mechMap)
					.magScaling(magScaling);
				double strike = readDouble(STRIKE, atts);
				sourceSetBuilder.strike(strike);
				if (log.isLoggable(FINE)) {
					log.fine("     Depths: " + depthMap);
					log.fine("Focal mechs: " + mechMap);
					log.fine("Mag scaling: " + magScaling);
					log.fine("     Strike: " + strike);
					log.fine("");
				}

				break;

			case NODE:
				readingLoc = true;
				locBuilder = new StringBuilder();
				nodeMFD = processNode(atts);
				try {
					String nodeMechMapStr = readString(FOCAL_MECH_MAP, atts);
					nodeMechMap = stringToEnumWeightMap(nodeMechMapStr, FocalMech.class);
				} catch (NullPointerException npe) {
					nodeMechMap = null;
				}
				break;
		}
		// @formatter:on
	}

	@Override public void endElement(String uri, String localName, String qName)
			throws SAXException {

		SourceElement e = null;
		try {
			e = SourceElement.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
		}

		switch (e) {

			case MAG_FREQ_DIST_REF:
				parsingDefaultMFDs = false;
				break;

			case NODE:
				readingLoc = false;
				Location loc = Location.fromString(locBuilder.toString());
				if (nodeMechMap != null) {
					sourceSetBuilder.location(loc, nodeMFD, nodeMechMap);
				} else {
					sourceSetBuilder.location(loc, nodeMFD);
				}
				nodeMFD = null;
				nodeMechMap = null;
				break;
				
			case GRID_SOURCE_SET:
				sourceSet = sourceSetBuilder.build();
				break;
				
		}
	}

	@Override public void characters(char ch[], int start, int length) throws SAXException {
		if (readingLoc) locBuilder.append(ch, start, length);
	}

	@Override public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}

	private IncrementalMfd processNode(Attributes atts) {
		MfdType type = readEnum(TYPE, atts, MfdType.class);

		switch (type) {
			case GR:
				MfdHelper.GR_Data grData = mfdHelper.getGR(atts);
				int nMag = Mfds.magCount(grData.mMin, grData.mMax, grData.dMag);
				IncrementalMfd mfdGR = Mfds.newGutenbergRichterMFD(grData.mMin, grData.dMag, nMag,
					grData.b, 1.0);
				mfdGR.scaleToIncrRate(grData.mMin, Mfds.incrRate(grData.a, grData.b, grData.mMin));
				return mfdGR;

			case INCR:
				MfdHelper.IncrData incrData = mfdHelper.getIncremental(atts);
				IncrementalMfd mfdIncr = Mfds.newIncrementalMFD(incrData.mags, incrData.rates);
				return mfdIncr;

			case SINGLE:
				MfdHelper.SingleData singleDat = mfdHelper.getSingle(atts);
				return Mfds.newSingleMFD(singleDat.m, singleDat.a, singleDat.floats);

			default:
				throw new IllegalStateException(type + " not yet implemented");

		}
	}	

}
