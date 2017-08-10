package gov.usgs.earthquake.nshmp.eq.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.eq.model.SystemParser.SECTIONS_FILENAME;
import static gov.usgs.earthquake.nshmp.internal.Parsing.readDouble;
import static gov.usgs.earthquake.nshmp.internal.Parsing.readString;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.ASEIS;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.DEPTH;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.DIP;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.DIP_DIR;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.INDEX;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.LOWER_DEPTH;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.NAME;

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

import gov.usgs.earthquake.nshmp.eq.fault.surface.DefaultGriddedSurface;
import gov.usgs.earthquake.nshmp.eq.fault.surface.GriddedSurface;
import gov.usgs.earthquake.nshmp.geo.LocationList;
import gov.usgs.earthquake.nshmp.internal.SourceElement;

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
  private List<String> sectionNames;
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
  void parse(InputStream in) throws SAXException, IOException {
    checkState(!used, "This parser has expired");
    sax.parse(in, this);
    checkState(sections.size() > 0, "Section surface list is empty");
    used = true;
  }

  /* Can't call before parse(). */
  List<GriddedSurface> sections() {
    checkState(used == true);
    return sections;
  }

  /* Can't call before parse(). */
  List<String> sectionNames() {
    checkState(used == true);
    return sectionNames;
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
          sectionNames = Lists.newArrayList();
          String setName = readString(NAME, atts);
          log.info("Fault model: " + setName + "/" + SECTIONS_FILENAME);
          break;

        /*
         * NOTE: currently, section indices are ordered ascending from zero; if
         * this were to change we'd need agregate a list of indices as well.
         */
          
        case SECTION:
          surfaceBuilder = DefaultGriddedSurface.builder();
          String sectionName = readString(NAME, atts);
          sectionNames.add(cleanName(sectionName));
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
  
  /*
   * TODO for consistency during development we have kept the original (long)
   * UCERF3 section names in fault_sections.xml but it might make sense down
   * the road to update the source file rather thna clean the name here.
   */
  private static String cleanName(String name) {
    return name.replace(" 2011 CFM", "").replace(", Subsection ", " [") + "]";
  }

}
