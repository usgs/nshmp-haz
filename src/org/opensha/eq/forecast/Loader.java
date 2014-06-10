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
			log.info("    File: " + sourcePath); // TODO change to "Parsed: *"
//			System.out.println(Files.exists(sourcePath));
			// TODO parse
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
			
			
			System.out.println(Files.exists(gmmFile));
			checkState(Files.exists(gmmFile), "Sources in %s require gmm.xml", typeDir);
			log.info("    File: " + gmmFile.getFileName());
		}
		
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(typeDir, NestedDirFilter.INSTANCE)) {
			for (Path nestedSourceDir : ds) {
				processNestedSourceDir(nestedSourceDir, gmmFile);
			}
		}
		
		
	}

//	private static void processTypeDir(File typeDir) throws Exception {
//		
//		int sourceCount = 0;
//		for (File sourceFile : listSourceFiles(typeDir)) {
//			log.info("    File: " + sourceFile.getName()); // TODO change to "Parsed: *"
//			// TODO parse
//			sourceCount++;
//		}
//		
//		/*
//		 * gmm.xml file -- this MUST exist if there is at least one source file,
//		 * however it MAY NOT exsist if all source files happen to be in nested
//		 * directories
//		 */
//		File gmmFile = null;
//		if (sourceCount > 0) {
//			gmmFile = new File(typeDir, GMM_Parser.FILE_NAME);
//			checkState(gmmFile.exists(), "Source files present in %s; gmm.xml file required",
//				typeDir);
//			log.info("**   GMM file:" + gmmFile.getName());
//		}
//		
////		if (!gmmFile.exists() && sourceCount > 0) {
////			
////		}
////		log.info("**     GMM file:" + sourceFile.getName());
//		for (File nestedSourceDir : listNestedSourceDirs(typeDir)) {
//			processNestedSourceDir(nestedSourceDir, gmmFile);
//		}
//		
//		
//	}
	
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

//		for (File sourceFile : listSourceFiles(sourceDir)) {
//			log.info("**       Source file:" + sourceFile.getName());
//			// TODO parse
//			sourceCount++;
//		}
		
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

	// TODO set forecast name
	
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
		
		
////		Iterable<Path> 
//		Map<String, String> zipEnvMap = ImmutableMap.of(
//			"create", "false",
//			"encoding", "UTF-8");
//		
//		URI zipURI = new URI("jar:file", zipTestPath, null);
//		System.out.println(zipURI);
////		
////		Iterable<Path> typePaths = 
////		for (Path path : typePaths) {
////			System.out.println(path);
////		}
////		
//		FileSystem zfs = FileSystems.newFileSystem(zipURI, zipEnvMap);
//		for (Path dir : zfs.getRootDirectories()) {
//			System.out.println(dir);
////			DirectoryStream<Path> fDir = java.nio.file.Files.newDirectoryStream(dir, TypeFilter.INSTANCE);
////			for (Path f : fDir) {
////				System.out.println(f);
////				System.out.println(f.getFileName());
//////				InputStream in = java.nio.file.Files.newInputStream(f);
////////				in.
//////				System.out.println(f.getFileSystem());
////			}
//		}
		
		
//		Map<String, String> fileEnvMap = ImmutableMap.of();
		
//		File ff = new File(forecastPath);
		
//		System.out.println(ff.exists());
//		URI fileURI = ff.toURI(); // new URI("file", forecastPath, null);
//		System.out.println(fileURI);
		
//		Path path = Paths.get(forecastPath);
//		
//		File ff = new File(forecastPath);
////		URI pp = new URI(forecastPath);
//		
////		System.out.println(ff.toURI().getPath());
////		FileSystem ffs = FileSystems.newFileSystem(ff.toURI(), null);
//		
////		for (Path dir : ffs.getRootDirectories()) {
//			DirectoryStream<Path> fDir = java.nio.file.Files.newDirectoryStream(path, TypeFilter.INSTANCE);
//			for (Path f : fDir) {
//				System.out.println(f);
////				System.out.println(f.getFileSystem());
//			}
//		}
		

		
//		ZipFileSystemProvider zfsp = new ZipFileSystemProvider();
//		FileSystem fs = zfsp.getFileSystem(uri);
		
		//		System.out.println(uri.getRawPath());
		
