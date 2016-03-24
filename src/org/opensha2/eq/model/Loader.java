package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static java.nio.file.Files.newDirectoryStream;
import static java.util.logging.Level.SEVERE;
import static org.opensha2.eq.model.SystemParser.GRIDSOURCE_FILENAME;
import static org.opensha2.eq.model.SystemParser.RUPTURES_FILENAME;
import static org.opensha2.eq.model.SystemParser.SECTIONS_FILENAME;

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
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.opensha2.calc.CalcConfig;
import org.opensha2.eq.model.HazardModel.Builder;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
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

	static {
		log = Logger.getLogger(Loader.class.getName());
	}

	/**
	 * Load a {@code HazardModel}. Supplied path should be an absolute path to a
	 * directory containing sub-directories by {@code SourceType}s, or the
	 * absolute path to a zipped model.
	 * 
	 * <p>This method is not thread safe. Any exceptions thrown while loading
	 * will be logged and the JVM will exit.</p>
	 * 
	 * @param path to model directory or Zip file (absolute)
	 * @return a newly created {@code HazardModel}
	 */
	static HazardModel load(Path path) {

		SAXParser sax = null;
		try {
			sax = SAXParserFactory.newInstance().newSAXParser();
		} catch (ParserConfigurationException | SAXException e) {
			Throwables.propagate(e);
		}

		HazardModel.Builder builder = HazardModel.builder();
		List<Path> typePaths = null;

		try {
			
			checkArgument(Files.exists(path), "Specified model does not exist: %s", path);
			Path typeDirPath = typeDirectory(path);

			ModelConfig modelConfig = ModelConfig.Builder.fromFile(typeDirPath).build();
			log.info(modelConfig.toString());

			CalcConfig calcConfig = CalcConfig.Builder.withDefaults()
					.extend(CalcConfig.Builder.fromFile(typeDirPath))
					.build();
			builder.config(calcConfig);

			typePaths = typeDirectoryList(typeDirPath);
			checkState(typePaths.size() > 0, "Empty model: %s", path.getFileName());
			builder.name(modelConfig.name);

			for (Path typePath : typePaths) {
				String typeName = cleanZipName(typePath.getFileName().toString());
				log.info("");
				log.info("=======  " + typeName + " Sources  =======");
				processTypeDir(typePath, builder, modelConfig, sax);
				log.info("==========================" + Strings.repeat("=", typeName.length()));
			}

		} catch (IOException | URISyntaxException e) {
			handleConfigException(e);
		}

		log.info("");
		HazardModel model = builder.build();

		return model;
	}

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
			checkArgument(Files.isDirectory(nestedTypeDir), "No nested directory in zip: %s",
				nestedTypeDir);
			return nestedTypeDir;
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

	private static void processTypeDir(Path typeDir, Builder builder, ModelConfig modelConfig,
			SAXParser sax) throws IOException {

		String typeName = cleanZipName(typeDir.getFileName().toString());
		SourceType type = SourceType.fromString(typeName);

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

		// load alternate config if such exists
		ModelConfig config = modelConfig;
		Path configPath = typeDir.resolve(ModelConfig.FILE_NAME);
		if (Files.exists(configPath)) {
			config = ModelConfig.Builder.copyOf(modelConfig)
				.extend(ModelConfig.Builder.fromFile(configPath))
				.build();
			log.info("(override) " + config.toString());
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
			gmmSet = parseGMM(gmmPath, sax);
		}

		for (Path sourcePath : typePaths) {
			log.info("Parsing: " + typeDir.getParent().relativize(sourcePath));
			SourceSet<? extends Source> sourceSet = parseSource(type, sourcePath, gmmSet, config,
				sax);
			builder.sourceSet(sourceSet);
		}

		try (DirectoryStream<Path> ds = Files.newDirectoryStream(typeDir, NestedDirFilter.INSTANCE)) {
			boolean firstDir = true;
			for (Path nestedSourceDir : ds) {
				if (firstDir) {
					log.info("");
					log.info("========  Nested " + typeName + " Sources  ========");
					firstDir = false;
				}
				processNestedDir(nestedSourceDir, type, gmmSet, builder, config, sax);
			}
		}
	}

	private static void processNestedDir(Path sourceDir, SourceType type, GmmSet gmmSet,
			Builder builder, ModelConfig parentConfig, SAXParser sax) throws IOException {

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
		ModelConfig nestedConfig = parentConfig;
		if (nestedSourcePaths.size() > 0) {

			// config
			Path nestedConfigPath = sourceDir.resolve(ModelConfig.FILE_NAME);
			if (Files.exists(nestedConfigPath)) {
				nestedConfig = ModelConfig.Builder.copyOf(parentConfig)
					.extend(ModelConfig.Builder.fromFile(nestedConfigPath))
					.build();
				log.info("(override) " + nestedConfig.toString());
			}

			// gmm
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
				nestedGmmSet = parseGMM(nestedGmmPath, sax);
			} else {
				log.info("(using parent gmm.xml)");
				nestedGmmSet = gmmSet;
			}

		}

		if (type == SourceType.SYSTEM) {
			log.info("Parsing: " + typeDir.relativize(sourceDir));
			parseSystemSource(sourceDir, nestedGmmSet, builder, nestedConfig, sax);
		} else {
			for (Path sourcePath : nestedSourcePaths) {
				log.info("Parsing: " + typeDir.relativize(sourcePath));
				SourceSet<? extends Source> sourceSet = parseSource(type, sourcePath, nestedGmmSet,
					nestedConfig, sax);
				builder.sourceSet(sourceSet);
			}
		}
	}

	private static SourceSet<? extends Source> parseSource(SourceType type, Path path,
			GmmSet gmmSet, ModelConfig config, SAXParser sax) {
		try {
			InputStream in = Files.newInputStream(path);
			switch (type) {
				case AREA:
					return AreaParser.create(sax).parse(in, gmmSet, config);
				case CLUSTER:
					return ClusterParser.create(sax).parse(in, gmmSet, config);
				case FAULT:
					return FaultParser.create(sax).parse(in, gmmSet, config);
				case GRID:
					return GridParser.create(sax).parse(in, gmmSet, config);
				case INTERFACE:
					return InterfaceParser.create(sax).parse(in, gmmSet, config);
				case SLAB:
					return SlabParser.create(sax).parse(in, gmmSet, config);
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

	private static void parseSystemSource(Path dir, GmmSet gmmSet, Builder builder,
			ModelConfig config, SAXParser sax) {
		try {
			Path sectionsPath = dir.resolve(SECTIONS_FILENAME);
			InputStream sectionsIn = Files.newInputStream(sectionsPath);
			Path rupturesPath = dir.resolve(RUPTURES_FILENAME);
			InputStream rupturesIn = Files.newInputStream(rupturesPath);

			SystemParser faultParser = SystemParser.create(sax);
			builder.sourceSet(faultParser.parse(sectionsIn, rupturesIn, gmmSet));

			Path gridSourcePath = dir.resolve(GRIDSOURCE_FILENAME);
			InputStream gridIn = Files.newInputStream(gridSourcePath);
			GridSourceSet gridSet = GridParser.create(sax).parse(gridIn, gmmSet, config);
			builder.sourceSet(gridSet);
			log.info("   Grid set: " + dir.getFileName() + "/" + GRIDSOURCE_FILENAME);
			log.info("    Sources: " + gridSet.size());
		} catch (Exception e) {
			handleParseException(e, dir);
		}
	}

	private static GmmSet parseGMM(Path path, SAXParser sax) {
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
		sb.append("** Configuration error: ").append(e.getMessage());
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
	 * Skips __MACOSX resource fork files that creep into zip files. Others?
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
