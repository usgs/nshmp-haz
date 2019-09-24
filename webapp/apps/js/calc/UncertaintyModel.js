
import { Preconditions } from '../error/Preconditions.js';

/**
 * @fileoverview Container class for mean, standard deviation, and
 *    truncation level.
 * 
 * @class UncertaintyModel
 * @author Brandon Clayton
 */
export class UncertaintyModel {

  /**
   * @param {number} μ mean
   * @param {number} σ standard deviation
   * @param {number} n truncation level in units of σ (truncation = n * σ)
   */
  constructor(μ, σ, n) {
    Preconditions.checkArgumentNumber(μ);
    Preconditions.checkArgumentNumber(σ);
    Preconditions.checkArgumentNumber(n);
  
    /** Mean */
    this.μ = μ;

    /** Standard deviation */
    this.σ = σ;

    /** Truncation level */
    this.n = n;
  }

}