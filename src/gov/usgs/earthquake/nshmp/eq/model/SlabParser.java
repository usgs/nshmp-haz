package gov.usgs.earthquake.nshmp.eq.model;

import static gov.usgs.earthquake.nshmp.eq.model.SourceType.SLAB;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.SAXParser;

import org.xml.sax.SAXException;

/*
 * Placeholder parser; delegates to GridParser.
 *
 * @author Peter Powers
 */
class SlabParser {

  private GridParser gridParser;

  private SlabParser(SAXParser sax) {
    gridParser = GridParser.create(sax);
    gridParser.type = SLAB;
  }

  static SlabParser create(SAXParser sax) {
    return new SlabParser(sax);
  }

  SlabSourceSet parse(
      InputStream in,
      GmmSet gmmSet,
      ModelConfig config) throws SAXException, IOException {

    GridSourceSet delegate = gridParser.parse(in, gmmSet, config);
    return new SlabSourceSet(delegate);
  }

}
