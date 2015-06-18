package org.opensha2.programs;

import static java.lang.Runtime.getRuntime;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.opensha2.util.TextUtils.NEWLINE;
import static org.opensha2.util.TextUtils.format;

import java.io.IOException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import org.opensha2.calc.CalcConfig;
import org.opensha2.calc.Calcs;
import org.opensha2.calc.HazardResult;
import org.opensha2.calc.Results;
import org.opensha2.calc.Site;
import org.opensha2.eq.model.HazardModel;
import org.opensha2.gmm.Imt;
import org.opensha2.util.Logging;

import com.google.common.base.Optional;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;

/**
 * Entry point for computing a hazard curve at a {@link Site} from a
 * {@link HazardModel}. The {@code main()} method of this class returns mean
 * hazard curves for the model and {@link Imt} specified. For more detailed
 * results at a {@code Site}, consider programmatically using the {@code calc()}
 * methods of this class.
 * 
 * @author Peter Powers
 */
public class HazardCurve {

	private static final int FLUSH_LIMIT = 20;

	/**
	 * Entry point for a hazard curve calculation.
	 * 
	 * <p>Computing hazard curves requires at least 1, and at most 3, arguments.
	 * At a minimum, the path to a model zip file or directory must be
	 * specified. If only a model is supplied, model initialization and
	 * calculation configuration settings are drawn from the config file that
	 * must reside at the root of the model directory.</p>
	 * 
	 * <p>Alternatively, the path to a file with calculation configuration may
	 * also be supplied. A configuration file, whether included with a model or
	 * supplied independently, is assumed to contain the sites of interest for a
	 * calculation. Any calculation settings in a supplied configuration file
	 * will override those included with a model; model initialization settings
	 * will be ignored and must be updated in the config file at the root of the
	 * model to take effect.</p>
	 * 
	 * <p>For long lists of sites, it may be easier to supply a third argument:
	 * the path to a comma-delimited file of site data. Please refer to the
	 * nshmp-haz <a href="https://github.com/usgs/nshmp-haz/wiki">wiki</a> for
	 * comprehensive descriptions of source models, configuration files, and
	 * hazard calculations.</p>
	 * 
	 * @param args
	 * @see <a href="https://github.com/usgs/nshmp-haz/wiki/Building-&-Running">
	 *      nshmp-haz wiki</a>
	 */
	public static void main(String[] args) {
		String status = run(args);
		if (status != null) {
			System.err.print(status);
			// System.exit(1); TODO clean?
		}
		// System.exit(0);
	}

	static String run(String[] args) {
		int argCount = args.length;

		if (argCount < 1 || argCount > 3) {
			return USAGE;
		}

		Logging.init();
		Logger log = Logger.getLogger(HazardCurve.class.getName());

		try {

			log.info("Hazard curve: init...");
			Path modelPath = Paths.get(args[0]);
			HazardModel model = HazardModel.load(modelPath);

			CalcConfig config = model.config();
			if (argCount > 1) {
				config = CalcConfig.builder()
					.copy(model.config())
					.extend(CalcConfig.builder(Paths.get(args[1])))
					.build();
			}
			log.info(config.toString());

			Iterable<Site> sites = config.sites();
			if (argCount > 2) {
				Path sitePath = Paths.get(args[2]);
				sites = Site.fromCsv(sitePath);
				log.info("");
				StringBuilder sb = new StringBuilder()
					.append("Site config:")
					.append(format("resource")).append(sitePath)
					.append(format("(override) sites"))
					.append(sites);
				log.info(sb.toString());
			}

			calc(model, config, sites, log);
			return null;

		} catch (Exception e) {
			return new StringBuilder()
				.append(NEWLINE)
				.append("Hazard Curve: error").append(NEWLINE)
				.append("   Arguments: ").append(Arrays.toString(args)).append(NEWLINE)
				.append(NEWLINE)
				.append(Throwables.getStackTraceAsString(e)).append(NEWLINE)
				.append(NEWLINE)
				.append(USAGE)
				.toString();
		}
	}

