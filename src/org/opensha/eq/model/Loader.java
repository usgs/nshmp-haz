package org.opensha.eq.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static java.nio.file.Files.newDirectoryStream;
import static java.util.logging.Level.SEVERE;
import static org.opensha.eq.model.SystemFaultParser.GRIDSOURCE_FILENAME;
import static org.opensha.eq.model.SystemFaultParser.RUPTURES_FILENAME;
import static org.opensha.eq.model.SystemFaultParser.SECTIONS_FILENAME;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.opensha.eq.model.HazardModel.Builder;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * {@code HazardModel} loader. This class takes care of extensive checked
 * exceptions required when initializing a {@code HazardModel} and will exit the
 * JVM in most cases.
 * 
 * @author Peter Powers
 */
class Loader {

	static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final String LF = LINE_SEPARATOR.value();
	private static Logger log;
	private static SAXParser sax;

	static {
		log = Logger.getLogger(Loader.class.getName());
		try {
			sax = SAXParserFactory.newInstance().newSAXParser();
		} catch (ParserConfigurationException | SAXException e) {
			Throwables.propagate(e);
		}
	}

	/**
	 * Load a {@code HazardModel}. Supplied path should be an absolute path to a
	 * directory containing sub-directories by {@code SourceType}s, or the
	 * absolute path to a zipped model.
	 * 
	 * <p>This method is not thread safe. Any exceptions thrown whil loading
	 * will be logged and the JVM will exit.</p>
	 * 
	 * @param path to model directory or Zip file (absolute)
	 * @return a newly created {@code HazardModel}
	 */
	static HazardModel load(Path path, String name) {

		HazardModel.Builder builder = HazardModel.builder();
		Path modelPath = null;
		List<Path> typePaths = null;

		try {
			checkNotNull(path, "Path is null");
			modelPath = path.toRealPath();
			checkArgument(Files.exists(modelPath), "Path does not exist: %s", path);
			Path typeDirPath = typeDirectory(modelPath);
			
			log.info("Loading config...");
			Config config = Config.load(typeDirPath);
			log.info(config.toString());
			builder.config(config);
			
			typePaths = typeDirectoryList(typeDirPath);
			checkState(typePaths.size() > 0, "Empty model: %s", modelPath.getFileName());
			log.info("Loading model: " + name);
			builder.name(name);
			log.info("   From resource: " + modelPath.getFileName());

			for (Path typePath : typePaths) {
				String typeName = cleanZipName(typePath.getFileName().toString());
				log.info("");
				log.info("========  " + typeName + " Sources  ========");
				processTypeDir(typePath, builder);
			}

		} catch (IOException | URISyntaxException e) {
			handleConfigException(e);
		}

		log.info("");
		log.info("Building model...");
		HazardModel model = builder.build();
		log.info("Finished loading: " + modelPath.getFileName());

		return model;
	}
	
	private static final String CONFIG_PROPS = "config.properties";
	
//	private static Properties loadConfig(Path typeDirPath) throws IOException {
//		Properties props = new Properties();
//		Path propsPath = typeDirPath.resolve(CONFIG_PROPS);
//		InputStream is = Files.newInputStream(propsPath);
//		props.load(is);
//		Closeables.closeQuietly(is);
//		return props;
//	}
	

	private static final Map<String, String> ZIP_ENV_MAP = ImmutableMap.of("create", "false",
		"encoding", "UTF-8");

	private static final String ZIP_SCHEME = "jar:file";