//		File f = new File("/Users/pmpowers/projects/git/nshmp-forecast-dev/forecasts/2008.zip");
//		System.out.println(f.toURI());
		
//		File f = new File("/Users/pmpowers/projects/git/nshmp-forecast-dev/forecasts/2008");
//		System.out.println(f.exists());
//		URI uri = new File("/Users/pmpowers/projects/git/nshmp-forecast-dev/forecasts/2008").toURI();
////		System.out.println(uri.isOpaque());
//		System.out.println(uri);
//		FileSystem fs = FileSystems.getFileSystem(uri);
//		
		

//		FileSystem
//		Loader.load("tmp/NSHMP08-noRedux/Western US/Fault/brange.3dip.gr.xml");
//		FaultSourceSet faultSet = Loader.load("../tmp/NSHMP08-noRedux/California/Fault/bFault.ch.xml");
//		System.out.println(faultSet.sources.size());
		

	}
	
//	private static void loadTest(String resource) throws IOException, URISyntaxException {
//
//		List<Path> paths = typeDirectories(Paths.get(resource));
////		System.out.println("Paths size: " + paths.size());
//		for (Path path : paths) {
// 			log.info("  " + path.getFileName() + " Sources ...");
//
////			System.out.println("Paths: " + path);
//			processTypeDir(path);
//		}
//	}
//	
	
	
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
	
	
//	private static Iterable<Path> dirIterable(String dirPath) throws IOException {
//		return java.nio.file.Files.newDirectoryStream(Paths.get(dirPath)); //, TypeFilter.INSTANCE);
////		File ff = new File(forecastPath);
////		URI pp = new URI(forecastPath);
//		
////		System.out.println(ff.toURI().getPath());
////		FileSystem ffs = FileSystems.newFileSystem(ff.toURI(), null);
//		
////		for (Path dir : ffs.getRootDirectories()) {
////			DirectoryStream<Path> fDir = java.nio.file.Files.newDirectoryStream(path, TypeFilter.INSTANCE);
////			for (Path f : fDir) {
////				System.out.println(f);
//////				System.out.println(f.getFileSystem());
////			}
////		}
//
//	}
	
	

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

//	/*
//	 * Lists the source type directories skipping hidden directories and those
//	 * that start with a tilde (~).
//	 */
//	private static Iterable<File> listTypeDirs(File dir) {
//		return FluentIterable.from(Lists.newArrayList(dir.listFiles()))
//				.filter(isDirectory())
//				.filter(SKIP_FILE_FILTER)
//				.filter(TYPE_FILE_FILTER);
//	}
//	
//	/*
//	 * Lists source files in a type directory skipping hidden files, those that
//	 * start with a tilde (~), and the gmm.xml file.
//	 */
//	private static Iterable<File> listSourceFiles(File dir) {
//		return FluentIterable.from(Lists.newArrayList(dir.listFiles()))
//				.filter(isFile())
//				.filter(SKIP_FILE_FILTER)
//				.filter(SOURCE_FILE_FILTER);
//	}
//	
//	/*
//	 * Lists any source directories nested in a type directory skipping hidden
//	 * directories and those that start with a tilde (~).
//	 */
//	private static Iterable<File> listNestedSourceDirs(File dir) {
//		return FluentIterable.from(Lists.newArrayList(dir.listFiles()))
//				.filter(isDirectory())
//				.filter(SKIP_FILE_FILTER);
//	}
//
//	
//	
//	
//	private static final Predicate<File> SKIP_FILE_FILTER = new Predicate<File>() {
//		@Override
//		public boolean apply(File f) {
//			return !f.isHidden() && !f.getName().startsWith("~");
//		}
//	};
//
//	private static final Predicate<File> SOURCE_FILE_FILTER = new Predicate<File>() {
//		@Override
//		public boolean apply(File f) {
//			return f.getName().endsWith(".xml") && !f.getName().equals(GMM_Parser.FILE_NAME);
//		}
//	};
//
//	private static final Predicate<File> TYPE_FILE_FILTER = new Predicate<File>() {
//		@Override
//		public boolean apply(File f) {
//			try {
//				SourceType.fromString(f.getName());
//				return true;
//			} catch (IllegalArgumentException iae) {
//				return false;
//			}
//		}
//	};

}






