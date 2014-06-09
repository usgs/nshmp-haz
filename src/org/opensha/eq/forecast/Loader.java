package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.TreeTraverser;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;

/**
 * {@code Forecast} loader. This class is not thread safe.
 * 
 * @author Peter Powers
 */
public class Loader {
	
	private static final String LF = LINE_SEPARATOR.value();

	private static final Logger log;
	
	private static final ClusterParser clusterParser;
	private static final FaultParser faultParser;
	private static final GridParser gridParser;
	private static final InterfaceParser interfaceParser;
	private static final SlabParser slabParser;

	static {
		// TODO see Logging; no log file handler yet
		log = Logging.create(Loader.class);
		SAXParser saxParser = null;
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			saxParser = factory.newSAXParser();
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error initializing SAX parser", e);
			System.exit(1);
		}
		clusterParser = ClusterParser.create(saxParser);
		faultParser = FaultParser.create(saxParser);
		gridParser = GridParser.create(saxParser);
		interfaceParser = InterfaceParser.create(saxParser);
		slabParser = SlabParser.create(saxParser);
	}


	public static Forecast load(String path) throws Exception {
		
		// validate forecast directory
		File forecastDir = null;
		try {
			forecastDir = new File(checkNotNull(path, "Path is null"));
			log.info("Forecast: " + forecastDir.getName());
			checkArgument(forecastDir.exists(), "Path does not exist: %s", path);
			checkArgument(forecastDir.isDirectory(), "Path is not a directory: %s", path);
		} catch (NullPointerException npe) {
			logConfigException(npe);
			throw npe;
		} catch (IllegalArgumentException iae) {
			logConfigException(iae);
			throw iae;
		}
		
		// process by type
		for (File typeDir : listTypeDirs(forecastDir)) {
 			log.info("  " + typeDir.getName() + " Sources ...");
			processTypeDir(typeDir);
		}
		
		return null;
	}
	
	private static void processTypeDir(File typeDir) throws Exception {
		
		int sourceCount = 0;
		for (File sourceFile : listSourceFiles(typeDir)) {
			log.info("    File: " + sourceFile.getName()); // TODO change to "Parsed: *"
			// TODO parse
			sourceCount++;
		}
		
		/*
		 * gmm.xml file -- this MUST exist if there is at least one source file,
		 * however it MAY NOT exsist if all source files happen to be in nested
		 * directories
		 */
		File gmmFile = null;
		if (sourceCount > 0) {
			gmmFile = new File(typeDir, GMM_Parser.FILE_NAME);
			checkState(gmmFile.exists(), "Source files present in %s; gmm.xml file required",
				typeDir);
			log.info("**   GMM file:" + gmmFile.getName());
		}
		
//		if (!gmmFile.exists() && sourceCount > 0) {
//			
//		}
//		log.info("**     GMM file:" + sourceFile.getName());
		for (File nestedSourceDir : listNestedSourceDirs(typeDir)) {
			processNestedSourceDir(nestedSourceDir, gmmFile);
		}
		
		
	}
	
	// gmm file may be null, but that requires one to be present in the parent dir
	private static void processNestedSourceDir(File sourceDir, File gmmFile) {
		log.info("**      Source dir:" + sourceDir.getName());
		int sourceCount = 0;
		for (File sourceFile : listSourceFiles(sourceDir)) {
			log.info("**       Source file:" + sourceFile.getName());
			// TODO parse
			sourceCount++;
		}
		
		/*
		 * gmm.xml file -- this MUST exist if there is at least one source file
		 * and there is no file in the parent source type directory
		 */
		File nestedGmmFile = null;
		if (sourceCount > 0) {
			nestedGmmFile = new File(sourceDir, GMM_Parser.FILE_NAME);
			checkState(nestedGmmFile.exists() || gmmFile != null,
				"Source files present in %s; gmm.xml file required", nestedGmmFile);
			log.info("**       GMM file:" + gmmFile.getName());
		}
		
	}
	
	public static FaultSourceSet processFile(String path) throws Exception {
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
			throw spe;
		} catch (SAXException se) {
			StringBuilder sb = new StringBuilder(LF);
			sb.append("** Other SAX parsing error: Exiting **").append(LF);
			log.log(Level.SEVERE, sb.toString(), se);
			throw se;
		} catch (IOException ioe) {
			StringBuilder sb = new StringBuilder(LF);
			sb.append("** IO error: ").append(ioe.getMessage()).append(LF);
			sb.append("**   File: ").append(file.getPath()).append(LF);
			sb.append("** Exiting **").append(LF);
			log.severe(sb.toString());
			throw ioe;
		}
	}

	// will want to loop through forecast directory structure, possibly
	// filtering by type or region, handing off each file to appropriate parser

	public static void main(String args[]) {
		
		// always want to re-trhow initialization exceptions to be able to run tests
		try {
			String path = "../nshmp-forecast-dev/forecasts/2008/Western US";
			Loader.load(path);
		} catch (Exception e) {
			System.exit(1);
		}
//		Loader.load("tmp/NSHMP08-noRedux/Western US/Fault/brange.3dip.gr.xml");
//		FaultSourceSet faultSet = Loader.load("../tmp/NSHMP08-noRedux/California/Fault/bFault.ch.xml");
//		System.out.println(faultSet.sources.size());
		

	}

	private static void logConfigException(Exception e) {
		StringBuilder sb = new StringBuilder(LF);
		sb.append("** Config error: ").append(e.getMessage()).append(LF);
		sb.append("** Exiting **").append(LF);
		log.severe(sb.toString());
	}
	
	/*
	 * Lists the source type directories skipping hidden directories and those
	 * that start with a tilde (~).
	 */
	private static Iterable<File> listTypeDirs(File dir) {
		return FluentIterable.from(Lists.newArrayList(dir.listFiles()))
				.filter(Files.isDirectory())
				.filter(SKIP_FILE_FILTER)
				.filter(TYPE_FILE_FILTER);
	}
	
	/*
	 * Lists source files in a type directory skipping hidden files, those that
	 * start with a tilde (~), and the gmm.xml file.
	 */
	private static Iterable<File> listSourceFiles(File dir) {
		return FluentIterable.from(Lists.newArrayList(dir.listFiles()))
				.filter(Files.isFile())
				.filter(SKIP_FILE_FILTER)
				.filter(SOURCE_FILE_FILTER);
	}
	
	/*
	 * Lists any source directories nested in a type directory skipping hidden
	 * directories and start with a tilde (~).
	 */
	private static Iterable<File> listNestedSourceDirs(File dir) {
		return FluentIterable.from(Lists.newArrayList(dir.listFiles()))
				.filter(Files.isDirectory())
				.filter(SKIP_FILE_FILTER);
	}

	private static final Predicate<File> SKIP_FILE_FILTER = new Predicate<File>() {
		@Override
		public boolean apply(File f) {
			return !f.isHidden() && !f.getName().startsWith("~");
		}
	};

	private static final Predicate<File> SOURCE_FILE_FILTER = new Predicate<File>() {
		@Override
		public boolean apply(File f) {
			return f.getName().endsWith(".xml") && !f.getName().equals(GMM_Parser.FILE_NAME);
		}
	};

	private static final Predicate<File> TYPE_FILE_FILTER = new Predicate<File>() {
		@Override
		public boolean apply(File f) {
			try {
				SourceType.fromString(f.getName());
				return true;
			} catch (IllegalArgumentException iae) {
				return false;
			}
		}
	};

}






