package gov.usgs.earthquake.nshmp.eq.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshmp.eq.model.SystemParser.GRIDSOURCE_FILENAME;
import static gov.usgs.earthquake.nshmp.eq.model.SystemParser.RUPTURES_FILENAME;
import static gov.usgs.earthquake.nshmp.eq.model.SystemParser.SECTIONS_FILENAME;
import static gov.usgs.earthquake.nshmp.internal.TextUtils.NEWLINE;
import static java.nio.file.Files.newDirectoryStream;

import java.io.FileNotFoundException;
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

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import gov.usgs.earthquake.nshmp.calc.CalcConfig;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel.Builder;

/**
 * {@code HazardModel} loader. This class logs information relevant to the many
 * various exceptions, both checked and unchecked, that may be thrown when
 * initializing a {@code HazardModel}.
 *
 * @author Peter Powers
 */
class Loader {

  static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static Logger log;

  static {
    log = Logger.getLogger(Loader.class.getName());
  }

  /**
   * Load a {@code HazardModel}. Supplied path should be an absolute path to a
   * directory containing sub-directories by {@code SourceType}s, or the
   * absolute path to a zipped model.
   *
   * <p>This method is not thread safe. This method wraps all {@code Runtime}
   * and other exceptions in {@code IO} and {@code SAXException}s.
   *
   * @param path to model directory or Zip file (absolute)
   * @return a newly created {@code HazardModel}
   */
  static HazardModel load(Path path) throws IOException, SAXException {

    try {

      SAXParser sax = SAXParserFactory.newInstance().newSAXParser();

      if (!Files.exists(path)) {
        String mssg = String.format("Specified model does not exist: %s", path);
        throw new FileNotFoundException(mssg);
      }

      HazardModel.Builder builder = HazardModel.builder();
      List<Path> typePaths = null;
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

      log.info("");
      HazardModel model = builder.build();
      return model;

    } catch (URISyntaxException | ParserConfigurationException oe) {
      log.severe(NEWLINE + "** Error loading model **");
      throw new IOException(oe);
    } catch (Exception e) {
      log.severe(NEWLINE + "** Error loading model **");
      throw e;
    }
  }

  private static final Map<String, String> ZIP_ENV_MAP = ImmutableMap.of(
      "create",
      "false",
      "encoding",
      "UTF-8");

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

