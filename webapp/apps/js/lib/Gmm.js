'use strict';

import Constraints from './Constraints.js';
import Footer from './Footer.js';
import Header from './Header.js';
import Settings from './Settings.js';
import Spinner from './Spinner.js';
import Tools from './Tools.js';
import NshmpError from '../error/NshmpError.js';

/** 
* @fileoverview Parent class for ground motion model based web apps including:
*     - HwFw
*     - GmmDistance
*     - Spectra
* This class contains some common HTML elements, including:
*     - #gmms
*     - .gmm-alpha
*     - .gmm-group
*     - #gmm-sorter
*     - #inputs
*     - #Mw
*     - #vs30
*     - #z1p0
*     - #z2p5
* This class creates the web pages header, footer, spinner, and settings
*     elements.
* Once getUsage() is called, the class will call out to the respective
*     web service, gmm/distance, gmm/hw-fw, or gmm/spectra, to get the 
*     usage and build the control panel with values.
* NOTE: If any other classes extend this class the WebApps enum must be 
*     updated accordingly.
*
* @class Gmm
* @author bclayton@usgs.gov (Brandon Clayton)
*/
export class Gmm {

  /**
  * @param {!String} webApp Identifier of the application being used.
  *     Possible values: GmmDistance, HwFW, or Spectra
  * @param {!String} wsUrl URL to corresponding web service.
  *     Possible values: /nshmp-haz-ws/gmm/distance, 
  *         /nshmp-haz-ws/gmm/hw-fw, /nshmp-haz-ws/gmm/spectra
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
    /** @type {Settings} */
    //this.settings = new Settings(this.footer.settingsBtnEl);
    /** @type {String} */
    this.currentWebApp = webApp;
    /** @type {String} */
    this.webServiceUrl = webServiceUrl;
   
    /** @type {HTMLElement} */
    this.controlPanelEl = document.querySelector('#control');
    /** @type {HTMLElement} */
    this.gmmsEl = document.querySelector('#gmms');
    /** @type {HTMLElement} */
    this.gmmAlphaEl = document.querySelector('.gmm-alpha');
    /** @type {HTMLElement} */
    this.gmmGroupEl = document.querySelector('.gmm-group');
    /** @type {HTMLElement} */
    this.gmmSorterEl = document.querySelector('#gmm-sorter');
    /** @type {HTMLElement} */
    this.inputsEl = document.querySelector('#inputs');
    /** @type {HTMLElement} */
    this.MwEl = document.querySelector('#Mw');
    /** @type {HTMLElement} */ 
    this.vs30El = document.querySelector('#vs30');
    /** @type {HTMLElement} */
    this.z1p0El = document.querySelector('#z1p0');
    /** @type {HTMLElement} */
    this.z2p5El = document.querySelector('#z2p5');
    
    /** @type {Object} */
    this.config = config;
    
    /** 
    * Web applications extending the Gmm class
    * @enum {String} 
    */
    this.WebApps = {
      GMM_DISTANCE: 'GmmDistance',
      HW_FW: 'HwFw',
      SPECTRA: 'Spectra',
    };

    $(this.footer.updateBtnEl).click((event) => {
      $(this.footer.rawBtnEl).off();
      this.footerOptions.rawBtnDisable = false; 
      this.footer.setOptions(this.footerOptions);
      this.updatePlot();
    });
    
    // On any input
    $(this.inputsEl).on('input', (event) => { this.inputsOnInput(event); });
    
