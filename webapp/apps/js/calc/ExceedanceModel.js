
import { D3XYPair } from '../d3/data/D3XYPair.js';
import { Maths } from './Maths.js';
import { Preconditions } from '../error/Preconditions.js';
import { UncertaintyModel } from './UncertaintyModel.js';

export class ExceedanceModel {

  /**
   * No truncation; model ignores truncation level n.
   * 
   * Compute the probability of exceeding a value.
   * @param {UncertaintyModel} model to compute exceedance
   * @param {number} value for which to compute the exceedance probability
   */
  static truncationOff(model, value) {
    Preconditions.checkArgumentInstanceOf(model, UncertaintyModel);
    Preconditions.checkArgumentNumber(value);

    return this._boundedCcdFn(model, value, 0.0, 1.0);
  }

  /**
   * No truncation; model ignores truncation level n.
   * 
   * Compute the probability of exceeding a value.
   *
   * @param {UncertaintyModel} model to compute exceedance
   * @param {D3XYPair[]} sequence for which to compute
   *    the exceedance probability
   */
  static truncationOffSequence(model, sequence) {
    Preconditions.checkArgumentInstanceOf(model, UncertaintyModel);
    Preconditions.checkArgumentArrayInstanceOf(sequence, D3XYPair);

    for (let xy of sequence) {
      xy.y = this.truncationOff(model, xy.x);
    }

    return sequence;
  }

  /**
   * Upper truncation only at μ + σ * n.
   * 
   * Compute the probability of exceeding a value.
   *
   * @param {UncertaintyModel} model to compute exceedance
   * @param {number} value for which to compute the exceedance probability
   */
  static truncationUpperOnly(model, value) {
    Preconditions.checkArgumentInstanceOf(model, UncertaintyModel);
    Preconditions.checkArgumentNumber(value);

    return this._boundedCcdFn(model, value, this._prob(model), 1.0);
  }

  /**
   * Upper truncation only at μ + σ * n.
   * 
   * Compute the probability of exceeding a value.
   *
   * @param {UncertaintyModel} model to compute exceedance
   * @param {D3XYPair[]} sequence for which to compute the exceedance probability
   */
  static truncationUpperOnlySequence(model, sequence) {
    Preconditions.checkArgumentInstanceOf(model, UncertaintyModel);
    Preconditions.checkArgumentArrayInstanceOf(sequence, D3XYPair);

    for (let xy of sequence) {
      xy.y = this.truncationUpperOnly(model, xy.x); 
    }

    return sequence;
  }

  /**
   * @private
   * 
   * Bounded complementary cumulative distribution. Compute the probability that
   * a value will be exceeded, subject to upper and lower probability limits.
   * 
   * @param {UncertaintyModel} model to compute exceedance
   */
  static _boundedCcdFn(model, value, pHi, pLo) {
    Preconditions.checkArgumentInstanceOf(model, UncertaintyModel);
    Preconditions.checkArgumentNumber(value);
    Preconditions.checkArgumentNumber(pHi);
    Preconditions.checkArgumentNumber(pLo);

    const p = Maths.normalCcdf(model.μ, model.σ, value);
    return this._probBoundsCheck((p - pHi) / (pLo - pHi));
  }

  /**
   * @private
   * 
   * For truncated distributions, p may be out of range. For upper truncations,
   * p may be less than pHi, yielding a negative value in boundedCcdFn(); for
   * lower truncations, p may be greater than pLo, yielding a value > 1.0 in
   * boundedCcdFn().
   */
  static _probBoundsCheck(p) {
    Preconditions.checkArgumentNumber(p);

    return (p < 0.0) ? 0.0 : (p > 1.0) ? 1.0 : p;
  }

  /**
   * @private
   * 
   * Compute ccd value at μ + nσ.
   * 
   * @param {UncertaintyModel} model to compute exceedance
   */
  static _prob(model) {
    Preconditions.checkArgumentInstanceOf(model, UncertaintyModel);

    return Maths.normalCcdf(model.μ, model.σ, model.μ + model.n * model.σ);
  }

}