	private static final OpenOption[] WRITE_OPTIONS = new OpenOption[] {};
	private static final OpenOption[] APPEND_OPTIONS = new OpenOption[] { APPEND };

	/*
	 * Compute hazard curves using the supplied model, config, and site files.
	 */
	private static void calc(
			HazardModel model,
			CalcConfig config,
			Iterable<Site> sites,
			Logger log) throws IOException {

		ExecutorService execSvc = createExecutor();
		Optional<Executor> executor = Optional.<Executor> of(execSvc);

		log.info("Hazard Curve: calculating ...");
		Stopwatch batchWatch = Stopwatch.createUnstarted();
		Stopwatch totalWatch = Stopwatch.createUnstarted();
		int count = 0;

		List<HazardResult> results = new ArrayList<>();
		boolean firstBatch = true;
		Path dir = Paths.get(StandardSystemProperty.USER_DIR.value());

		for (Site site : sites) {
			HazardResult result = calc(model, config, site, executor);
			results.add(result);

			if (results.size() == FLUSH_LIMIT) {
				OpenOption[] opts = firstBatch ? WRITE_OPTIONS : APPEND_OPTIONS;
				firstBatch = false;
				Results.writeResults(dir, results, opts);
				log.info("   " + count + "  batch: " + batchWatch + "  total: " + totalWatch);
				results.clear();
				batchWatch.reset();
			}

			count++;
		}
		// write final batch
		if (!results.isEmpty()) {
			OpenOption[] opts = firstBatch ? WRITE_OPTIONS : APPEND_OPTIONS;
			Results.writeResults(dir, results, opts);
		}
		log.info("Hazard Curve: " + count + " complete " + totalWatch);

		execSvc.shutdown();
	}

	/**
	 * Compute hazard curves at a {@code site} for a {@code model} and
	 * {@code config}. If an {@code executor} is supplied, it will be used to
	 * distribute tasks; otherwise, one will be created.
	 * 
	 * <p><b>Note:</b> any model initialization settings in {@code config} will
	 * be ignored as the supplied model will already have been initialized.</p>
	 * 
	 * @param model to use
	 * @param config calculation configuration
	 * @param site of interest
	 * @param executor to use ({@link Optional})
	 * @return a HazardResult
	 */
	public static HazardResult calc(
			HazardModel model,
			CalcConfig config,
			Site site,
			Optional<Executor> executor) {

		// TODO does this even need to be public?

		Executor ex = executor.or(createExecutor());

		try {
			HazardResult result = Calcs.hazardCurve(model, config, site, ex);
			if (!executor.isPresent()) ((ExecutorService) ex).shutdown();
			return result;
		} catch (ExecutionException | InterruptedException e) {
			Throwables.propagate(e);
			return null;
		}
	}

	private static ExecutorService createExecutor() {
		return newFixedThreadPool(getRuntime().availableProcessors());
	}

	private static final String USAGE_COMMAND = "java -cp nshmp-haz.jar org.opensha.programs.HazardCurve model [config [sites]]";
	private static final String USAGE_URL1 = "https://github.com/usgs/nshmp-haz/wiki/Earthquake-Source-Models";
	private static final String USAGE_URL2 = "https://github.com/usgs/nshmp-haz/wiki/Hazard-Calculations";

	static final String USAGE = new StringBuilder()
		.append("HazardCurve usage:").append(NEWLINE)
		.append("  ").append(USAGE_COMMAND).append(NEWLINE)
		.append(NEWLINE)
		.append("Where:").append(NEWLINE)
		.append("  'model' is a model zip file or directory").append(NEWLINE)
		.append("  'config' supplies a calculation configuration").append(NEWLINE)
		.append("  'sites' is a comma-delimited site data file").append(NEWLINE)
		.append(NEWLINE)
		.append("For more information, see:").append(NEWLINE)
		.append("  ").append(USAGE_URL1).append(NEWLINE)
		.append("  ").append(USAGE_URL2).append(NEWLINE)
		.toString();
}
