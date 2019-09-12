'use strict';

import Constraints from './Constraints.js';
import ControlPanel from './ControlPanel.js';
import Footer from './Footer.js';
import Header from './Header.js';
import NshmpError from '../error/NshmpError.js';
import Settings from './Settings.js';
import Spinner from './Spinner.js';
import Tools from './Tools.js';

/** 
 * @fileoverview Parent class for ground motion model based web apps including:
 *     - HwFw
 *     - GmmDistance
 *     - Spectra
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
export class GmmBeta {

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
    /** The control panel - @type {ControlPanel} */
    this.controlPanel = new ControlPanel();

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
  }
  
  /**
   * Calculate rX, rJB, and rRup
   * 
   * @return {Array<Number, Number, Number>} [rX, rRup, rX] 
   */  
  calcDistances() {
    let rX = this.rX_val();
    let zTop = this.zTop_val();
    let footwall = this.hwFwFwEl.checked;
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
     * Linear scaling of distance normal to top and bottom of fault.
     */
    rRup = h1 + (h2 - h1) * ((rX - rCut1) / (rCut2 - rCut1));
    return [rJB, rRup, rX];
  }
 
  /**
   * Check URL to see if parameters exist on hash part of URL and plot 
   *     parameters if they are present.
   */
  checkQuery(){
    let url = window.location.hash.substring(1);
    if (!url) return;
    
    $(this.gmmGroupEl).removeClass('active');
    $(this.gmmAlphaEl).addClass('active');
    this.controlPanel.updateSelectOptions(this.gmmsEl, this.gmmAlphaOptions);

    if (this.currentWebApp == this.WebApps.SPECTRA) {
      $('input[type*="checkbox"]').prop('checked', false);
      $(this.zHypEl).prop('readOnly', false);
      $(this.rRupEl).prop('readOnly', false);
      $(this.rJBEl).prop('readOnly', false);
      $(this.hwFwHwEl.parentNode).toggleClass('disabled', true);
      $(this.hwFwFwEl.parentNode).toggleClass('disabled', true);
    }

    let urlObject = Tools.urlQueryStringToObject(url);
    let multiParam = urlObject.multi;

    for (let key in urlObject) {
      let value = urlObject[key];
      
      switch(key) {
        case 'gmm': 
          if (multiParam != 'gmms' || !Array.isArray(value)) value = [value];

          for (let gmm of value) {
            $(this.gmmsEl).find(`option[value='${gmm}']`)
                .prop('selected', true);
          }
          break;
        case 'rMin' || 'rMax':
          this[key] = parseFloat(value);
          break;
        case multiParam:
          if (multiParam == 'gmms') break;  
          this.multiSelectEl.value = multiParam;
          $(this.multiSelectEl).trigger('input');
          let btnGroupEl = d3.select(this.multiSelectEl).data()[0];

          d3.select(btnGroupEl)
              .selectAll('label')
              .classed('active', false)
              .selectAll('input')
              .property('checked', false);

          for (let val of value) {
            $(btnGroupEl).find(`[value='${val}']`).click();
          }
          $(`#${key}`).trigger('change');
          break;
        default: 
          $(`input[name='${key}']`).val(value);
          $(`#${key}`).trigger('input');
      }

    }

    if (this.currentWebApp != this.WebApps.SPECTRA){
      this.setImts();
    }

    let gmm = document.querySelector('#' + this.gmmsEl.value);
    gmm.scrollIntoView();
   
    $(this.controlPanel.inputsEl).trigger('input');
    $(this.footer.updateBtnEl).click();
  }

  /**
   * Return the dip in radians as a float.
   * 
   * @returns {Number} Dip in radians.
   */ 
  dip_val() {
    return parseFloat(this.dipEl.value) * Math.PI / 180.0;
  }
 
  /**
   * Get a list of all selected ground motion models.
   * 
   * @returns {Array<String>}  Array of string values corresponding
   *     to ground motion model.
   */
  getCurrentGmms() {
    let gmmVals = $(this.gmmsEl).val();
    gmmVals = Array.isArray(gmmVals) ? gmmVals : [gmmVals];
    let gmms = [];
    gmmVals.forEach((val) => {
      let text = d3.select(this.gmmsEl)
          .select('#' + val)
          .text();
      gmms.push(text);
    });

    return gmms;
  };

  /**
   * Call web service and get usage information to build control panel.
   * 
   * @param {Function=} callback - Callback function
   */
  getUsage(callback = () => {}) {
    this._callback = callback;
    let url = this.webServiceUrl;
    let jsonCall = Tools.getJSON(url);
    this.spinner.on(jsonCall.reject);

    jsonCall.promise.then((usage) => {
      NshmpError.checkResponse(usage);
      this.parameters = usage.parameters;
      this.createControlPanel(usage);
      this._callback();
    }).catch((errorMessage) => {
      this.spinner.off();
      NshmpError.throwError(errorMessage);
    })
  }
  
  /**
   * Update plot on any IMT change.
   * 
   * @param {Event=} event - The event that triggered the change
   */
  imtOnChange(event) {
    this.updatePlot();
  } 
  
  /**
   * Check to see if any input field has class "has-error" and
   *     disable the update button if true. 
   * 
   * @param {Event=} event - The event that triggered it
   */
  inputsOnInput(event = null) {
    if (event != null) { 
      let el = event.target;
      let id = el.id;
      if (el.type == 'text' || el.type == 'number'){
        let minVal = el.min || this.parameters[id].min;
        let maxVal = el.max || this.parameters[id].max;
        if (id == 'z1p0' || id == 'z2p5'){
          Constraints.check(el, minVal, maxVal, true /* can have NaN */);
        } else {
          Constraints.check(el, minVal, maxVal);
        }
      }
    }

    let hasError = $('*').hasClass('has-error');
    let gmmIsSelected = $(':selected', this.gmmsEl).length > 0;

    let multiSelectParam = this.multiSelectEl.value;
    let multiSelectableBtnGroupEl = d3.select(this.multiSelectEl).data()[0];

    let btnsAreSelected = multiSelectParam == 'gmms' ? gmmIsSelected :
        $(':checked', multiSelectableBtnGroupEl).length > 0;        

    hasError = hasError || !btnsAreSelected || !gmmIsSelected;
    this.footerOptions.updateBtnDisable = hasError;
    this.footer.setOptions(this.footerOptions);
  }

  /**
   * Return the rake value as a float.
   * 
   * @returns {Number} Rake value.
   */
  rake_val() {
    return parseFloat(this.rakeEl.value);
  }

  /**
   * Return rX as a float.
   * 
   * @returns {Number} rX 
   */
  rX_val() {
    return parseFloat(this.rXEl.value);
  }

  /**
   * Set the intensity mesure type select menu.
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
    // let commonImts = Tools
    //     .supportedParameters(this.parameters.imt, supportedImts); 

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
   * Serialize all forms for ground motion web service and 
   *     set the hash of the window location to reflect the form values.
   * 
   * @returns {Array<String>} Array of URLs to call the web service.
   */
  serializeGmmUrl() {
    let inputs = $(this.controlPanel.inputsEl).serialize();

    let selectedParam = this.multiSelectEl.value;

    if (selectedParam == 'gmms') {
      window.location.hash = inputs + '&multi=gmms';
      return [this.webServiceUrl + '?' + inputs];
    }

    let btnGroupEl = d3.select(this.multiSelectEl).data()[0];
    let multiParams = '';
    let urls = [];

    $(':checked', btnGroupEl).each((i, d) => {
      let param = btnGroupEl.getAttribute('name');
      multiParams +=  '&' + param + '=' + d.value;
      urls.push(this.webServiceUrl + '?' + inputs + '&' + 
          param + '=' + d.value);
    });

    window.location.hash = inputs + multiParams + '&multi=' + selectedParam;

    return urls;
  }

  /**
   * Find common supported values of a parameters.
   * 
   * @param {Array<String>} values - Array of all supported values
   * @param {Object} params
   * @returns {Object} The supported parameters. 
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
   * Update rJB, rRup, and rX calculations and input fields.
   */
  updateDistance() {
    if (!this.rCheckEl.checked) return;
    let r = this.calcDistances();
    $(this.rJBEl).val(r[0].toFixed(2));
    $(this.rRupEl).val(r[1].toFixed(2));
  }

  /**
   * Update hypocenter depth.
   */
  updateHypoDepth() {
    if (this.zCheckEl.checked) {
      let hypoDepth = this.zTop_val() + 
          Math.sin(this.dip_val()) * this.width_val() / 2.0;
      
      this.zHypEl.min = this.parameters.zHyp.min; 
      this.zHypEl.max = this.parameters.zHyp.max; 

      $(this.zHypEl).val(hypoDepth.toFixed(2));
      $(this.zHypEl).tooltip('destroy');
      $(this.zHypEl).trigger('change');
    } else {
      let hypoDepthMax = this.zTop_val() + 
          Math.sin(this.dip_val()) * this.width_val();
      
      hypoDepthMax = parseFloat(hypoDepthMax.toFixed(2));
      this.zHypEl.min = this.zTop_val();
      this.zHypEl.max = hypoDepthMax; 
      Constraints.addTooltip(this.zHypEl, this.zTop_val(), hypoDepthMax);
      $(this.zHypEl).trigger('change');
    }
  }

  /**
   * Return the width as a float.
   * 
   * @returns {Number} The width
   */
  width_val() {
    return  parseFloat(this.widthEl.value);
  }

  /**
   * Return zTop as a float.
   * 
   * @returns {Number} zTop
   */
  zTop_val() {
    return parseFloat(this.zTopEl.value);
  }

}