	private static Path typeDirectory(Path path) throws URISyntaxException, IOException {

		boolean isZip = path.getFileName().toString().toLowerCase().endsWith(".zip");

		if (isZip) {
			URI zipURI = new URI(ZIP_SCHEME, path.toString(), null);
			FileSystem zfs = null;
			
			// TODO if a servlet reloads in the same JVM then the filesystem
			// already exists and an exception is thrown; not sure how this
			// behaves when using an unexploded WAR, but it probably fails;
			// it probably also fails for a directory model in a WAR
			try {
				zfs = FileSystems.getFileSystem(zipURI);
			} catch (FileSystemNotFoundException fsnfe) {
				zfs = FileSystems.newFileSystem(zipURI, ZIP_ENV_MAP);
			}
			Path zipRoot = Iterables.get(zfs.getRootDirectories(), 0);
			
			// The expectation is that zipped models will have been created
			// from a single model directory containing type directories,
			// which will therefore be nested two levels deep in the zip file;
			// in contrast to non-zip models where type directories will
			// be one level deep.
			
			Path nestedTypeDir = firstPath(zipRoot);
			checkArgument(Files.isDirectory(nestedTypeDir), "No nested directory in zip: %s", nestedTypeDir);
			return nestedTypeDir;

			// TODO clean
			// List<Path> paths = typeDirectoryList(zipRoot);
			// if (paths.size() > 0) return zipRoot;
			// return typeDirectoryList(nestedDir);
		}

		return path;
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

	private static void processTypeDir(Path typeDir, Builder builder) throws IOException {

		SourceType type = SourceType.fromString(cleanZipName(typeDir.getFileName().toString()));

		/*
		 * gmm.xml file -- this MUST exist if there is at least one source file,
		 * however it MAY NOT exsist if all source files happen to be in nested
		 * directories along with their own gmm.xml files
		 */

		// Collect type paths
		List<Path> typePaths = null;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(typeDir, SourceFilter.INSTANCE)) {
			typePaths = Lists.newArrayList(ds);
		}

		// Build GmmSet from gmm.xml
		GmmSet gmmSet = null;
		Path gmmPath = typeDir.resolve(GmmParser.FILE_NAME);

		// if source files exist, gmm.xml must also exist
		if (typePaths.size() > 0) {
			try {
				checkState(Files.exists(gmmPath), "%s sources present. Where is gmm.xml?",
					typeDir.getFileName());
			} catch (IllegalStateException ise) {
				handleConfigException(ise);
				throw ise;
			}
		}
		// having checked directory state, load gmms if present
		// we may have gmm.xml but no source files
		if (Files.exists(gmmPath)) {
			log.info("Parsing: " + typeDir.getParent().relativize(gmmPath));
			gmmSet = parseGMM(gmmPath);
		}

		for (Path sourcePath : typePaths) {
			log.info("Parsing: " + typeDir.getParent().relativize(sourcePath));
			SourceSet<? extends Source> sourceSet = parseSource(type, sourcePath, gmmSet);
			builder.sourceSet(sourceSet);
		}

		try (DirectoryStream<Path> ds = Files.newDirectoryStream(typeDir, NestedDirFilter.INSTANCE)) {
			for (Path nestedSourceDir : ds) {
				processNestedDir(nestedSourceDir, type, gmmSet, builder);
			}
		}

	}

	private static void processNestedDir(Path sourceDir, SourceType type, GmmSet gmmSet,
			Builder builder) throws IOException {

		/*
		 * gmm.xml -- this MUST exist if there is at least one source file and
		 * there is no gmm.xml file in the parent source type directory
		 */

		// Collect nested paths
		List<Path> nestedSourcePaths = null;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(sourceDir, SourceFilter.INSTANCE)) {
			nestedSourcePaths = Lists.newArrayList(ds);
		}

		Path typeDir = sourceDir.getParent().getParent();

		GmmSet nestedGmmSet = null;
		if (nestedSourcePaths.size() > 0) {
			Path nestedGmmPath = sourceDir.resolve(GmmParser.FILE_NAME);
			try {
				checkState(Files.exists(nestedGmmPath) || gmmSet != null,
					"%s sources present. Where is gmm.xml?", sourceDir.getFileName());
			} catch (IllegalStateException ise) {
				handleConfigException(ise);
				throw ise;
			}

			if (Files.exists(nestedGmmPath)) {
				log.info("Parsing: " + typeDir.relativize(nestedGmmPath));
				nestedGmmSet = parseGMM(nestedGmmPath);
			} else {
				log.info("Parsing: (using parent gmm.xml)");
				nestedGmmSet = gmmSet;
			}
		}

