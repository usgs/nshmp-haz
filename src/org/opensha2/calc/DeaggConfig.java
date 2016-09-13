package org.opensha2.calc;

import static org.opensha2.internal.TextUtils.LOG_INDENT;

import org.opensha2.calc.CalcConfig.Deagg.Bins;
import org.opensha2.gmm.Imt;

/**
 * A deaggregation configuration container. This class provides a reusable
 * builder that comes in handy when iterating over IMTs and only the return
 * period and iml require updating. A unique config is required for each
 * deaggregation performed.
 *
 * Note that this is a class of convenience and assumes that return period and
 * IML are in agreement for the IMT of interest, i.e. one or the other has been
 * correctly derived from the total curve in a hazard object.
 *
 * @author Peter Powers
 */
final class DeaggConfig {

  final Bins bins;
  final double contributorLimit;

  final Imt imt;
  final DeaggDataset model;
  final double iml;
  final double rate;
  final double returnPeriod;
  final ExceedanceModel probabilityModel;
  final double truncation;

  private DeaggConfig(
      Bins bins,
      double contributorLimit,
      Imt imt,
      DeaggDataset model,
      double iml,
      double rate,
      double returnPeriod,
      ExceedanceModel probabilityModel,
      double truncation) {

    this.bins = bins;
    this.contributorLimit = contributorLimit;
    this.imt = imt;
    this.model = model;
    this.iml = iml;
    this.rate = rate;
    this.returnPeriod = returnPeriod;
    this.probabilityModel = probabilityModel;
    this.truncation = truncation;
  }

  @Override
  public String toString() {
    return new StringBuilder("Deagg config:")
        .append(LOG_INDENT)
        .append("imt: ").append(imt.name()).append(" [").append(imt).append("]")
        .append(LOG_INDENT)
        .append("iml: ").append(iml).append(" ").append(imt.units())
        .append(LOG_INDENT)
        .append("rate: ").append(rate).append(" yr⁻¹")
        .append(LOG_INDENT)
        .append("returnPeriod: ").append(returnPeriod).append(" yrs")
        .append(LOG_INDENT)
        .append("probabilityModel: ").append(probabilityModel)
        .append(" [trunc = ").append(truncation).append("]")
        .toString();
  }

  static Builder builder(Hazard hazard) {
    return new Builder()
        .dataModel(
            DeaggDataset.builder(hazard.config).build())
        .probabilityModel(
            hazard.config.curve.exceedanceModel,
            hazard.config.curve.truncationLevel)
        .settings(hazard.config.deagg);
  }

  /* Reusable builder */
  static class Builder {

    private Bins bins;
    private Double contributorLimit;

    private Imt imt;
    private DeaggDataset model;
    private Double iml;
    private Double rate;
    private Double returnPeriod;
    private ExceedanceModel probabilityModel;
    private Double truncation;

    Builder imt(Imt imt) {
      this.imt = imt;
      return this;
    }

    Builder settings(CalcConfig.Deagg settings) {
      this.bins = settings.bins;
      this.contributorLimit = settings.contributorLimit;
      return this;
    }

    Builder dataModel(DeaggDataset model) {
      this.model = model;
      return this;
    }

    /*
     * Supply the target iml along with corresponding annual rate and return
     * period for the IMT of interest.
     */
    Builder iml(double iml, double rate, double returnPeriod) {
      this.iml = iml;
      this.rate = rate;
      this.returnPeriod = returnPeriod;
      return this;
    }

    Builder probabilityModel(
        ExceedanceModel probabilityModel,
        double truncation) {

      this.probabilityModel = probabilityModel;
      this.truncation = truncation;
      return this;
    }

    Builder contributorLimit(double contributorLimit) {
      this.contributorLimit = contributorLimit;
      return this;
    }

    DeaggConfig build() {
      return new DeaggConfig(
          bins,
          contributorLimit,
          imt,
          model,
          iml,
          rate,
          returnPeriod,
          probabilityModel,
          truncation);
    }
  }

}
