package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import static org.opensha2.eq.model.SourceAttribute.ASEIS;
import static org.opensha2.eq.model.SourceAttribute.DEPTH;
import static org.opensha2.eq.model.SourceAttribute.DIP;
import static org.opensha2.eq.model.SourceAttribute.DIP_DIR;
import static org.opensha2.eq.model.SourceAttribute.INDEX;
import static org.opensha2.eq.model.SourceAttribute.LOWER_DEPTH;
import static org.opensha2.eq.model.SourceAttribute.NAME;
import static org.opensha2.eq.model.SystemParser.SECTIONS_FILENAME;
import static org.opensha2.util.Parsing.readDouble;
import static org.opensha2.util.Parsing.readString;

import org.opensha2.eq.fault.surface.DefaultGriddedSurface;
import org.opensha2.eq.fault.surface.GriddedSurface;
import org.opensha2.geo.LocationList;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.Lists;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

/*
 * Non-validating indexed fault section parser. SAX parser 'Attributes' are
 * stateful and cannot be stored. This class is not thread safe. The List
 * returned by parse() is mutable as it will be made immutable when ultimately
 * passed to SystemSourceSet.Builder
 *
 * @author Peter Powers
 */
@SuppressWarnings("incomplete-switch")
class SystemSectionParser extends DefaultHandler {

  private final Logger log = Logger.getLogger(SystemParser.class.getName());
  private final SAXParser sax;
  private boolean used = false;

  private Locator locator;

  private List<GriddedSurface> sections;
  private DefaultGriddedSurface.Builder surfaceBuilder;

  // Traces are the only text content in source files
  private boolean readingTrace = false;
  private StringBuilder traceBuilder = null;

  private SystemSectionParser(SAXParser sax) {
    this.sax = sax;
  }

  static SystemSectionParser create(SAXParser sax) {
    return new SystemSectionParser(checkNotNull(sax));
  }

  // TODO can we just return RuptureSurface? are grid details necessary
  // downstream?
  List<GriddedSurface> parse(InputStream in) throws SAXException, IOException {
    checkState(!used, "This parser has expired");
    sax.parse(in, this);
    checkState(sections.size() > 0, "Section surface list is empty");
    used = true;
    return sections;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes atts)
      throws SAXException {

    SourceElement e = null;
    try {
      e = SourceElement.fromString(qName);
    } catch (IllegalArgumentException iae) {
      throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
    }

    try {
      switch (e) {

        case SYSTEM_FAULT_SECTIONS:
          sections = Lists.newArrayList();
          String setName = readString(NAME, atts);
          log.info("Fault model: " + setName + "/" + SECTIONS_FILENAME);
          break;

        case SECTION:
          surfaceBuilder = DefaultGriddedSurface.builder();
          String sectionName = readString(NAME, atts);
          String sectionIndex = readString(INDEX, atts);
          log.finer("    Section: [" + sectionIndex + "] " + sectionName);
          break;

        case GEOMETRY:
          double aseis = readDouble(ASEIS, atts);
          double depth = readDouble(DEPTH, atts);
          double lowerDepth = readDouble(LOWER_DEPTH, atts);
          surfaceBuilder.depth(depth)
              .lowerDepth(lowerDepth)
              .aseis(aseis)
              .dip(readDouble(DIP, atts))
              .dipDir(readDouble(DIP_DIR, atts));
          break;

        case TRACE:
          readingTrace = true;
          traceBuilder = new StringBuilder();
          break;
      }

    } catch (Exception ex) {
      throw new SAXParseException("Error parsing <" + qName + ">", locator, ex);
    }
  }

  @Override
  public void endElement(String uri, String localName, String qName)
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

  @Override
  public void characters(char ch[], int start, int length) throws SAXException {
    if (readingTrace) {
      traceBuilder.append(ch, start, length);
    }
  }

  @Override
  public void setDocumentLocator(Locator locator) {
    this.locator = locator;
  }

}
