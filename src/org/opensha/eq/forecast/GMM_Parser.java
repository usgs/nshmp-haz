package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha.util.Parsing.readDouble;
import static org.opensha.data.DataUtils.validateWeights;
import static org.opensha.util.Parsing.*;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opensha.gmm.GMM;
import org.opensha.gmm.GMM_Element;
import org.opensha.util.Logging;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;
import com.google.common.collect.TreeTraverser;
import com.google.common.io.Files;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
class GMM_Parser extends DefaultHandler {

	static final String FILE_NAME = "gmm.xml";

	// TODO init/use logging for exceptions OR do we pass them to Loader for logging?
	private static final Logger log = Logging.create(GMM_Parser.class);
	private final SAXParser sax;
	private Locator locator;

	private int mapCount = 0;
	private Map<GMM, Double> gmmWtMap;
	
	// there may be distance-dependent gmm reweightings
	// in which case this field will be used
	private Map<GMM, Double> gmmWtMap2;

	
	private GMM_Parser(SAXParser sax) {
		this.sax = sax;
	}
	
	static GMM_Parser create(SAXParser sax) {
		return new GMM_Parser(checkNotNull(sax));
	}

	// TODO will want to return GMM_Calculator instances methinks
	Map<GMM, Double> parse(File f) throws SAXException, IOException {
		checkArgument(checkNotNull(f).getName().equals(FILE_NAME));
		sax.parse(f, this);
		return gmmWtMap;
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {

		GMM_Element e = null;
		try {
			e = GMM_Element.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
		}

		try {
			switch (e) {
	
				case GROUND_MOTION_MODELS:
					break;
				
				case MODEL_SET:
					mapCount++;
					if (mapCount == 1) {
						gmmWtMap = Maps.newEnumMap(GMM.class);
					} else {
						gmmWtMap2 = Maps.newEnumMap(GMM.class);
					}
					break;
					
				case MODEL:
					Map<GMM, Double> currentMap = (mapCount == 1) ? gmmWtMap : gmmWtMap2;
//					currentMap.put(GMM.valueOf(atts.getValue("id")), readDouble("weight", atts));

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

		GMM_Element e = null;
		try {
			e = GMM_Element.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">",
				locator, iae);
		}

		try {
			switch (e) {
	
				case MODEL_SET:
					validateWeights(gmmWtMap.values());
					break;
	
			}
			
		} catch (Exception ex) {
			throw new SAXParseException("Error parsing <" + qName + ">",
				locator, ex);
		}
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}
		
	
	// TODO clean
	public static void main(String[] args) throws Exception {
		
		String model = "../nshmp-forecast-dev/forecasts/2008/Western US/Fault/gmm.xml";
		File gmmFile = new File(model);

		SAXParser saxParser = null;
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			saxParser = factory.newSAXParser();
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error initializing loader", e);
			System.exit(1);
		}

		GMM_Parser parser = GMM_Parser.create(saxParser);
		Map<GMM, Double> gmmMap = parser.parse(gmmFile);
		System.out.println(gmmMap);
		
		
		
	}
		
}