    // Setup jQuery tooltip   
    $('[data-toggle="tooltip"]').tooltip(); 
  
  }
  
  /**
  * @method addToggle
  *
  * Add toggle behavier to all button children of id. 
  * @param {!String} id ID of the button element
  * @param {!Function} callback 
  */
  addToggle(id, callback) {
    $('#' + id + ' button').click((event) => {
      if ($(event.target).hasClass('active')) return;
      $(event.target).siblings().removeClass('active');
      $(event.target).addClass('active');
      this.callback_ = callback;
      this.callback_(event.target.id);
    });
  }
  
  /**
  * @method buildInputs
  * 
  * Process usage response and build form inputs
  * @param {!Object} usage JSON response from web service call 
  */
  buildInputs(usage) {
    this.spinner.off();
    let params = usage.parameters;

    // Alphabetical GMMs. 
    let gmmAlphaOptions = $();
    params.gmm.values.forEach((gmm) => {
      gmmAlphaOptions = gmmAlphaOptions.add($('<option>')
        .attr('value', gmm.id)
        .attr('id', gmm.id)
        .text(gmm.label));
    });

    // Grouped GMMs. 
    let gmmGroupOptions = $();
    params.group.values.forEach((group) => {
      let members = group.data;
      let optGroup = $('<optgroup>')
          .attr('label', group.label)
          .attr('id', group.id);
      gmmGroupOptions = gmmGroupOptions.add(optGroup);
      optGroup.append(gmmAlphaOptions
        .filter((index, gmmOption) => {
          return members.includes(gmmOption.getAttribute('value')); 
        })
        .clone());
    });

    // Bind option views to sort buttons 
    $(this.gmmSorterEl).find('input').change((event) => {
      let options = event.target.value === 'alpha' ? 
          gmmAlphaOptions : gmmGroupOptions;
      $(this.gmmsEl).empty().append(options);
      $(this.gmmsEl).scrollTop(0);
    });

    // Set initial view to groups 
    $(this.gmmsEl).empty().append(gmmGroupOptions);

    // Populate fields with defaults. 
    Object.keys(params)
      .filter((key) => {
        if (key === 'gmm') return false;
        if (key === 'group') return false;
        return true; 
      })
      .forEach((key, index) => {
        let input = $('input[name="' + key + '"]');
        let inputEl = input[0];
        input.val(params[key].value); 
        if (inputEl != undefined && inputEl.type != 'radio'){
          Constraints.addTooltip(inputEl, params[key].min, params[key].max);
        }
      });

    if (this.currentWebApp != this.WebApps.SPECTRA){
      let imtOptions = $();
      imtOptions = imtOptions.add($('<option>')
          .attr('value', 'default')
          .text('Select a GMM')
      );
      $(this.imtEl).append(imtOptions);
      
      $(this.gmmsEl).change((event) => {
        this.setImts();
      });
    }
   
    $(this.controlPanelEl).removeClass('hidden');

    this.checkQuery(gmmAlphaOptions);
  }
  
  /**
  * @method calcDistances
  *
  * Calculate rX, rJB, and rRup
  * @return {Array<number, number, number>} [rX, rRup, rX] 
  */  
  calcDistances() {
    let rX = this.rX_val();
    let zTop = this.zTop_val();
    let footwall = $(this.hwFwFwEl).hasClass('active');
    let rRup = Math.hypot(rX, zTop);

    if (footwall) {
      return [rX, rRup, rX];
    }

    let δ = this.dip_val();
    let W = this.width_val();
    let sinδ = Math.sin(δ);
    let cosδ = Math.cos(δ);
    let Wx = W * cosδ;
    let Wz = W * sinδ;
    let rJB = Math.max(0.0, rX - Wx);
    let h1 = zTop / cosδ;
    let rCut1 = h1 * sinδ;

    if (rX < rCut1) {
      return [rJB, rRup, rX];
    }

    let zBot = zTop + Wz;
    let h2 = zBot / cosδ;
    let rCut2 = Wx + h2 * sinδ;

    if (rX >= rCut2) {
      rRup = Math.hypot(zBot, rJB);
      return [rJB, rRup, rX];
    }

    /*  
     * Linear scaling of distance normal
     * to top and bottom of fault.
     */
    rRup = h1 + (h2 - h1) * ((rX - rCut1) / (rCut2 - rCut1));
    return [rJB, rRup, rX];
  }
 
  /**
  * @method checkRakeRange
  *
  * Check if rake is normal, reverse, or strike-slip
  * @return {number} Rake value
  */ 
  checkRakeRange(mech, value) {
    console.log(mech);
    let isNormal = value < -45.0 && value > -135.0;
    let isReverse = value > 45.0 && value < 135.0;
    let isStrike = !isReverse && !isNormal;
    if (mech == 'fault-style-reverse') return isReverse ? value : 90.0;
    if (mech == 'fault-style-normal') return isNormal ? value : -90.0;
    return isStrike ? value : 0.0;
  }
 
  /**
  * @method checkQuery
  *
  * Check URL to see if parameters exist on hash part of URL and plot 
  *     parameters if they are present.
  * @param {Array<HTMLElements>} Array of GMM options for GMM select menu
  */
  checkQuery(gmmOptions){
    let url = window.location.hash.substring(1);
    if (!url) return;
    
    $(this.gmmGroupEl).removeClass('active');
    $(this.gmmAlphaEl).addClass('active');
    $(this.gmmAlphaEl).find('input').prop('checked', true);
    $(this.gmmsEl).empty().append(gmmOptions);
    
    if (this.currentWebApp == this.WebApps.SPECTRA){
      $('input[type*="checkbox"]').prop('checked', false);
      $(this.zHypEl).prop('readOnly', false);
      $(this.rRupEl).prop('readOnly', false);
      $(this.rJBEl).prop('readOnly', false);
      $(this.hwFwHwEl).prop('disabled', true);
      $(this.hwFwFwEl).prop('disabled', true);
    }
    
    let pars = url.split('&');
    pars.forEach((par, i) => {
      let key = par.split('=')[0]; 
      let value  = par.split('=')[1]; 
      if (key == 'gmm'){
        $(this.gmmsEl).find('option[value="' + value + '"]')
            .prop('selected', true);
      } else if (key == 'rMin') {
        this.rMin = parseFloat(value);
      } else if (key == 'rMax') {
        this.rMax = parseFloat(value);
      } else {
        $('input[name="' + key + '"]').val(value);
      }
    });

    if (this.currentWebApp == this.WebApps.SPECTRA){
       this.updateFocalMech();
    } else {
      this.setImts();
    }

    let gmm = document.querySelector('#' + this.gmmsEl.value);
    gmm.scrollIntoView();
    
    this.footerOptions.rawBtnDisable = false;
    this.footerOptions.updateBtnDisable = false;
    this.footer.setOptions(this.footerOptions);
    this.updatePlot();
  }

  /**
  * @method dip_val
  *
  * Return the dip in radians as a float
  * @return {number} Dip in radians
  */ 
  dip_val() {
    return parseFloat(this.dipEl.value) * Math.PI / 180.0;
  }
 
  /**
  * @method getCurrentGmms
  *
  * Get a list of all selected ground motion models
  * @return {Array<String>} - Array of string values corresponding
  *     to ground motion model.
  */
  getCurrentGmms() {
    let gmmVals = $(this.gmmsEl).val();
    let gmms = [];
    gmmVals.forEach((val) => {
      gmms.push(d3.select('#' + val).text());
    });

    return gmms;
  };

  /**
  * @method getUsage
  * 
  * Call web service and get usage information to build control panel
  * @param {Function=} callback - Callback function
  */
  getUsage(callback = () => {}) {
    this.callback = callback;
    let url = this.webServiceUrl;
    let jsonCall = Tools.getJSON(url);
    this.spinner.on(jsonCall.reject);

    jsonCall.promise.then((usage) => {
      NshmpError.checkResponse(usage);
      this.parameters = usage.parameters;
      this.buildInputs(usage);
      this.callback();
    }).catch((errorMessage) => {
      this.spinner.off();
      NshmpError.throwError(errorMessage);
    });
  }
  
  /**
  * @method imtOnChange
  *
  * Update plot on any IMT change
  * @param {Event=} event - The event that triggered the change
  */
  imtOnChange(event) {
    this.updatePlot();
  } 
  
  /**
  * @method inputsOnInput
  *
  * Check to see if any input field has class "has-error" and
  *     disable the update button if true. 
  * @param {Event=} event - The event that triggered it
  */
  inputsOnInput(event = null) {
    if (event != null) { 
      let el = event.target;
      let id = el.id;
      if (el.type == 'text' || el.type == 'number'){
        let minVal = this.parameters[id].min;
        let maxVal = this.parameters[id].max;
        if (id == 'z1p0' || id == 'z2p5'){
          Constraints.check(el, minVal, maxVal, true /* can have NaN */);
        } else {
          Constraints.check(el, minVal, maxVal);
        }
      }
    }
    
    let hasError = $('*').hasClass('has-error');
    let hasNoGmm = $(':selected', this.gmmsEl).length == 0;
    if ( !hasNoGmm) {
      this.footerOptions.updateBtnDisable = hasError;
      this.footer.setOptions(this.footerOptions);
    }
  }

  /**
  * @method rake_val
  *
  * Return the rake value as a float
  * @return {number} Rake 
  */
  rake_val() {
    return parseFloat(this.rakeEl.value);
  }

  /**
  * @method rX_val
  * 
  * Return rX as a float
  * @return {number} rX 
  */
  rX_val() {
    return parseFloat(this.rXEl.value);
  }

  /**
  * @method setImts
  *  
  * Set the intensity mesure type select menu
  */
  setImts(){
    let selectedGmms = $(this.gmmsEl).val();
    let supportedImts = [];
    
    selectedGmms.forEach((selectedGmm) => {
      let gmm = this.parameters.gmm.values.find((gmm) => {
          return gmm.id == selectedGmm;
      });
      supportedImts.push(gmm.supportedImts);
    });
    
    let commonImts = this.supportedValues(supportedImts, this.parameters.imt);
    
    let imtOptions = $();
    commonImts.forEach((imt) => {
      imtOptions = imtOptions.add($('<option>')
          .attr('value', imt.value)
          .text(imt.display)
      );
    });
    $(this.imtEl).empty().append(imtOptions);
  }

  /**
  * @method serializeGmmUrl
  *
  * Serialize all forms for ground motion web service and 
  *     set the hash of the window location to reflect the form values
  * @return {String} URL to call the web service
  */
  serializeGmmUrl() {
    let inputs = $(this.inputsEl).serialize();
    let url = this.webServiceUrl + '?' + inputs; 
    window.location.hash = inputs;
    
    return url;
  }
  
  /**
  * @method supportedValues
  *
  * Find common supported values of a parameters
  * @param {Array<String>} values - Array of all supported values
  * @param {Object} params
  * @return {Object} 
  */
  supportedValues(values, params){
    let allValues = values.toString().split(",");
    let uniqueValues = [];
    allValues.forEach((val) => {
      if ($.inArray(val, uniqueValues) == -1) uniqueValues.push(val);
    });
    
    let commonValues = uniqueValues.filter((val, jv) => {
      return values.every((d, i) => {
        return d.includes(val);
      });
    });
    
    let supportedParams = params.values.filter((par) => {
      return commonValues.find((val) => {
        return val == par.value;
      })
    });
    
    return supportedParams;
  }
  
  /**
  * @method updateDistance
  * 
  * Update rJB, rRup, and rX calculations and input fields
  */
  updateDistance() {
    if (!$(this.rCheckEl).prop('checked')) return;
    let r = this.calcDistances();
    $(this.rJBEl).val(r[0].toFixed(2));
    $(this.rRupEl).val(r[1].toFixed(2));
  }

  /**
  * @method updateFocalMech
  * 
  * Update focal mech selection based on rake. 
  */
  updateFocalMech() {
    let rake = this.rake_val();
    if (rake > 45.0 && rake < 135.0 
          && !$(this.faultStyleReverseEl).hasClass('active')) {
      $(this.faultStyleReverseEl).click();
      return;
    }
    if (rake < -45.0 && rake > -135.0 
          && !$(this.faultStyleNormalEl).hasClass('active')) {
      $(this.faultStyleNormalEl).click();
      return;
    }
    if (!$(this.faultStyleStrikeEl).hasClass('active')) {
      $(this.faultStyleStrikeEl).click();
    }
  }
  
  /**
  * @method updateHypoDepth
  *
  * Update 
  */
  updateHypoDepth() {
    let hypoDepth = this.zTop_val() + 
        Math.sin(this.dip_val()) * this.width_val() / 2.0;
    $(this.zHypEl).val(hypoDepth.toFixed(2));
  }

  /**
  * @method updateRake
  *
  * Update rake if out of focal mech range 
  * @param {String} id - ID of fault mech 
  */
  updateRake(id) {
    $(this.rakeEl)
        .val(this.checkRakeRange(id, this.rake_val()));
  }
  
  /**
  * @method width_val
  *
  * Return the width
  * @return {number} The width
  */
  width_val() {
    return  parseFloat(this.widthEl.value);
  }

  /**
  * @method zTop_val
  *
  * Return zTop
  * @return {number} zTop
  */
  zTop_val() {
    return parseFloat(this.zTopEl.value);
  }

}
