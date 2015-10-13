package org.opensha2.calc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.opensha2.data.DataUtils;
import org.opensha2.eq.model.Source;
import org.opensha2.eq.model.SourceSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.math.DoubleMath;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class DeaggResult {

	private String id;

	private double returnPeriod;
	private double iml;

	private transient Data rawData;
	
	private double minmag;
	private double maxmag;
	private double deltaMag;
	
	private double mindistance;
	private double maxdistance;
	private double deltadistance;
	
	private double[] εbins;
	
	private List<Bin> data = new ArrayList<>();
	/*
	 * rBins and mBins are bin centers εBins are upper bin edges so there is one
	 * extra bin
	 */
//	private double[] rBins, mBins, εBins;
//
//	private double[][][] rmεMatrix; // [r][m][ε] DataVolume
//
//	private double rBar, mBar, εBar;
////	private double totalRate;
//
//	// wieghted m and r position data
//	private double[][] rPosValues; // DataTable
//	private double[][] mPosValues; // DataTable
//	// private double[][] rmPosWeights;
//	// private double[][] rWtPosValues; // DataTable
//	// private double[][] mWtPosValues; // DataTable

	List<SourceTypeContribution> primarySourceTypes;
	List<SourceContribution> primarySources;

	// private Map<SourceSet<? extends Source>, Double>
	// contributorsBySourceType;
	// private Map<SourceSet<? extends Source>, List<Source>>
	// contributorsBySource;
	// private Map<SourceSet<? extends Source>, List<Source>> topContributors;

	
	static class Bin {
		double rbin;
		double rpos;
		double mbin;
		double mpos;
		double[] εvalues;
	}
	
	static class Data {
		private double[] rBins, mBins, εBins;

		private double[][][] rmεMatrix; // [r][m][ε] DataVolume

		private double rBar, mBar, εBar;
//		private double totalRate;

		// wieghted m and r position data
		private double[][] rPosValues; // DataTable
		private double[][] mPosValues; // DataTable

	}

	static class SourceTypeContribution {
		String name;
		double contribution;
		int id;
		double rBar;
		double mBar;
		double εBar;

		SourceTypeContribution(String name, double contribution, int id, double rBar, double mBar,
				double εBar) {
			this.name = name;
			this.contribution = contribution;
			this.id = id;
			this.mBar = mBar;
			this.rBar = rBar;
			this.εBar = εBar;
		}
	}

	static class SourceContribution {
		String name;
		double contribution;
		int id;
		double r;
		double m;
		double ε;
		double azimuth;

		SourceContribution(String name, double contribution, int id, double r, double m, double ε,
				double azimuth) {
			this.name = name;
			this.contribution = contribution;
			this.id = id;
			this.m = m;
			this.r = r;
			this.ε = ε;
			this.azimuth = azimuth;
		}
	}

	static DeaggResult demo() {
		DeaggResult result = new DeaggResult();
		result.id = "Total";

		result.returnPeriod = 2475;
		result.iml = 0.65;

		result.rawData = new Data();
		result.rawData.rBins = DataUtils.buildCleanSequence(0.0, 100.0, 10.0, true, 1);
		result.rawData.mBins = DataUtils.buildCleanSequence(5.0, 8.0, 0.2, true, 1);
		result.rawData.εBins = new double[] { -2.0, -1.0, -0.5, 0.0, 0.5, 1.0, 2.0, 3.0 };

		result.rawData.rmεMatrix = new double[result.rawData.rBins.length][result.rawData.mBins.length][result.rawData.εBins.length + 1];
		result.rawData.rmεMatrix[1][1] = new double[] { 0.0, 0.0, 0.0, 2.0, 1.0, 0.0, 0.0, 0.0, 0.0 };
		result.rawData.rmεMatrix[1][2] = new double[] { 0.0, 0.0, 0.0, 0.0, 3.0, 5.0, 1.0, 0.0, 0.0 };
		result.rawData.rmεMatrix[1][3] = new double[] { 0.0, 0.0, 0.0, 1.0, 2.0, 3.0, 2.0, 1.0, 0.0 };
		result.rawData.rmεMatrix[3][4] = new double[] { 0.0, 0.0, 2.0, 4.0, 4.0, 2.0, 0.0, 0.0, 0.0 };
		result.rawData.rmεMatrix[3][6] = new double[] { 0.0, 0.0, 0.0, 3.0, 3.0, 6.0, 4.0, 0.0, 0.0 };
		result.rawData.rmεMatrix[6][12] = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 0.2 };

		result.rawData.rPosValues = new double[result.rawData.rBins.length][result.rawData.mBins.length];
		result.rawData.rPosValues[1][1] = 13.0;
		result.rawData.rPosValues[1][2] = 14.5;
		result.rawData.rPosValues[1][3] = 18.0;
		result.rawData.rPosValues[3][4] = 47.0;
		result.rawData.rPosValues[3][6] = 42.0;
		result.rawData.rPosValues[6][12] = 73.0;

		result.rawData.mPosValues = new double[result.rawData.rBins.length][result.rawData.mBins.length];
		result.rawData.mPosValues[1][1] = 5.28;
		result.rawData.mPosValues[1][2] = 5.52;
		result.rawData.mPosValues[1][3] = 5.73;
		result.rawData.mPosValues[3][4] = 5.88;
		result.rawData.mPosValues[3][6] = 6.34;
		result.rawData.mPosValues[6][12] = 7.52;

		result.primarySourceTypes = ImmutableList.of(
			new SourceTypeContribution("California B-Faults CH", 28.5, -1, 5.0, 7.4, 0.4),
			new SourceTypeContribution("California B-Faults GR", 22.0, -1, 6.2, 6.7, 0.15),
			new SourceTypeContribution("CA Crustal Gridded", 15.0, -1, 7.0, 6.7, -0.2));

		result.primarySources = ImmutableList.of(
			new SourceContribution("Puente Hills", 5.2, 521, 3.2, 7.6, 0.5, 160.1),
			new SourceContribution("Elysian Park", 4.0, 431, 5.6, 6.8, 0.7, 340.0),
			new SourceContribution("San Andreas (Mojave)", 1.2, 44, 32.1, 8.2, 1.5, 22.3));

		result.mindistance = result.rawData.rBins[0];
		result.maxdistance = result.rawData.rBins[result.rawData.rBins.length - 1];
		result.deltadistance = 10.0;

		result.minmag = result.rawData.mBins[0];
		result.maxmag = result.rawData.mBins[result.rawData.mBins.length - 1];
		result.deltaMag = 0.2;
		
		result.εbins = result.rawData.εBins;
		for (int i=0; i<result.rawData.rBins.length; i++) {
			for (int j=0; j< result.rawData.mBins.length; j++) {
				double[] values = result.rawData.rmεMatrix[i][j];
				double rmBinSum = DataUtils.sum(values);
				if (rmBinSum == 0.0) continue;
				
				Bin bin = new Bin();
				bin.rbin = result.rawData.rBins[i];
				bin.mbin = result.rawData.mBins[j];
				bin.rpos = result.rawData.rPosValues[i][j];
				bin.mpos = result.rawData.mPosValues[i][j];
				bin.εvalues = values;
				result.data.add(bin);
			}
		}
		return result;

	}

	static String toJson(DeaggResult dr) {
		Gson gson = new GsonBuilder()
			.disableHtmlEscaping()
			.setPrettyPrinting()
			.create();
		
		return gson.toJson(dr);

	}
	
	public static void main(String[] args) {
		String deaggStr = toJson(demo());
		System.out.println(deaggStr);
		
	}

}
