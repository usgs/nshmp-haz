package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.opensha.util.Logging;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.collect.TreeTraverser;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import com.sun.org.apache.xpath.internal.axes.WalkerFactory;

/**
 * TODO logging
 * TODO if multithreaded forecast loading is
 * adopted, new parsers and handlers should be initialized on a per-thread basis
 * 
 * @author Peter Powers
 */
public class Loader {

	private static final String LF = LINE_SEPARATOR.value();

	private static final Logger log;
	private static final FaultSourceParser faultParser;
	// private static final gridParser;

	static {
		log = Logging.create(Loader.class);
		SAXParser saxParser = null;
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			saxParser = factory.newSAXParser();
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error initializing loader", e);
			System.exit(1);
		}
		faultParser = FaultSourceParser.create(saxParser);
	}


	public static FaultSourceSet load(String path) {
		File file = new File(path);
		try {
			return faultParser.parse(file);
		} catch (SAXParseException spe) {
			StringBuilder sb = new StringBuilder(LF);
			sb.append("** SAX Parser error:").append(LF);
			sb.append("**   File: ").append(spe.getSystemId()).append(LF);
			sb.append("**   Line: ").append(spe.getLineNumber());
			sb.append(" [").append(spe.getColumnNumber());
			sb.append("]").append(LF);
			sb.append("**   Info: ").append(spe.getMessage());
			if (spe.getException() != null) {
				String message = spe.getException().getMessage();
				if (message != null) {
					sb.append(LF).append("           ")
						.append(spe.getException().getMessage());
					sb.append(LF).append(Throwables.getStackTraceAsString(
						spe.getException()));
				} else {
					sb.append(", ").append(Throwables.getStackTraceAsString(
						spe.getException()));
				}
			}
			sb.append(LF);
			sb.append("** Exiting **").append(LF);
			log.severe(sb.toString());
			System.exit(1);
		} catch (SAXException se) {
			StringBuilder sb = new StringBuilder(LF);
			sb.append("** Other SAX parsing error: Exiting **").append(LF);
			log.log(Level.SEVERE, sb.toString(), se);
			System.exit(1);
		} catch (IOException ioe) {
			StringBuilder sb = new StringBuilder(LF);
			sb.append("** IO error: ").append(ioe.getMessage()).append(LF);
			sb.append("**   File: ").append(file.getPath()).append(LF);
			sb.append("** Exiting **").append(LF);
			log.severe(sb.toString());
			System.exit(1);
		}
		return null;
	}

	// will want to loop through forecast directory structure, possibly
	// filtering by type or region, handing off each file to appropriate parser

	public static void main(String args[]) {

//		Loader.load("tmp/NSHMP08-noRedux/Western US/Fault/brange.3dip.gr.xml");
		FaultSourceSet faultSet = Loader.load("tmp/NSHMP08-noRedux/California/Fault/bFault.ch.xml");
		System.out.println(faultSet.sources.size());
		

	}

	
	private static final String FC_PATH = "tmp/NSHMP08-noRedux/California";
	/*
	 * This could be a zip file
	 * Should kill symlinks (j7)
	 */
	public static Forecast load(File dir) {
		checkArgument(dir.isDirectory(), "Supplied file is not a directory");
		FluentIterable<File> typeDirs = FluentIterable
			.from(Lists.newArrayList(dir.listFiles()))
			.filter(Files.isDirectory())
			.filter(SKIP_FILE_FILTER)
			.filter(TYPE_FILE_FILTER);
		
		for (File typeDir : typeDirs) {
			SourceType type = SourceType.valueOf(typeDir.getName());
			System.out.println(type + ":");
			
			// typeDirs can have nested folders one level deep
			
			// check for gmm.xml - required unless no xml files exist,
			// subdirs should have own gmm.xml
			
			// check for subdirectories
		}
		return null;
	}
	
	private static final Predicate<File> SKIP_FILE_FILTER = new Predicate<File>() {
		@Override
		public boolean apply(File f) {
			return !f.isHidden() && !f.getName().startsWith("~");
		}
	};

	private static final Predicate<File> TYPE_FILE_FILTER = new Predicate<File>() {
		@Override
		public boolean apply(File f) {
			try {
				SourceType.valueOf(f.getName());
				return true;
			} catch (IllegalArgumentException iae) {
				return false;
			}
		}
	};

}






