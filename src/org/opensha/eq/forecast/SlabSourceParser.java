package org.opensha.eq.forecast;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.SAXParser;

import org.xml.sax.SAXException;

/*
 * Placeholder parser; delegates to GridSourceParser.
 * 
 * @author Peter Powers
 */
class SlabSourceParser {

	private GridSourceParser gridParser;

	private SlabSourceParser(SAXParser sax) {
		gridParser = GridSourceParser.create(sax);
	}

	static SlabSourceParser create(SAXParser sax) {
		return new SlabSourceParser(sax);
	}

	SlabSourceSet parse(File f) throws SAXException, IOException {
		GridSourceSet delegate = gridParser.parse(f);
		return new SlabSourceSet(delegate);
	}

}
