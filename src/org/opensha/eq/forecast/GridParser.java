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
import org.opensha.mfd.IncrementalMFD;
import org.opensha.mfd.MFD_Type;
import org.opensha.mfd.MFDs;
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
	private MFD_Helper mfdHelper;

	private GmmSet gmmSet;

	private GridSourceSet sourceSet;
	private GridSourceSet.Builder sourceSetBuilder;

	// Node locations are the only text content in source files
	private boolean readingLoc = false;
	private StringBuilder locBuilder = null;

	// Per-node MFD
	IncrementalMFD nodeMFD = null;

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

		switch (e) {

			case GRID_SOURCE_SET:
				String name = readString(NAME, atts);
				double weight = readDouble(WEIGHT, atts); // need this to scale
															// MFDs
				sourceSetBuilder = new GridSourceSet.Builder();
				sourceSetBuilder.name(name);
				sourceSetBuilder.weight(weight);
				sourceSetBuilder.gmms(gmmSet);
				if (log.isLoggable(FINE)) {
					log.fine("");
					log.fine("       Name: " + name);
					log.fine("     Weight: " + weight);
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
				String depthMapStr = readString(MAG_DEPTH_MAP, atts);
				NavigableMap<Double, Map<Double, Double>> depthMap = stringToValueValueWeightMap(depthMapStr);
				sourceSetBuilder.depthMap(depthMap);
				String mechMapStr = readString(FOCAL_MECH_MAP, atts);
				Map<FocalMech, Double> mechMap = stringToEnumWeightMap(mechMapStr, FocalMech.class);
				sourceSetBuilder.mechs(mechMap);
				MagScalingType magScaling = readEnum(MAG_SCALING, atts, MagScalingType.class);
				sourceSetBuilder.magScaling(magScaling);
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
				nodeMFD = processNode(atts, sourceSetBuilder.weight);
				break;
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

		switch (e) {

			case MAG_FREQ_DIST_REF:
				parsingDefaultMFDs = false;
				break;

			case NODE:
				readingLoc = false;
				sourceSetBuilder.location(Location.fromString(locBuilder.toString()), nodeMFD);
				nodeMFD = null;
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

			default:
				throw new IllegalStateException(type + " not yet implemented");

		}
	}

}
