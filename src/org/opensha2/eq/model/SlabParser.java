package org.opensha2.eq.model;

import static org.opensha2.eq.model.SourceType.SLAB;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.SAXParser;

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

  SlabSourceSet parse(InputStream in, GmmSet gmmSet, ModelConfig config) throws SAXException,
      IOException {
    GridSourceSet delegate = gridParser.parse(in, gmmSet, config);
    return new SlabSourceSet(delegate);
  }

}
