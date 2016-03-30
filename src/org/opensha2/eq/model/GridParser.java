package org.opensha2.eq.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.logging.Level.FINE;
import static org.opensha2.eq.model.SourceAttribute.FOCAL_MECH_MAP;
import static org.opensha2.eq.model.SourceAttribute.ID;
import static org.opensha2.eq.model.SourceAttribute.MAG_DEPTH_MAP;
import static org.opensha2.eq.model.SourceAttribute.MAX_DEPTH;
import static org.opensha2.eq.model.SourceAttribute.NAME;
import static org.opensha2.eq.model.SourceAttribute.RUPTURE_SCALING;
import static org.opensha2.eq.model.SourceAttribute.STRIKE;
import static org.opensha2.eq.model.SourceAttribute.TYPE;
import static org.opensha2.eq.model.SourceAttribute.WEIGHT;
import static org.opensha2.eq.model.SourceType.GRID;
import static org.opensha2.util.Parsing.readDouble;
import static org.opensha2.util.Parsing.readEnum;
import static org.opensha2.util.Parsing.readInt;
import static org.opensha2.util.Parsing.readString;
import static org.opensha2.util.Parsing.stringToEnumWeightMap;
import static org.opensha2.util.Parsing.stringToValueValueWeightMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.opensha2.data.Data;
import org.opensha2.data.XySequence;
import org.opensha2.eq.Magnitudes;
import org.opensha2.eq.fault.FocalMech;
import org.opensha2.eq.fault.surface.RuptureScaling;
import org.opensha2.eq.model.MfdHelper.GR_Data;
import org.opensha2.eq.model.MfdHelper.IncrData;
import org.opensha2.eq.model.MfdHelper.SingleData;
import org.opensha2.eq.model.MfdHelper.TaperData;
import org.opensha2.geo.Location;
import org.opensha2.mfd.IncrementalMfd;
import org.opensha2.mfd.MfdType;
import org.opensha2.mfd.Mfds;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.Iterables;
import com.google.common.primitives.Doubles;

/*
 * Non-validating grid source parser. SAX parser 'Attributes' are stateful and
 * cannot be stored. This class is not thread safe.
 * 
 * @author Peter Powers
 */
@SuppressWarnings("incomplete-switch")
class GridParser extends DefaultHandler {

	private final Logger log = Logger.getLogger(GridParser.class.getName());
	private final SAXParser sax;
	private boolean used = false;

	private Locator locator;

	// Default MFD data
	private boolean parsingDefaultMFDs = false;
	private MfdHelper.Builder mfdHelperBuilder;
	private MfdHelper mfdHelper;

	private GmmSet gmmSet;

	private ModelConfig config;

	private GridSourceSet sourceSet;
	private GridSourceSet.Builder sourceSetBuilder;

	// master magnitude list data
	private double minMag = Magnitudes.MAX_MAG;
	private double maxMag = Magnitudes.MIN_MAG;
	private double deltaMag;

	// Node locations are the only text content in source files
	private boolean readingLoc = false;
	private StringBuilder locBuilder = null;

	// TODO why are these fields being initialized to null; necessary?

	// Per-node MFD and mechMap
	private XySequence nodeMFD = null;
	private Map<FocalMech, Double> nodeMechMap = null;

	// Exposed for use when validating depths in subclasses
	SourceType type = GRID;

	private GridParser(SAXParser sax) {
		this.sax = checkNotNull(sax);
	}

	static GridParser create(SAXParser sax) {
		return new GridParser(sax);
	}

	GridSourceSet parse(InputStream in, GmmSet gmmSet, ModelConfig config) throws SAXException,
			IOException {
		checkState(!used, "This parser has expired");
		this.gmmSet = gmmSet;
		this.config = config;
		sax.parse(in, this);
		used = true;
		return sourceSet;
	}

