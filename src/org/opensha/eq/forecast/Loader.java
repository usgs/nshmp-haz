package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static com.google.common.io.Files.isDirectory;
import static com.google.common.io.Files.isFile;
import static java.nio.file.Files.newDirectoryStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.opensha.util.Logging;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.TreeTraverser;
import com.google.common.io.FileWriteMode;
//import com.google.common.io.Files;
import com.sun.nio.zipfs.ZipFileSystemProvider;

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

	/**
	 * Load a {@code Forecast}. Supplied path should be an absolute path to a directory containing
	 * sub-directories by {@code SourceType}s, or the absolute path to a zipped forecast.
	 * @param path to forecast directory or Zip file (absolute)
	 * @return a newly created {@code Forecast}
	 * @throws Exception TODO checked exceptions
	 */
	public static Forecast load(String path) throws IOException, URISyntaxException {
		
//		if 
		
		
		
		// validate forecast directory
		File forecastDir = null;
		try {
			checkNotNull(path, "Path is null");
			forecastDir = new File(path);
			checkArgument(forecastDir.exists(), "Path does not exist: %s", path);
			log.info("Loading forecast: " + forecastDir.getName());
		} catch (NullPointerException | IllegalArgumentException e) {
			logConfigException(e);
			throw e;
		}
		
		
		
		
		// process by type
//		for (File typeDir : listTypeDirs(forecastDir)) {
// 			log.info("  " + typeDir.getName() + " Sources ...");
////			processTypeDir(typeDir);
//		}
		
		List<Path> typePaths = typeDirectories(Paths.get(path));
//		System.out.println("Paths size: " + paths.size());
		for (Path typePath : typePaths) {
 			log.info("  " + typePath.getFileName() + " Sources ...");

//			System.out.println("Paths: " + path);
			processTypeDir(typePath);
		}

		return null;
	}

	
	private static void processTypeDir(Path typeDir) throws IOException {
		
		int sourceCount = 0;
		
		List<Path> typePaths = null;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(typeDir, SourceFilter.INSTANCE)) {
			typePaths = Lists.newArrayList(ds);
		}
		
		for (Path sourcePath : typePaths) {
			log.info("    File: " + sourcePath);
			// TODO change to "Parsed: *" and parse it
			sourceCount++;
		}
		
		/*
		 * gmm.xml file -- this MUST exist if there is at least one source file,
		 * however it MAY NOT exsist if all source files happen to be in nested
		 * directories
		 */
		Path gmmFile = null;
		if (sourceCount > 0) {
			gmmFile = typeDir.resolve(GMM_Parser.FILE_NAME); // Paths.get(typeDir.toString(), GMM_Parser.FILE_NAME);
			
			
			checkState(Files.exists(gmmFile), "Sources in %s require gmm.xml", typeDir);
			log.info("    File: " + gmmFile.getFileName());
		}
		
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(typeDir, NestedDirFilter.INSTANCE)) {
			for (Path nestedSourceDir : ds) {
				processNestedSourceDir(nestedSourceDir, gmmFile);
			}
		}
		
		
	}
	
	// gmm file may be null, but that requires one to be present in the parent dir
	private static void processNestedSourceDir(Path sourceDir, Path gmmFile) throws IOException {
		log.info("**      Source dir:" + sourceDir.getFileName());
		int sourceCount = 0;
		
		List<Path> nestedSourcePaths = null;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(sourceDir, SourceFilter.INSTANCE)) {
			nestedSourcePaths = Lists.newArrayList(ds);
		}
		
		for (Path sourcePath : nestedSourcePaths) {
			log.info("    File: " + sourcePath.getFileName()); // TODO change to "Parsed: *"
			// TODO parse
			sourceCount++;
		}
		
		/*
		 * gmm.xml file -- this MUST exist if there is at least one source file
		 * and there is no file in the parent source type directory
		 */
		Path nestedGmmFile = null;
		if (sourceCount > 0) {
			nestedGmmFile = Paths.get(sourceDir.toString(), GMM_Parser.FILE_NAME);
			checkState(Files.exists(nestedGmmFile) || gmmFile != null,
				"Source files present in %s; gmm.xml file required", nestedGmmFile);
			log.info("**       GMM file:" + gmmFile.getFileName());
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

	public static void main(String args[]) throws IOException, URISyntaxException {
		
//		// always want to re-trhow initialization exceptions to be able to run tests
//		try {
//			String path = "../nshmp-forecast-dev/forecasts/2008/Western US";
//			Loader.load(path);
//		} catch (Exception e) {
//			System.exit(1);
//		}
		
		
		String forecastPath = "/Users/pmpowers/projects/git/nshmp-forecast-dev/forecasts/2008/Western US";
		String zipPath = forecastPath + ".zip";
		String zipNestPath = forecastPath + " nest.zip";
		
//		load(forecastPath);
//		System.out.println();
//		load(zipPath);
//		System.out.println();
		load(zipNestPath);
		
		
	}
	
	private static final Map<String, String> ZIP_ENV_MAP = ImmutableMap.of("create", "false",
		"encoding", "UTF-8");
		
	private static final String ZIP_SCHEME = "jar:file";

	private static List<Path> typeDirectories(Path path) throws IOException, URISyntaxException {

		boolean isZip = path.getFileName().toString().toLowerCase().endsWith(".zip");
		
		if (isZip) {
			URI zipURI = new URI(ZIP_SCHEME, path.toString(), null);
			FileSystem zfs = FileSystems.newFileSystem(zipURI, ZIP_ENV_MAP);
			Path zipRoot = Iterables.get(zfs.getRootDirectories(), 0);
			List<Path> paths = typeDirectoryList(zipRoot);
			if (paths.size() > 0) return paths;
			
			// We expect that some forecasts will be nested one level down
			// in zip files; there should only ever be one nested directory
			// so take a look in that, otherwise we'll throw an exception
			// updtream for having an empty forecast.
			Path nestedDir = firstPath(zipRoot);
			System.out.println(nestedDir);
			return typeDirectoryList(nestedDir);
		}
		
		return typeDirectoryList(path);
	}
	
	private static List<Path> typeDirectoryList(Path path) throws IOException {
		try (DirectoryStream<Path> ds = newDirectoryStream(path, TypeFilter.INSTANCE)) {
			return Lists.newArrayList(ds);
		}
	}
	
	private static Path firstPath(Path path) throws IOException {
		try (DirectoryStream<Path> ds = newDirectoryStream(path, ZipSkipFilter.INSTANCE)) {
			return Lists.newArrayList(ds).get(0);
		}
	}
	
	private static void logConfigException(Exception e) {
		StringBuilder sb = new StringBuilder(LF);
		sb.append("** Config error: ").append(e.getMessage()).append(LF);
		sb.append("** Exiting **").append(LF);
		log.severe(sb.toString());
	}
	
	/*
	 * Only lists those directories matching a SourceType.
	 */
	private static enum TypeFilter implements DirectoryStream.Filter<Path> {
		INSTANCE;
		@Override public boolean accept(Path path) throws IOException {
			try {
				String name = path.getFileName().toString();
				// zip directory entries preserve trailing slash
				if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
				SourceType.fromString(name);
				return true;
			} catch (IllegalArgumentException iae) {
				return false;
			}
		}
	}
	
	/*
	 * Skips pesky __MACOSX resource fork files that creep into zip files.
	 * Others?
	 */
	private static enum ZipSkipFilter implements DirectoryStream.Filter<Path> {
		INSTANCE;
		@Override public boolean accept(Path path) throws IOException {
			return !path.getFileName().toString().startsWith("__");
		}
	}

	/*
	 * Filters source XML files, skipping hidden files, those that start with a
	 * tilde (~), and any gmm.xml files.
	 */
	private static enum SourceFilter implements DirectoryStream.Filter<Path> {
		INSTANCE;
		@Override public boolean accept(Path path) throws IOException {
			String s = path.getFileName().toString();
			return Files.isRegularFile(path) && !Files.isHidden(path) &&
				s.toLowerCase().endsWith(".xml") && !s.equals(GMM_Parser.FILE_NAME) &&
				!s.startsWith("~");
		}
	}
	
	/*
	 * Filters nested source directories, skipping hidden directories and those
	 * that start with a tilde (~).
	 */
	private static enum NestedDirFilter implements DirectoryStream.Filter<Path> {
		INSTANCE;
		@Override public boolean accept(Path path) throws IOException {
			String s = path.getFileName().toString();
			return Files.isDirectory(path) && !Files.isHidden(path) && !s.startsWith("~");
		}
	}	

}






