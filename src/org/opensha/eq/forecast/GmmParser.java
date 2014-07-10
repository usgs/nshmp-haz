package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.logging.Level.FINE;
import static org.opensha.gmm.GmmAttribute.ID;
import static org.opensha.gmm.GmmAttribute.MAX_DISTANCE;
import static org.opensha.gmm.GmmAttribute.VALUES;
import static org.opensha.gmm.GmmAttribute.WEIGHT;
import static org.opensha.gmm.GmmAttribute.WEIGHTS;
import static org.opensha.util.Parsing.readDouble;
import static org.opensha.util.Parsing.readDoubleArray;
import static org.opensha.util.Parsing.readEnum;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha.gmm.Gmm;
import org.opensha.gmm.GmmElement;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;

/*
 * Non-validating gmm.xml parser. SAX parser 'Attributes' are stateful and
 * cannot be stored. This class is not thread safe.
 * 
 * Support is included for a second Gmm map for distance splits.
 * 
 * @author Peter Powers
 */
@SuppressWarnings("incomplete-switch")
class GmmParser extends DefaultHandler {

	static final String FILE_NAME = "gmm.xml";

	private final Logger log = Logger.getLogger(GmmParser.class.getName());
	private final SAXParser sax;
	private boolean used = false;
	
	private Locator locator;

	private int mapCount = 0;
	private GmmSet gmmSet;
	private GmmSet.Builder setBuilder;
	private Map<Gmm, Double> gmmWtMap;

	private GmmParser(SAXParser sax) {
		this.sax = sax;
	}

	static GmmParser create(SAXParser sax) {
		return new GmmParser(checkNotNull(sax));
	}

	GmmSet parse(InputStream in) throws SAXException, IOException {
		checkState(!used, "This parser has expired");
		sax.parse(checkNotNull(in), this);
		used = true;
		return gmmSet;
	}

	@Override public void startElement(String uri, String localName, String qName, Attributes atts)
			throws SAXException {

		GmmElement e = null;
		try {
			e = GmmElement.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
		}

		try {
			switch (e) {

				case GROUND_MOTION_MODELS:
					setBuilder = new GmmSet.Builder();
					break;

				case UNCERTAINTY:
					double[] uncValues = readDoubleArray(VALUES, atts);
					double[] uncWeights = readDoubleArray(WEIGHTS, atts);
					setBuilder.uncertainty(uncValues, uncWeights);
					log.fine("");
					log.fine("Uncertainty...");
					log.fine("     Values: " + Arrays.toString(uncValues));
					log.fine("    Weights: " + Arrays.toString(uncWeights));
					break;
					
				case MODEL_SET:
					mapCount++;
					checkState(mapCount < 3, "Only two ground motion model sets are allowed");
					gmmWtMap = Maps.newEnumMap(Gmm.class);
					double rMax = Double.NaN;
					if (mapCount == 1) {
						rMax = readDouble(MAX_DISTANCE, atts);
						setBuilder.primaryMaxDistance(rMax);
					} else {
						rMax = readDouble(MAX_DISTANCE, atts);
						setBuilder.secondaryMaxDistance(rMax);
					}
					log.fine("");
					log.fine("        Set: " + mapCount + "[rMax = " + rMax + "]");
					break;

				case MODEL:
					Gmm model = readEnum(ID, atts, Gmm.class);
					double weight = readDouble(WEIGHT, atts);
					gmmWtMap.put(model, weight);
					if (log.isLoggable(FINE)) {
						log.fine(" Model [wt]: " + Strings.padEnd(model.toString(), 44, ' ') +
							" [" + weight + "]");
					}
					break;

			}

		} catch (Exception ex) {
			throw new SAXParseException("Error parsing <" + qName + ">", locator, ex);
		}

	}

	@Override public void endElement(String uri, String localName, String qName)
			throws SAXException {

		GmmElement e = null;
		try {
			e = GmmElement.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
		}

		try {
			switch (e) {

				case MODEL_SET:
					if (mapCount == 1) {
						setBuilder.primaryModelMap(gmmWtMap);
					} else {
						setBuilder.secondaryModelMap(gmmWtMap);
					}
					log.fine("");
					break;

				case GROUND_MOTION_MODELS:
					gmmSet = setBuilder.build();
					break;

			}

		} catch (Exception ex) {
			throw new SAXParseException("Error parsing <" + qName + ">", locator, ex);
		}
	}

	@Override public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}

}
