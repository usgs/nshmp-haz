
import Constraints from './Constraints.js';
import Footer from './Footer.js';
import Header from './Header.js';
import NshmpError from '../error/NshmpError.js';
import Spinner from './Spinner.js';
import Tools from './Tools.js';

/**
 * @fileoverview Parent class for hazard based web apps including:
 *     - DynamicCompare
 *     - ModelCompare
 *     - ModelExplorer
 *
 * This class contains common HTML elements including:
 *     - #control: control panel
 *     - #imt: IMT select menu
 *     - #inputs: input form
 *     - #lat: latitude input
 *     - #lon: longitude input
 *     - #return-period: return period input
 *     - #return-period-btns: return period buttons
 *     - #vs30: vs30 select menu
 *
 * @author Brandon Clayton
 */
export class Hazard {
  
  /**
   * @param {String} webApp - Identifier of the application being used.
   *     Possible values: 'DynamicCompare', 'ModelComparison', 'ModelExplorer'.
   * @param {String} webServiceUrl - URK to corresponding web servce.
   *     Possible values: '/nshmp-haz-ws/hazard', '/nshmp-haz-ws/source/models'
   * @param {Config} config - The config file.
   */
  constructor(webApp, webServiceUrl, config) {
    /** @type {Footer} */
    this.footer = new Footer();
    this.footerOptions = {
        rawBtnDisable: true,
        updateBtnDisable: true,
    };
    this.footer.setOptions(this.footerOptions);
    
    /** @type {Header} */
    this.header = new Header();
    
    /** @type {Spinner} */
    this.spinner = new Spinner();
    
    /** @type {String} */
    this.currentWebApp = webApp;
    
    /** @type {String} */
    this.webServiceUrl = webServiceUrl;
    
    /** @type {Object} */
    this.config = config;

    /** @type {HTMLElement} */
    this.controlPanelEl = document.querySelector("#control");
    
    /** @type {HTMLElement} */
    this.imtEl = document.querySelector("#imt");
    
    /** @type {HTMLElement} */
    this.inputsEl = document.querySelector('#inputs');
    
    /** @type {HTMLElement} */
    this.latEl = document.querySelector("#lat");
    
    /** @type {HTMLElement} */
    this.lonEl = document.querySelector("#lon");
    
    /** @type {HTMLElement} */
    this.returnPeriodEl = document.querySelector('#return-period');
    
    /** @type {HTMLElement} */
    this.returnPeriodBtnsEl = document.querySelector('#return-period-btns');
    
    /** @type {HTMLElement} */
    this.vs30El = document.querySelector("#vs30");

    this.Y_MIN_CUTOFF = 1e-16;
    
    /**
     * Web applications extending the Hazard class
     * @enum {String}
     */
    this.WebApps = {
      MODEL_EXPLORER: 'ModelExplorer',
      MODEL_COMPARISON: 'ModelComparison',
      DYNAMIC_COMPARISON: 'DynamicComparison',
    };
  
    /** @type {String} */
    this.dynamicUrl = this.config.server.dynamic + '/nshmp-haz-ws/haz';

    /** @type {String} */
    this.staticUrl = this.config.server.static + '/hazws/staticcurve/1';
 
    /* Update plot on click */ 
    $(this.footer.updateBtnEl).click((event) => {
      $(this.footer.rawBtnEl).off();
      this.footerOptions.rawBtnDisable = false;
      this.footer.setOptions(this.footerOptions);
      this.updatePlot();
    });

    /* Listen for return period to change */ 
    this.returnPeriodEventListener();

    /* Return period custom event */
    this.returnPeriodEvent = new Event('returnPeriodChange');
  }
  
  /**
   * Given a region in the web service usage parameters, 
   *     check to see the inputted value in either
   *     the latitude or longitude input is correct.
   * 
   * @param {HTMLElement} el - The input element.
   * @param {Object} region - The region to check lat or lon against. 
   */
  checkCoordinates(el, region) {
    let min = el.id == 'lat' ? region.minlatitude :
        region.minlongitude;
    
    let max = el.id == 'lat' ? region.maxlatitude :
        region.maxlongitude;
    
    Constraints.check(el, min, max);
  }
  
  /**
   * Check the return period input value using the Contraints.check method.
   *     If the value is out of range or bad, the plot cannot be updated.
   */
  checkReturnPeriod() {
    let period = this.parameters.returnPeriod;
    
    return Constraints.check(
        this.returnPeriodEl, period.values.minimum, period.values.maximum);
  }

  /**
   * Check to see if the input value matches any of the return period
   *    buttons.
   */
  checkReturnPeriodButtons() {
    d3.select(this.returnPeriodBtnsEl)
        .selectAll('label')
        .classed('active', false)
        .selectAll('input')
        .select((d, i, els) => {
          if (this.returnPeriodEl.value == els[i].value) {
            return els[i].parentNode;
          }
        })
        .classed('active', true);
  }

  /**
   * Get web service usage and set this.parameters to usage.parameters.
   * 
   * @param {Callback=} callback - An optional callback. 
   */
  getUsage(callback = () => {}) {
    this.callback = callback;
    let jsonCall = Tools.getJSON(this.webServiceUrl);
    this.spinner.on(jsonCall.reject);

    jsonCall.promise.then((usage) => {
      NshmpError.checkResponse(usage);
      this.parameters = usage.parameters;
      this.buildInputs();
      this.callback();
    }).catch((errorMessage) => {
      this.spinner.off();
      NshmpError.throwError(errorMessage);
    });
  }

  /**
   * On return period button click event handler. 
   */
  onReturnPeriodClick() {
    let el = $(event.target).closest('.btn').find('input');

    let val = el.val();
    this.returnPeriodEl.value = val;
    
    let returnPeriodInBounds = this.checkReturnPeriod();

    if (returnPeriodInBounds) {
      this.returnPeriodEl.dispatchEvent(this.returnPeriodEvent);
    }
  }

  /**
   * On return period input event handler
   */
  onReturnPeriodInput() {
    let returnPeriodInBounds = this.checkReturnPeriod();
    if (!returnPeriodInBounds) return;
    this.returnPeriodEl.dispatchEvent(this.returnPeriodEvent);
    this.checkReturnPeriodButtons(); 
  }

  /**
   * Add event listener on input for the return period input and 
   *    click for the return period buttons.
   */
  returnPeriodEventListener() {
    this.returnPeriodBtnsEl.addEventListener('click', () => {
      this.onReturnPeriodClick();
    });

    this.returnPeriodEl.addEventListener('input', () => {
      this.onReturnPeriodInput();
    });
  }

  /**
   * Set the default return period value and button.
   */
  setDefaultReturnPeriod() {
    d3.select(this.returnPeriodBtnsEl)
        .selectAll('input')
        .filter('input[value="' + this.options.defaultReturnPeriod + '"]')
        .select((d, i, els) => { return els[0].parentNode })
        .classed('active', true);

    this.returnPeriodEl.value = this.options.defaultReturnPeriod;
  }

}
