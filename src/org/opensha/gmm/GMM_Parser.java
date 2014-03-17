package org.opensha.gmm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import static org.opensha.util.Parsing.readDouble;
import static org.opensha.data.DataUtils.validateWeights;
import static org.opensha.util.Parsing.*;

//import static GMM_El

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

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

	// TODO init/use logging for exceptions OR do we pass them to Loader for
	// logging?
	private static final Logger log = Logging.create(GMM_Parser.class);
	private static final String NAME = "gmm.xml";
	private final SAXParser sax;
	
	private Locator locator;
	
	private Map<GMM, Double> gmmWtMap;


	private GMM_Parser(SAXParser sax) {
		this.sax = sax;
	}
	
	static GMM_Parser create(SAXParser sax) {
		return new GMM_Parser(checkNotNull(sax));
	}

	Map<GMM, Double> parse(File f) throws SAXException, IOException {
		checkArgument(checkNotNull(f).getName().equals(NAME));
		sax.parse(f, this);
		return gmmWtMap;
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {

		GMM_Element e = null;
		try {
			e = GMM_Element.valueOf(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">",
				locator, iae);
		}

		try {
			switch (e) {
	
				case GMM_SET:
					//TODO look at EnumMap class 
					gmmWtMap = new EnumMap<GMM, Double>(GMM.class);
					break;
				
				case GMM:
					gmmWtMap.put(
						GMM.valueOf(atts.getValue("id")),
						readDouble("weight", atts));
					
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
			e = GMM_Element.valueOf(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">",
				locator, iae);
		}

		try {
			switch (e) {
	
				case GMM_SET:
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
	
	static enum GMM_Element {
		GMM_SET,
		GMM;
	}
	
	static void write(Map<GMM, Double> gmmWtMap, File out) throws 
			ParserConfigurationException,
			TransformerException {
		
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		Document doc = docBuilder.newDocument();
		Element root = doc.createElement(GMM_Element.GMM_SET.name());
		doc.appendChild(root);

		for (Entry<GMM, Double> entry : gmmWtMap.entrySet()) {
			Element e = addElement(GMM_Element.GMM, root);
			e.setAttribute("id", entry.getKey().name());
			e.setAttribute("weight", entry.getValue().toString());
		}

		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer trans = transformerFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(out);
		trans.transform(source, result);
	}
	
	public static void main(String[] args) throws Exception {
		
		//writer test
//		String out = "tmp/gmm-config/";
//		File f = new File(out, NAME);
//		
//		EnumMap<GMM, Double> gmmMap = Maps.newEnumMap(GMM.class);
//		gmmMap.put(GMM.ASK_14, 0.22);
//		gmmMap.put(GMM.BSSA_14, 0.22);
//		gmmMap.put(GMM.CB_14, 0.22);
//		gmmMap.put(GMM.CY_14, 0.22);
//		gmmMap.put(GMM.IDRISS_14, 0.12);
//		
//		write(gmmMap, f);
		
		// parser test
		
		// TODO should skip symlinks
		// traverser
		String model = "tmp/NSHMP08-noRedux/";
		File modelDir = new File(model);
		TreeTraverser<File> traverser = Files.fileTreeTraverser();
		FluentIterable<File> fIt = traverser
				.preOrderTraversal(modelDir)
				.filter(MODEL_FILE_FILTER);
		for (File f : fIt) {
			System.out.println(f.getName());
		}
		
		
	}
	
	
	private static final Predicate<File> MODEL_FILE_FILTER = new Predicate<File>() {
		@Override
		public boolean apply(File f) {
			return
				!f.isHidden() && 
				!f.getName().startsWith("~");
		}
	};
	
}