	@Override public void startElement(String uri, String localName, String qName, Attributes atts)
			throws SAXException {

		SourceElement e = null;
		try {
			e = SourceElement.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
		}
		switch (e) {

			case GRID_SOURCE_SET:
				String name = readString(NAME, atts);
				int id = readInt(ID, atts);
				double weight = readDouble(WEIGHT, atts);
				sourceSetBuilder = new GridSourceSet.Builder();
				sourceSetBuilder
					.name(name)
					.id(id)
					.weight(weight);
				sourceSetBuilder.gmms(gmmSet);
				if (log.isLoggable(FINE)) {
					log.fine("");
					log.fine("       Name: " + name);
					log.fine("     Weight: " + weight);
				}
				mfdHelperBuilder = MfdHelper.builder();
				mfdHelper = mfdHelperBuilder.build(); // dummy; usually overwritten
				break;

			case DEFAULT_MFDS:
				parsingDefaultMFDs = true;
				break;

			case INCREMENTAL_MFD:
				if (parsingDefaultMFDs) {
					mfdHelperBuilder.addDefault(atts);
				}
				break;

			case SOURCE_PROPERTIES:
				String depthMapStr = readString(MAG_DEPTH_MAP, atts);
				NavigableMap<Double, Map<Double, Double>> depthMap = stringToValueValueWeightMap(depthMapStr);
				double maxDepth = readDouble(MAX_DEPTH, atts);
				String mechMapStr = readString(FOCAL_MECH_MAP, atts);
				Map<FocalMech, Double> mechMap = stringToEnumWeightMap(mechMapStr, FocalMech.class);
				RuptureScaling rupScaling = readEnum(RUPTURE_SCALING, atts, RuptureScaling.class);
				sourceSetBuilder
					.depthMap(depthMap, type)
					.maxDepth(maxDepth, type)
					.mechs(mechMap)
					.ruptureScaling(rupScaling);
				double strike = readDouble(STRIKE, atts);
				// first validate strike by setting it in builder
				sourceSetBuilder.strike(strike);
				// then possibly override type if strike is set
				PointSourceType type = config.pointSourceType;
				if (!Double.isNaN(strike)) type = PointSourceType.FIXED_STRIKE;
				sourceSetBuilder.sourceType(type);
				if (log.isLoggable(FINE)) {
					log.fine("     Depths: " + depthMap);
					log.fine("  Max depth: " + maxDepth);
					log.fine("Focal mechs: " + mechMap);
					log.fine("Rup scaling: " + rupScaling);
					log.fine("     Strike: " + strike);
					String typeOverride = (type != config.pointSourceType) ? " (" +
						config.pointSourceType + " overridden)" : "";
					log.fine("Source type: " + type + typeOverride);
				}
				break;

			case NODE:
				readingLoc = true;
				locBuilder = new StringBuilder();
				nodeMFD = processNode(atts);
				minMag = Math.min(minMag, nodeMFD.min().x());
				maxMag = Math.max(maxMag, nodeMFD.max().x());
				try {
					String nodeMechMapStr = readString(FOCAL_MECH_MAP, atts);
					nodeMechMap = stringToEnumWeightMap(nodeMechMapStr, FocalMech.class);
				} catch (NullPointerException npe) {
					nodeMechMap = null;
				}
				break;
				
				/*
				 * TODO we need to check that delta mag, if included in a node, is
				 * consistent with deltaMag of all default MFDs. Or, we check that
				 * all defaults are consistent and don't permit inclusion of deltaMag
				 * as node attribute. The same could be done for mMin. This ensures a
				 * basic consistency of structure.
				 */
		}
	}

	@Override public void endElement(String uri, String localName, String qName)
			throws SAXException {

		SourceElement e = null;
		try {
			e = SourceElement.fromString(qName);
		} catch (IllegalArgumentException iae) {
			throw new SAXParseException("Invalid element <" + qName + ">", locator, iae);
		}

		switch (e) {

			case DEFAULT_MFDS:
				parsingDefaultMFDs = false;
				mfdHelper = mfdHelperBuilder.build();
				break;

			case NODE:
				readingLoc = false;
				Location loc = Location.fromString(locBuilder.toString());
				if (nodeMechMap != null) {
					sourceSetBuilder.location(loc, nodeMFD, nodeMechMap);
				} else {
					sourceSetBuilder.location(loc, nodeMFD);
				}
				nodeMFD = null;
				nodeMechMap = null;
				break;

			case GRID_SOURCE_SET:
				sourceSetBuilder.mfdData(minMag, maxMag, deltaMag);
				sourceSet = sourceSetBuilder.build();

				if (log.isLoggable(FINE)) {
					// TODO there must be a better way to organize this so that we
					// can log the depth model without having to give it package vis
					log.fine("       Size: " + sourceSet.size());
					log.finer("  MFD count: " + mfdHelper.size());
					log.finer("  Mag count: " + sourceSet.depthModel.magMaster.size());
					log.finer(" Mag master: " + sourceSet.depthModel.magMaster);
					log.finer("  MFD index: " + sourceSet.depthModel.magDepthIndices);
					log.finer("     Depths: " + sourceSet.depthModel.magDepthDepths);
					log.finer("    Weights: " + sourceSet.depthModel.magDepthWeights);
					log.fine("");
				}
				break;

		}
	}

	@Override public void characters(char ch[], int start, int length) throws SAXException {
		if (readingLoc) locBuilder.append(ch, start, length);
	}

