
import { Preconditions } from '../error/Preconditions.js';

export class Maths {

  /**
   * Normal complementary cumulative distribution function.
   * 
   * @param {number} μ mean
   * @param {number} σ standard deviation
   * @param {number} x variate
   */
  static normalCcdf(μ, σ, x) {
    Preconditions.checkArgumentNumber(μ);
    Preconditions.checkArgumentNumber(σ);
    Preconditions.checkArgumentNumber(x);

    return (1.0 + this.erf((μ - x) / (σ * Math.sqrt(2)))) * 0.5;
  }

  /**
   * Error function approximation of Abramowitz and Stegun, formula 7.1.26 in
   * the <em>Handbook of Mathematical Functions with Formulas, Graphs, and
   * Mathematical Tables</em>. Although the approximation is only valid for
   * x ≥ 0, because erf(x) is an odd function,
   * erf(x) = −erf(−x) and negative values are supported.
   */
  static erf(x) {
    Preconditions.checkArgumentNumber(x);

    return x < 0.0 ? -this._erfBase(-x) : this._erfBase(x);
  }

  static round(value, scale) {
    Preconditions.checkArgumentNumber(value);
    Preconditions.checkArgumentInteger(scale);

    let format = d3.format(`.${scale}f`);

    return Number(format(value));
  }

  static _erfBase(x) {
    Preconditions.checkArgumentNumber(x);

    const P = 0.3275911;
    const A1 = 0.254829592;
    const A2 = -0.284496736;
    const A3 = 1.421413741;
    const A4 = -1.453152027;
    const A5 = 1.061405429;

    const t = 1 / (1 + P * x);
    const tsq = t * t;

    return 1 - (A1 * t +
        A2 * tsq +
        A3 * tsq * t +
        A4 * tsq * tsq +
        A5 * tsq * tsq * t) * Math.exp(-x * x);
  }

}
