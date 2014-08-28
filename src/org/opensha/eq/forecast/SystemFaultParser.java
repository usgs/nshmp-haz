package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.eq.forecast.SourceAttribute.A;
import static org.opensha.eq.forecast.SourceAttribute.DEPTH;
import static org.opensha.eq.forecast.SourceAttribute.DIP;
import static org.opensha.eq.forecast.SourceAttribute.INDICES;
import static org.opensha.eq.forecast.SourceAttribute.M;
import static org.opensha.eq.forecast.SourceAttribute.NAME;
import static org.opensha.eq.forecast.SourceAttribute.RAKE;
import static org.opensha.eq.forecast.SourceAttribute.TYPE;
import static org.opensha.eq.forecast.SourceAttribute.WEIGHT;
import static org.opensha.eq.forecast.SourceAttribute.WIDTH;
import static org.opensha.util.Parsing.rangeStringToIntList;
import static org.opensha.util.Parsing.readDouble;
import static org.opensha.util.Parsing.readEnum;
import static org.opensha.util.Parsing.readString;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha.eq.fault.surface.GriddedSurface;
import org.opensha.mfd.MfdType;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/*
 * Non-validating indexed fault source parser. SAX parser 'Attributes' are
 * stateful and cannot be stored. This class is not thread safe.
 * 
 * @author Peter Powers
 */
@SuppressWarnings("incomplete-switch")
class SystemFaultParser extends DefaultHandler {

	static final String GRIDSOURCE_FILENAME = "grid_sources.xml";
	static final String RUPTURES_FILENAME = "fault_ruptures.xml";
	static final String SECTIONS_FILENAME = "fault_sections.xml";

	private final Logger log = Logger.getLogger(SystemFaultParser.class.getName());
	private final SAXParser sax;
	private boolean used = false;

	private Locator locator;

	private GmmSet gmmSet;

	private List<GriddedSurface> sections; // TODO can these RuptureSurface??
	private SystemSourceSet sourceSet;
	private SystemSourceSet.Builder sourceSetBuilder;

	// instead of building IncrementalMFD's this parser just holds onto
	// mag and rate to directly populate SystemSourceSet
	private double sourceMag;
	private double sourceRate;

	// Default MFD data
	private boolean parsingDefaultMFDs = false;
	private MfdHelper mfdHelper;

	// Traces are the only text content in source files
	private boolean readingTrace = false;
	private StringBuilder traceBuilder = null;

	private SystemFaultParser(SAXParser sax) {
		this.sax = sax;
	}

	static SystemFaultParser create(SAXParser sax) {
		return new SystemFaultParser(checkNotNull(sax));
	}

	SystemSourceSet parse(InputStream sectionsIn, InputStream rupturesIn, GmmSet gmmSet)
			throws SAXException, IOException {
		checkState(!used, "This parser has expired");
		this.gmmSet = gmmSet;
		sections = parseSections(sectionsIn);
		sax.parse(rupturesIn, this);
		checkState(sourceSet.size() > 0, "SystemSourceSet is empty");
		used = true;
		return sourceSet;
	}

	private List<GriddedSurface> parseSections(InputStream in) throws SAXException, IOException {
		SystemSectionParser parser = SystemSectionParser.create(sax);
		return parser.parse(in);
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
			//@formatter:off
			switch (e) {

				case INDEXED_FAULT_SOURCE_SET:
					String name = readString(NAME, atts);
					double weight = readDouble(WEIGHT, atts);
					sourceSetBuilder = new SystemSourceSet.Builder();
					sourceSetBuilder
						.name(name)
						.weight(weight)
						.gmms(gmmSet)
						.sections(sections);
					log.info("   Sections: " + sections.size());
					log.info("Rupture set: "  + name + "/" + RUPTURES_FILENAME);
					log.info(" Set Weight: " + weight);
					break;

				case MAG_FREQ_DIST_REF:
					mfdHelper = MfdHelper.create();
					parsingDefaultMFDs = true;
					break;

				case MAG_FREQ_DIST:
					if (parsingDefaultMFDs) {
						mfdHelper.addDefault(atts);
						break;
					}
					MfdType type = readEnum(TYPE, atts, MfdType.class);
					checkState(type == MfdType.SINGLE, "Only SINGLE mfds are supported");
					sourceMag = readDouble(M, atts);
					sourceRate = readDouble(A, atts);
					break;

				case GEOMETRY:
					sourceSetBuilder
						.mag(sourceMag)
						.rate(sourceRate)
						.indices(rangeStringToIntList(readString(INDICES, atts)))
						.depth(readDouble(DEPTH, atts))
						.dip(readDouble(DIP, atts))
						.rake(readDouble(RAKE, atts))
						.width(readDouble(WIDTH, atts));
					break;

			}
			//@formatter:on

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

				case MAG_FREQ_DIST_REF:
					parsingDefaultMFDs = false;
					break;

				case INDEXED_FAULT_SOURCE_SET:
					sourceSet = sourceSetBuilder.build();
					log.info("   Ruptures: " + sourceSet.size());
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
