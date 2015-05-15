package org.opensha2.gmm;


/**
 * Default wrapper for ground motion prediction equation (GMPE) results.
 * 
 * @author Peter Powers
 */
public class DefaultScalarGroundMotion implements ScalarGroundMotion {

	private double mean;
	private double sigma;
	
	private DefaultScalarGroundMotion(double mean, double sigma) {
		this.mean = mean;
		this.sigma = sigma;
	}
	
	/**
	 * Create a new ground motion container.
	 * 
	 * @param mean ground motion (in natural log units)
	 * @param sigma aleatory uncertainty
	 * @return a new scalar ground motion container.
	 */
	public static DefaultScalarGroundMotion create(double mean, double sigma) {
		return new DefaultScalarGroundMotion(mean, sigma);
	}

	@Override public double mean() { return mean; }
	@Override public double sigma() { return sigma; }

	@Override
	public String toString() {
		return new StringBuilder(getClass().getSimpleName())
			.append(" [Median: ")
			.append(String.format("%.6f", Math.exp(mean)))
			.append(" Sigma: ")
			.append(String.format("%.6f", sigma))
			.append("]")
			.toString();
	}
}