		if (type == SourceType.SYSTEM) {
			log.info("Parsing: " + typeDir.relativize(sourceDir));
			parseIndexedSource(sourceDir, gmmSet, builder);
		} else {
			for (Path sourcePath : nestedSourcePaths) {
				log.info("Parsing: " + typeDir.relativize(sourcePath));
				SourceSet<? extends Source> sourceSet = parseSource(type, sourcePath, nestedGmmSet);
				builder.sourceSet(sourceSet);
			}
		}
	}

	private static SourceSet<? extends Source> parseSource(SourceType type, Path path, GmmSet gmmSet) {
		try {
			InputStream in = Files.newInputStream(path);
			switch (type) {
				case AREA:
					throw new UnsupportedOperationException("Area sources not currently supported");
				case CLUSTER:
					return ClusterParser.create(sax).parse(in, gmmSet);
				case FAULT:
					return FaultParser.create(sax).parse(in, gmmSet);
				case GRID:
					return GridParser.create(sax).parse(in, gmmSet);
				case INTERFACE:
					return InterfaceParser.create(sax).parse(in, gmmSet);
				case SLAB:
					return SlabParser.create(sax).parse(in, gmmSet);
				case SYSTEM:
					throw new UnsupportedOperationException(
						"Fault system sources are not processed with this method");
				default:
					throw new IllegalStateException("Unkown source type");
			}
		} catch (Exception e) {
			handleParseException(e, path);
			return null;
		}
	}

	private static void parseIndexedSource(Path dir, GmmSet gmmSet, Builder builder) {
		try {
			Path sectionsPath = dir.resolve(SECTIONS_FILENAME);
			InputStream sectionsIn = Files.newInputStream(sectionsPath);
			Path rupturesPath = dir.resolve(RUPTURES_FILENAME);
			InputStream rupturesIn = Files.newInputStream(rupturesPath);

			SystemFaultParser faultParser = SystemFaultParser.create(sax);
			builder.sourceSet(faultParser.parse(sectionsIn, rupturesIn, gmmSet));

			Path gridSourcePath = dir.resolve(GRIDSOURCE_FILENAME);
			InputStream gridIn = Files.newInputStream(gridSourcePath);
			GridSourceSet gridSet = GridParser.create(sax).parse(gridIn, gmmSet);
			builder.sourceSet(gridSet);
			log.info("   Grid set: " + dir.getFileName() + "/" + GRIDSOURCE_FILENAME);
			log.info("    Sources: " + gridSet.size());
		} catch (Exception e) {
			handleParseException(e, dir);
		}
	}

	private static GmmSet parseGMM(Path path) {
		try {
			InputStream in = Files.newInputStream(path);
			return GmmParser.create(sax).parse(in);
		} catch (Exception e) {
			handleParseException(e, path);
			return null;
		}
	}

	/* This method will exit runtime environment */
	private static void handleConfigException(Exception e) {
		StringBuilder sb = new StringBuilder(LF);
		sb.append("** Config error: ").append(e.getMessage());
		log.log(SEVERE, sb.toString(), e);
		System.exit(1);
	}

	/* This method will exit runtime environment */
	private static void handleParseException(Exception e, Path path) {
		if (e instanceof SAXParseException) {
			SAXParseException spe = (SAXParseException) e;
			StringBuilder sb = new StringBuilder(LF);
			sb.append("** SAX parser error:").append(LF);
			sb.append("**   Path: ").append(path).append(LF);
			sb.append("**   Line: ").append(spe.getLineNumber());
			sb.append(" [").append(spe.getColumnNumber()).append("]").append(LF);
			sb.append("**   Info: ").append(spe.getMessage()).append(LF);
			sb.append("** Exiting **").append(LF).append(LF);
			log.log(SEVERE, sb.toString(), spe);
		} else if (e instanceof SAXException) {
			StringBuilder sb = new StringBuilder(LF);
			sb.append("** Other SAX parsing error **");
			sb.append("** Exiting **").append(LF).append(LF);
			log.log(SEVERE, sb.toString(), e);
		} else if (e instanceof IOException) {
			IOException ioe = (IOException) e;
			StringBuilder sb = new StringBuilder(LF);
			sb.append("** IO error: ").append(ioe.getMessage()).append(LF);
			sb.append("**     Path: ").append(path).append(LF);
			sb.append("** Exiting **").append(LF).append(LF);
			log.log(SEVERE, sb.toString(), ioe);
		} else if (e instanceof UnsupportedOperationException ||
			e instanceof IllegalStateException || e instanceof NullPointerException) {
			StringBuilder sb = new StringBuilder(LF);
			sb.append("** Parsing error: ").append(e.getMessage()).append(LF);
			sb.append("** Exiting **").append(LF).append(LF);
			log.log(SEVERE, sb.toString(), e);
		} else {
			StringBuilder sb = new StringBuilder(LF);
			sb.append("** Unknown parsing error: ").append(e.getMessage()).append(LF);
			sb.append("** Exiting **").append(LF).append(LF);
			log.log(SEVERE, sb.toString(), e);
		}
		System.exit(1);
	}

	/* Prune trailing slash if such exists. */
	private static String cleanZipName(String name) {
		return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
	}
	
//	private static 

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
				s.toLowerCase().endsWith(".xml") && !s.equals(GmmParser.FILE_NAME) &&
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