  private static void processTypeDir(
      Path typeDir,
      Builder builder,
      ModelConfig modelConfig,
      SAXParser sax) throws IOException, SAXException {

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
    if (typePaths.size() > 0 && !Files.exists(gmmPath)) {
      String mssg = gmmErrorMessage(typeDir.getFileName());
      throw new FileNotFoundException(mssg);
    }

    // having checked directory state, load gmms if present
    // we may have gmm.xml but no source files
    if (Files.exists(gmmPath)) {
      log.info("Parsing: " + typeDir.getParent().relativize(gmmPath));
      gmmSet = parseGMM(gmmPath, sax);
    }

    for (Path sourcePath : typePaths) {
      log.info("Parsing: " + typeDir.getParent().relativize(sourcePath));
      SourceSet<? extends Source> sourceSet = parseSource(type, sourcePath, gmmSet, config, sax);
      builder.sourceSet(sourceSet);
    }

    try (DirectoryStream<Path> ds =
        Files.newDirectoryStream(typeDir, NestedDirFilter.INSTANCE)) {
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

  private static void processNestedDir(
      Path sourceDir,
      SourceType type,
      GmmSet gmmSet,
      Builder builder,
      ModelConfig parentConfig,
      SAXParser sax) throws IOException, SAXException {

    /*
     * gmm.xml -- this MUST exist if there is at least one source file and there
     * is no gmm.xml file in the parent source type directory
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
      if (!Files.exists(nestedGmmPath) && gmmSet == null) {
        String mssg = gmmErrorMessage(sourceDir.getFileName());
        throw new FileNotFoundException(mssg);
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

  private static SourceSet<? extends Source> parseSource(
      SourceType type,
      Path path,
      GmmSet gmmSet,
      ModelConfig config,
      SAXParser sax) throws IOException, SAXException {

    try (InputStream in = Files.newInputStream(path)) {
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

  private static void parseSystemSource(
      Path dir,
      GmmSet gmmSet,
      Builder builder,
      ModelConfig config,
      SAXParser sax) throws IOException, SAXException {

    log.info("");
    try {
      Path sectionsPath = dir.resolve(SECTIONS_FILENAME);
      Path rupturesPath = dir.resolve(RUPTURES_FILENAME);
      if (Files.exists(sectionsPath) && Files.exists(rupturesPath)) {
        InputStream sectionsIn = Files.newInputStream(sectionsPath);
        InputStream rupturesIn = Files.newInputStream(rupturesPath);

        SystemParser systemParser = SystemParser.create(sax);
        builder.sourceSet(systemParser.parse(sectionsIn, rupturesIn, gmmSet));
      } else {
        log.info("Fault model: (no fault sources supplied with system)");
      }

      log.info("");
      Path gridSourcePath = dir.resolve(GRIDSOURCE_FILENAME);
      if (Files.exists(gridSourcePath)) {
        InputStream gridIn = Files.newInputStream(gridSourcePath);
        GridSourceSet gridSet = GridParser.create(sax).parse(gridIn, gmmSet, config);
        builder.sourceSet(gridSet);
        log.info(" Grid model: " + dir.getFileName() + "/" + GRIDSOURCE_FILENAME);
        log.info("     Weight: " + gridSet.weight());
        log.info("    Sources: " + gridSet.size());
      } else {
        log.info(" Grid model: (no grid sources supplied with system)");
      }
    } catch (Exception e) {
      handleParseException(e, dir);
    }
  }

  private static GmmSet parseGMM(Path path, SAXParser sax)
      throws IOException, SAXException {
    try {
      InputStream in = Files.newInputStream(path);
      return GmmParser.create(sax).parse(in);
    } catch (Exception e) {
      handleParseException(e, path);
      return null;
    }
  }

  /* Called from all *parse*() methods. */
  private static void handleParseException(Exception e, Path path)
      throws IOException, SAXException {

    StringBuilder sb = new StringBuilder(NEWLINE);
    sb.append("** Parsing error: ");
    if (e instanceof SAXParseException) {
      SAXParseException spe = (SAXParseException) e;
      sb.append("SAX parser **").append(NEWLINE);
      sb.append("**   Path: ").append(path).append(NEWLINE);
      sb.append("**   Line: ").append(spe.getLineNumber());
      sb.append(" [").append(spe.getColumnNumber()).append("]").append(NEWLINE);
      sb.append("**   Info: ").append(spe.getMessage());
      log.severe(sb.toString());
      throw spe;
    } else if (e instanceof SAXException) {
      sb.append("Other SAX error **");
      log.severe(sb.toString());
      throw (SAXException) e;
    } else if (e instanceof IOException) {
      IOException ioe = (IOException) e;
      sb.append("IO error ** ").append(NEWLINE);
      sb.append("**     Path: ").append(path);
      log.severe(sb.toString());
      throw ioe;
    } else {
      sb.append("Other error **");
      log.severe(sb.toString());
      throw new SAXException(e);
    }
  }

  /* Prune trailing slash if such exists. */
  private static String cleanZipName(String name) {
    return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
  }

  private static String gmmErrorMessage(Path dir) {
    return dir + " sources present. Where is gmm.xml?";
  }

  /*
   * Only lists those directories matching a SourceType.
   */
  private static enum TypeFilter implements DirectoryStream.Filter<Path> {
    INSTANCE;
    @Override
    public boolean accept(Path path) throws IOException {
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
    @Override
    public boolean accept(Path path) throws IOException {
      return !path.getFileName().toString().startsWith("__");
    }
  }

  /*
   * Filters source XML files, skipping hidden files, those that start with a
   * tilde (~), and any gmm.xml files.
   */
  private static enum SourceFilter implements DirectoryStream.Filter<Path> {
    INSTANCE;
    @Override
    public boolean accept(Path path) throws IOException {
      String s = path.getFileName().toString();
      return Files.isRegularFile(path) && !Files.isHidden(path) &&
          s.toLowerCase().endsWith(".xml") && !s.equals(GmmParser.FILE_NAME) &&
          !s.startsWith("~");
    }
  }

  /*
   * Filters nested source directories, skipping hidden directories, those that
   * start with a tilde (~), or that are named 'sources' that may exist in grid,
   * area, or slab type directories.
   */
  private static enum NestedDirFilter implements DirectoryStream.Filter<Path> {
    INSTANCE;
    @Override
    public boolean accept(Path path) throws IOException {
      String s = path.getFileName().toString();
      return Files.isDirectory(path) && !Files.isHidden(path) && !s.startsWith("~") &&
          !s.equals(GridParser.RATE_DIR);
    }
  }

}
