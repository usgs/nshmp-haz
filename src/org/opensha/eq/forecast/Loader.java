package org.opensha.eq.forecast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static java.nio.file.Files.newDirectoryStream;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.opensha.util.Logging;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * {@code Forecast} loader. This class takes care of extensive error-handling
 * required when initializing a {@code Forecast}.
 * 
 * <p>The {@link Loader#load(String)} method of this class is not thread safe.</p>
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
		// TODO see Logging; add file handler
		log = Logging.create(Loader.class);

		SAXParser saxParser = null;
		try {
			saxParser = SAXParserFactory.newInstance().newSAXParser();
		} catch (ParserConfigurationException | SAXException e) {
			log.log(Level.SEVERE, "** SAX initialization error **", e);
			Throwables.propagate(e);
		}

		clusterParser = ClusterParser.create(saxParser);
		faultParser = FaultParser.create(saxParser);
		gridParser = GridParser.create(saxParser);
		interfaceParser = InterfaceParser.create(saxParser);
		slabParser = SlabParser.create(saxParser);
	}

	/**
	 * Load a {@code Forecast}. Supplied path should be an absolute path to a
	 * directory containing sub-directories by {@code SourceType}s, or the
	 * absolute path to a zipped forecast.
	 * 
	 * <p>This method is not thread safe.</p>
	 * 
	 * @param path to forecast directory or Zip file (absolute)
	 * @return a newly created {@code Forecast}
	 * @throws Exception TODO checked exceptions
	 */
	public static Forecast load(String path) throws Exception {

		Path forecastPath = null;
		List<Path> typePaths = null;

		try {
			checkNotNull(path, "Path is null");
			forecastPath = Paths.get(path);
			checkArgument(Files.exists(forecastPath), "Path does not exist: %s", path);
			typePaths = typeDirectories(Paths.get(path));
			checkState(typePaths.size() > 0, "Empty forecast: %s", forecastPath.getFileName());
		} catch (Exception e) {
			logConfigException(e);
			throw e;
		}
		
		log.info("Loading forecast: " + forecastPath.getFileName());
		
		for (Path typePath : typePaths) {
			String name = cleanZipName(typePath.getFileName().toString());
			log.info("  " + name + " Sources ...");
			processTypeDir(typePath);
		}

		return null;
	}
	
	private static final Map<String, String> ZIP_ENV_MAP = ImmutableMap.of("create", "false",
		"encoding", "UTF-8");

	private static final String ZIP_SCHEME = "jar:file";

	private static List<Path> typeDirectories(Path path) throws Exception {
		
		// methods in here potentially throw a myriad of checked and
		// unchecked exceptions

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
			checkArgument(Files.isDirectory(nestedDir), "No nested directory in zip: %s", nestedDir);
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

	private static void processTypeDir(Path typeDir) throws IOException {

		int sourceCount = 0;

		List<Path> typePaths = null;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(typeDir, SourceFilter.INSTANCE)) {
			typePaths = Lists.newArrayList(ds);
		}

		for (Path sourcePath : typePaths) {
			log.info("    File: " + typeDir.getParent().relativize(sourcePath));
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
			gmmFile = typeDir.resolve(GMM_Parser.FILE_NAME);
			checkState(Files.exists(gmmFile), "Sources in %s require gmm.xml", typeDir);
			// TODO change to "Parsed: *" and parse it if present
			log.info("    File: " + typeDir.getParent().relativize(gmmFile));
		}

		try (DirectoryStream<Path> ds = Files.newDirectoryStream(typeDir, NestedDirFilter.INSTANCE)) {
			for (Path nestedSourceDir : ds) {
				processNestedSourceDir(nestedSourceDir, gmmFile);
			}
		}

	}

	// gmm file may be null, but that requires one to be present in the parent
	// dir
	private static void processNestedSourceDir(Path sourceDir, Path gmmFile) throws IOException {
		int sourceCount = 0;

		List<Path> nestedSourcePaths = null;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(sourceDir, SourceFilter.INSTANCE)) {
			nestedSourcePaths = Lists.newArrayList(ds);
		}

		for (Path sourcePath : nestedSourcePaths) {
			Path typeDir = sourceDir.getParent().getParent();
			log.info("    File: " + typeDir.relativize(sourcePath));
			// TODO parse
			sourceCount++;
		}

		/*
		 * gmm.xml file -- this MUST exist if there is at least one source file
		 * and there is no file in the parent source type directory
		 */
		Path nestedGmmFile = null;
		if (sourceCount > 0) {
			nestedGmmFile = sourceDir.resolve(GMM_Parser.FILE_NAME);
			checkState(Files.exists(nestedGmmFile) || gmmFile != null,
				"Source files present in %s; gmm.xml file required", nestedGmmFile);
			Path typeDir = sourceDir.getParent().getParent();
			log.info("    File: " + typeDir.relativize(nestedGmmFile));
		}

	}

	// TODO hook this in once we get to parsing stage
	private static FaultSourceSet processFile(String path) throws Exception {
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
					sb.append(LF).append("           ").append(spe.getException().getMessage());
					sb.append(LF).append(Throwables.getStackTraceAsString(spe.getException()));
				} else {
					sb.append(", ").append(Throwables.getStackTraceAsString(spe.getException()));
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


	private static void logConfigException(Exception e) {
		StringBuilder sb = new StringBuilder(LF);
		sb.append("** Config error: ").append(e.getMessage());
		log.severe(sb.toString());
	}

	/* Prune trailing slash if such exists. */
	private static String cleanZipName(String name) {
		return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
	}
	
	/*
	 * Only lists those directories matching a SourceType.
	 */
	private static enum TypeFilter implements DirectoryStream.Filter<Path> {
		INSTANCE;
		@Override public boolean accept(Path path) throws IOException {
			try {
				String name = path.getFileName().toString();
				SourceType.fromString(cleanZipName(name));
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