	@Override public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}

	/*
	 * Currently, grid sources may have multiple defaults of a uniform
	 * type. No checking is done to see if node types match defaults.
	 * Defaults are collapsed into a single MFD.
	 */
	
	private XySequence processNode(Attributes atts) {
		MfdType type = readEnum(TYPE, atts, MfdType.class);

		switch (type) {
			case GR:
				return buildCollapsedGR(atts);

			case INCR:
				return buildIncr(atts);

			case SINGLE:
				return buildCollapsedSingle(atts);

			case GR_TAPER:
				return buildTapered(atts);

			default:
				throw new IllegalStateException(type + " not yet implemented");
		}
	}

	private XySequence buildGR(Attributes atts) {
		List<GR_Data> grDataList = mfdHelper.grData(atts);
		GR_Data grData = grDataList.get(0);
		deltaMag = grData.dMag;
		return Mfds.toSequence(buildGR(grData));
	}
	
	private XySequence buildCollapsedGR(Attributes atts) {
		List<GR_Data> dataList = mfdHelper.grData(atts);
		// validate callapsability
		GR_Data grModel = dataList.get(0);
		double mMin = grModel.mMin;
		double dMag = grModel.dMag;
		double mMax = grModel.mMax;
		for (GR_Data grData : Iterables.skip(dataList, 1)) {
			checkState(grData.mMin == mMin, "All mMin must be equal");
			checkState(grData.dMag == dMag, "All dMag must be equal");
			mMax = Math.max(grData.mMax, mMax);
		}
		
		deltaMag = dMag;
		
		double[] mags = Data.buildCleanSequence(mMin, mMax, dMag, true, 2);
		double[] rates = new double[mags.length];
		
		for (GR_Data grData : dataList) {
			IncrementalMfd mfd = buildGR(grData);
			List<Double> mfdRates = mfd.yValues();
			for (int i=0; i<mfdRates.size(); i++) {
				rates[i] += mfdRates.get(i);
			}
		}
		
		return XySequence.createImmutable(mags, rates);
	}
	
	private static IncrementalMfd buildGR(GR_Data grData) {
		int nMagGR = Mfds.magCount(grData.mMin, grData.mMax, grData.dMag);
		IncrementalMfd mfdGR = Mfds.newGutenbergRichterMFD(grData.mMin, grData.dMag,
			nMagGR, grData.b, 1.0);
		mfdGR.scaleToIncrRate(grData.mMin, Mfds.incrRate(grData.a, grData.b, grData.mMin) *
			grData.weight);
		return mfdGR;
	}
		
	// TODO are there circumstances under which one would
	// combine multiple INCR MFDs??
	private  XySequence buildIncr(Attributes atts) {
		List<IncrData> incrDataList = mfdHelper.incrementalData(atts);
		IncrData incrData = incrDataList.get(0);
		deltaMag = incrData.mags[1] - incrData.mags[0];
		return Mfds.toSequence(buildIncr(incrData));
	}

	private static IncrementalMfd buildIncr(IncrData incrData) {
		IncrementalMfd mfdIncr = Mfds.newIncrementalMFD(incrData.mags,
			Data.multiply(incrData.weight, incrData.rates));
		return mfdIncr;
	}
	
	private XySequence buildSingle(Attributes atts) {
		List<SingleData> singleDataList = mfdHelper.singleData(atts);
		SingleData singleData = singleDataList.get(0);
		deltaMag = 0.0;
		return Mfds.toSequence(buildSingle(singleData));
	}
	
	private XySequence buildCollapsedSingle(Attributes atts) {
		List<SingleData> dataList = mfdHelper.singleData(atts);

		double[] mags = new double[dataList.size()];
		double[] rates = new double[mags.length];
		
		for (int i=0; i<dataList.size(); i++) {
			SingleData data = dataList.get(i);
			mags[i] = data.m;
			rates[i] = data.rate * data.weight;
		}
		
		return XySequence.createImmutable(mags, rates);
	}
	
	private static IncrementalMfd buildSingle(SingleData singleData) {
		return Mfds.newSingleMFD(singleData.m, singleData.rate * singleData.weight,
			singleData.floats);
	}
	
	private XySequence buildTapered(Attributes atts) {
		List<TaperData> taperDataList = mfdHelper.taperData(atts);
		TaperData taperData = taperDataList.get(0);
		deltaMag = taperData.dMag;
		return Mfds.toSequence(buildTapered(taperData));
	}
	
	private static IncrementalMfd buildTapered(TaperData taperData) {
		int nMagTaper = Mfds.magCount(taperData.mMin, taperData.mMax, taperData.dMag);
		IncrementalMfd mfdTaper = Mfds.newTaperedGutenbergRichterMFD(taperData.mMin,
			taperData.dMag, nMagTaper, taperData.a, taperData.b, taperData.cMag,
			taperData.weight);
		return mfdTaper;
	}
	
}
