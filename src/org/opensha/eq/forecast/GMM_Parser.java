package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.gmm.GMM_Attribute.ID;
import static org.opensha.gmm.GMM_Attribute.MAX_DISTANCE;
import static org.opensha.gmm.GMM_Attribute.WEIGHT;
import static org.opensha.util.Parsing.readDouble;
import static org.opensha.util.Parsing.readEnum;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha.gmm.GMM;
import org.opensha.gmm.GMM_Element;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.Maps;

/*
 * Non-validating gmm.xml parser. SAX parser 'Attributes' are stateful and
 * cannot be stored. This class is not thread safe.
 * 
 * Support is included for a second GMM map for distance splits.
 * 
 * @author Peter Powers
 */
@SuppressWarnings("incomplete-switch")
class GMM_Parser extends DefaultHandler {

	static final String FILE_NAME = "gmm.xml";

	private final Logger log = Logger.getLogger(GMM_Parser.class.getName());
	private final SAXParser sax;
	private boolean used = false;
	
	private Locator locator;

	private int mapCount = 0;
	private GMM_Set gmmSet;
	private GMM_Set.Builder setBuilder;
	private Map<GMM, Double> gmmWtMap;

	private GMM_Parser(SAXParser sax) {
		this.sax = sax;
	}

	static GMM_Parser create(SAXParser sax) {
		return new GMM_Parser(checkNotNull(sax));
	}

	GMM_Set parse(InputStream in) throws SAXException, IOException {
		checkState(!used, "This parser has expired");
		sax.parse(checkNotNull(in), this);
		used = true;
		return gmmSet;
	}

	@Override public void startElement(String uri, String localName, String qName, Attributes atts)
			throws SAXException {

		GMM_Element e = null;
		try {
			e = GMM_Element.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
		}

		try {
			switch (e) {

				case GROUND_MOTION_MODELS:
					setBuilder = new GMM_Set.Builder();
					break;

				case MODEL_SET:
					mapCount++;
					checkState(mapCount < 3, "Only two ground motion model sets are allowed");
					gmmWtMap = Maps.newEnumMap(GMM.class);
					if (mapCount == 1) {
						setBuilder.primaryMaxDistance(readDouble(MAX_DISTANCE, atts));
					} else {
						setBuilder.secondaryMaxDistance(readDouble(MAX_DISTANCE, atts));
					}
					log.fine("");
					log.fine("        Set: " + mapCount);
					break;

				case MODEL:
					GMM model = readEnum(ID, atts, GMM.class);
					double weight = readDouble(WEIGHT, atts);
					gmmWtMap.put(model, weight);
					log.fine(" Model [wt]: " + model + " [" + weight + "]");
					break;

			}

		} catch (Exception ex) {
			throw new SAXParseException("Error parsing <" + qName + ">", locator, ex);
		}

	}

	@Override public void endElement(String uri, String localName, String qName)
			throws SAXException {

		GMM_Element e = null;
		try {
			e = GMM_Element.fromString(qName);
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
