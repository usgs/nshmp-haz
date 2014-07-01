package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.eq.forecast.SourceAttribute.ASEIS;
import static org.opensha.eq.forecast.SourceAttribute.DEPTH;
import static org.opensha.eq.forecast.SourceAttribute.DIP;
import static org.opensha.eq.forecast.SourceAttribute.DIP_DIR;
import static org.opensha.eq.forecast.SourceAttribute.INDEX;
import static org.opensha.eq.forecast.SourceAttribute.LOWER_DEPTH;
import static org.opensha.eq.forecast.SourceAttribute.NAME;
import static org.opensha.util.Parsing.readDouble;
import static org.opensha.util.Parsing.readString;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha.eq.fault.surface.GriddedSurface;
import org.opensha.eq.fault.surface.GriddedSurfaceWithSubsets;
import org.opensha.geo.LocationList;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.Lists;

/*
 * Non-validating indexed fault section parser. SAX parser 'Attributes' are
 * stateful and cannot be stored. This class is not thread safe. The List
 * returned by parse() is mutable as it will be made immutable when ultimately
 * passed to IndexedFaultSourceSet.Builder
 * 
 * @author Peter Powers
 */
@SuppressWarnings("incomplete-switch")
class IndexedSectionParser extends DefaultHandler {

	private final Logger log = Logger.getLogger(IndexedFaultParser.class.getName());
	private final SAXParser sax;
	private boolean used = false;

	private Locator locator;

	private List<GriddedSurface> sections;
	private GriddedSurfaceWithSubsets.Builder surfaceBuilder;

	// Traces are the only text content in source files
	private boolean readingTrace = false;
	private StringBuilder traceBuilder = null;

	private IndexedSectionParser(SAXParser sax) {
		this.sax = sax;
	}

	static IndexedSectionParser create(SAXParser sax) {
		return new IndexedSectionParser(checkNotNull(sax));
	}

	// TODO can we just return RuptureSurface? are grid details necessary downstream?
	List<GriddedSurface> parse(InputStream in) throws SAXException, IOException {
		checkState(!used, "This parser has expired");
		sax.parse(in, this);
		checkState(sections.size() > 0, "Section surface list is empty");
		used = true;
		return sections;
	}

	@Override public void startElement(String uri, String localName, String qName, Attributes atts)
			throws SAXException {

		SourceElement e = null;
		try {
			e = SourceElement.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
		}

		try {
			switch (e) {

				case INDEXED_FAULT_SECTIONS:
					sections = Lists.newArrayList();
					String setName = readString(NAME, atts);
					log.info("");
					log.info("Section set: " + setName);
					break;

				case SECTION:
					surfaceBuilder = GriddedSurfaceWithSubsets.builder();
					String sectionName = readString(NAME, atts);
					String sectionIndex = readString(INDEX, atts);
					log.finer("    Section: [" + sectionIndex + "] " + sectionName);
					break;

				case GEOMETRY:
					// @formatter:off
					double aseis = readDouble(ASEIS, atts);
					double depth = readDouble(DEPTH, atts);
					double lowerDepth = readDouble(LOWER_DEPTH, atts);
					depth += aseis * (lowerDepth - depth);
					surfaceBuilder.depth(depth)
						.lowerDepth(lowerDepth)
						.dip(readDouble(DIP, atts))
						.dipDir(readDouble(DIP_DIR, atts));
					break;
					// @formatter:on

				case TRACE:
					readingTrace = true;
					traceBuilder = new StringBuilder();
					break;
			}

		} catch (Exception ex) {
			throw new SAXParseException("Error parsing <" + qName + ">", locator, ex);
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

		try {
			switch (e) {

				case TRACE:
					readingTrace = false;
					surfaceBuilder.trace(LocationList.fromString(traceBuilder.toString()));
					break;

				case SECTION:
					sections.add(surfaceBuilder.build());
					break;

			}

		} catch (Exception ex) {
			throw new SAXParseException("Error parsing <" + qName + ">", locator, ex);
		}
	}

	@Override public void characters(char ch[], int start, int length) throws SAXException {
		if (readingTrace) traceBuilder.append(ch, start, length);
	}

	@Override public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}

}
