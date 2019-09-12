'use strict';

import Constraints from './lib/Constraints.js';
import D3GeoDeagg from './lib/D3GeoDeagg.js';
import Footer from './lib/Footer.js';
import Header from './lib/Header.js';
import LeafletTestSitePicker from './lib/LeafletTestSitePicker.js';
import NshmpError from './error/NshmpError.js';
import Spinner from './lib/Spinner.js';
import Tools from './lib/Tools.js';

/**
* @class GeoDeagg
*
* @fileoverview Class for the geographic deaggregation webpage, geo-deagg.html.
* The class first calls out to the deagg webservice, nshmp-haz-ws/deagg, to 
*     get the usage and builds the following menus:
*         - Edition
*         - Region
*         - IMT
*         - Vs30
* The IMT and Vs30 menus are created by the common supported values in 
*     the edition and region. 
* Bootstrap tooltips are created and updated for the latitude, longitude,
*     and return period inputs. 
* The inputs, latitude, longitude, and return period, are monitored. If
*     a bad or out of range value, given the coorresponding min/max values in
*     the usage, is entered the update button will remain disabled. Once
*     all inputs are correctly entered the update button or enter can be 
*     pressed to render the results.
* Once the update button or enter is pressed the results are rendered 
*     using the D3GeoDeagg class.
* For the geographic deaggregation, only the single sources for the total
*     component are graphed.
*
* @author bclayton@usgs.gov (Brandon Clayton)          
*/
export default class GeoDeagg {

  /**
  * @param {!Config} config -  The config file. 
  */
  constructor(config) {
    /** @type {Config} */
    this.config = config;
    /** @type {FooterOptions} */
    this.footerOptions = {
      rawBtnDisable: true,
      updateBtnDisable: true,
    };
    /** @type {Footer} */
    this.footer = new Footer();
    this.footer.setOptions(this.footerOptions);
    /** @type {Header} */
    this.header = new Header().setTitle('Geographic Deaggregation');
    /** @type {Spinner} */
    this.spinner = new Spinner();
   
    /**
    * @typedef {Object} GeoDeaggOptions - Geographic deagg options
    * @property {String} defaultEdition - Default selected edition.
    *     Values: 'E2014' || 'E2008' || 'E2007'
    *     Default: 'E2014'
    * @property {Number} defaultReturnPeriod - Default selected return 
    *     return period. 
    *     Values: 475 || 975 || 2475
    *     Default: 2475
    */ 
    this.options = {
      defaultEdition: 'E2014',
      defaultReturnPeriod: 2475,
    };

    /** @type {String} */
    this.webServiceUrl = this.config.server.dynamic + '/nshmp-haz-ws/deagg';
    
    /** @type {HTMLElement} */
    this.contentEl = document.querySelector('#content');
    /** @type {HTMLElement} */
    this.controlPanelEl = document.querySelector('#control');
    /** @type {HTMLElement} */
    this.editionEl = document.querySelector('#edition');
    /** @type {HTMLElement} */
    this.imtEl = document.querySelector('#imt');
    /** @type {HTMLElement} */
    this.inputsEl = document.querySelector('#inputs');
    /** @type {HTMLElement} */
    this.latEl = document.querySelector('#lat');
    /** @type {HTMLElement} */
    this.lonEl = document.querySelector('#lon');
    /** @type {HTMLElement} */
    this.regionEl = document.querySelector('#region');
    /** @type {HTMLElement} */
    this.returnPeriodEl = document.querySelector('#return-period');
    /** @type {HTMLElement} */
    this.returnPeriodBtnsEl = document.querySelector('#return-period-btns');
    /** @type {HTMLElement} */
    this.testSitePickerBtnEl = document.querySelector('#test-site-picker');
    /** @type {HTMLElement} */
    this.vs30El = document.querySelector('#vs30');

    /** @type {D3GeoDeagg} */
    this.plot = this.plotSetup();

    /** @type {Object} - Deagg usage */
    this.parameters = undefined;
    // Get deagg usage
    this.getUsage();
    
    // Check latitude values on input 
    $(this.latEl).on('input', (event) => {
      this.checkCoordinates(event.target);
    });
    
    // Check longitude values on input 
    $(this.lonEl).on('input', (event) => {
      this.checkCoordinates(event.target);
    });
 
    // Update plot when update button pressed
    $(this.footer.updateBtnEl).click((event) => {
      $(this.footer.rawBtnEl).off();
      this.footerOptions.rawBtnDisable = false;
      this.footer.setOptions(this.footerOptions);
      this.updatePlot();
    });
    
    // Listen for all control panel changes
    this.onInputChange(); 
  
    /* @type {LeafletTestSitePicker} */
    this.testSitePicker = new LeafletTestSitePicker(
        this.latEl,
        this.lonEl,
        this.testSitePickerBtnEl);

    /* Bring Leaflet map up when clicked */                                     
    $(this.testSitePickerBtnEl).on('click', (event) => {                        
      this.testSitePicker.plotMap(this.regionEl.value);
    });
  }
  
  /**
  * @method addInputTooltip
  *
  * Add an input tooltip for latitude, logitude, and return period using
  *     Contraints.addTooltip.
  */
  addInputTooltip() {
    let region = Tools.stringToParameter(
        this.parameters.region, this.regionEl.value);
    
    Constraints.addTooltip(
        this.latEl, region.minlatitude, region.maxlatitude); 
    
    Constraints.addTooltip(
        this.lonEl, region.minlongitude, region.maxlongitude); 
    
    let period = this.parameters.returnPeriod;
    Constraints.addTooltip(
        this.returnPeriodEl, period.values.minimum, period.values.maximum); 
  }
  
  /**
  * @method buildInputs
  *
  * Process usage response and set select menus
  * @param {!Object} usage - JSON usage response from deagg web service.
  */
  buildInputs(usage) {
    this.spinner.off();
    let parameters = usage.parameters;
    this.parameters = parameters;
    
    // Set edition menu
    Tools.setSelectMenu(this.editionEl, parameters.edition.values);
    this.editionEl.value = this.options.defaultEdition; 
    
    // Set menus 
    this.setRegionMenu();
    this.setImtMenu();
    this.setVs30Menu();
    this.setDefaultReturnPeriod();
    this.addInputTooltip();
   
    $(this.controlPanelEl).removeClass('hidden');

    // Update return period on change
    this.onReturnPeriodChange();
    // Update menus when edition is changed
    this.onEditionChange();
    // Update menus when region is changed
    this.onRegionChange();  
    // Check URL for parameters
    this.testSitePicker.on('testSiteLoad', (event) => {
      this.checkQuery();
    });
  }

  /**
  * @method checkCoordinates
  *
  * Check the input values of latitude or longitude using Contraints.check
  *     method. If values is out of range or bad, the plot cannot be updated.
  * @param {HTMLElement} el - Latitude or longitude element.
  */
  checkCoordinates(el) {
    let region = Tools.stringToParameter(
        this.parameters.region, this.regionEl.value);
    
    let min = el.id == 'lat' ?  region.minlatitude : 
        region.minlongitude;

    let max = el.id == 'lat' ?  region.maxlatitude : 
        region.maxlongitude;
    
    Constraints.check(el, min, max);
  }

  /**
  * @method checkReturnPeriod
  *
  * Check the return period input value using the Contraints.check method.
  *     If the value is out of range or bad, the plot cannot be updated. 
  */
  checkReturnPeriod() {
    let period = this.parameters.returnPeriod;
    Constraints.check(
        this.returnPeriodEl, period.values.minimum, period.values.maximum); 
  }
 
  /**
  * @method checkQuery
  *
  * Check the hash of the URL string to see if parameters exists. If
  *     there are paramaters present, set the menus to match the values and
  *     plot the results.
  */
  checkQuery() {
    let url = window.location.hash.substring(1);
    let urlObject = Tools.urlQueryStringToObject(url); 
   
    // Make sure all pramameters are present in URL 
    if (!urlObject.hasOwnProperty('edition') || 
          !urlObject.hasOwnProperty('region') ||
          !urlObject.hasOwnProperty('latitude') ||
          !urlObject.hasOwnProperty('longitude') ||
          !urlObject.hasOwnProperty('imt') ||
          !urlObject.hasOwnProperty('vs30') ||
          !urlObject.hasOwnProperty('returnperiod')) return;
    
    // Set edition value and trigger a change to update the other menus 
    let edition = urlObject.edition;
    this.editionEl.value = edition;
    $(this.editionEl).trigger('change');
    delete urlObject.edition;
     
    // Set regions value and trigger a change to update the other menus
    let region = urlObject.region;
    this.regionEl.value = region;
    $(this.regionEl).trigger('change');
    delete urlObject.region;
   
    // Update all other values 
    for (let key in urlObject) {
      $('[name="' + key + '"]').val(urlObject[key]);
    }
    
    this.checkCoordinates(this.latEl);  
    this.checkCoordinates(this.lonEl);  

    // Trigger input event on return period
    $(this.returnPeriodEl).trigger('input');

    // Trigger an enter key
    $(this.inputsEl).trigger('change');
    let keypress = jQuery.Event('keypress');
    keypress.which = 13;
    keypress.keyCode = 13;
    $(document).trigger(keypress);
  }
  
  /**
   * Get current chosen parameters.
   * @return Map<String, Array<String>> The metadata Map
   */
  getMetadata() {
    let metadata = new Map();
    metadata.set('Edition:', [$(this.editionEl).find(':selected').text()]);
    metadata.set('Region:', [$(this.regionEl).find(':selected').text()]);
    metadata.set('Latitude (°):', [this.latEl.value]);
    metadata.set('Longitude (°):', [this.lonEl.value]);
    metadata.set('Intensity Measure Type:', [$(this.imtEl).find(':selected').text()]);
    metadata.set('V<sub>s</sub>30:', [$(this.vs30El).find(':selected').text()]);
    metadata.set('Return Period (years):', [this.returnPeriodEl.value + ' years']);
    
    return metadata;
  }

  /**
  * @method getUsage
  *
  * Call deagg web service and get the JSON usage. Once usage is received,
  *     build the inputs.
  */
  getUsage() {
    let jsonCall = Tools.getJSON(this.webServiceUrl);
    this.spinner.on(jsonCall.reject);

    jsonCall.promise.then((usage) => {
      NshmpError.checkResponse(usage);
      this.buildInputs(usage);
    }).catch((errorMessage) => {
      this.spinner.off();
      NshmpError.throwError(errorMessage);
    });
  }

  /**
  * @method onEditionChange
  *
  * Listen for the edition menu to change and update region, IMT, 
  *     and Vs30 menus and update input tooltip for new values.
  */
  onEditionChange() {
    $(this.editionEl).on('change', (event) => {
      this.setRegionMenu();
      this.setImtMenu();
      this.setVs30Menu();
      this.latEl.value = null;
      this.lonEl.value = null;
      this.addInputTooltip();
    });
  }

  /**
  * @method onInputChange
  * 
  * Listen for any menu or input to change in the control panel and check
  *   if any input box has bad values. If values are good allow the plot 
  *   to be updated. 
  */
  onInputChange() {
    $(this.inputsEl).on('change input', (event) => {
      let hasError;
      let val; 
      $(this.inputsEl).find('input').each((i, d) => {
        val = parseFloat(d.value);
        hasError = isNaN(val) ? true : $(d.parentNode).hasClass('has-error');
        return !hasError;
      });
      this.footerOptions.updateBtnDisable = hasError;
      this.footer.setOptions(this.footerOptions);
    });
  }
 
  /**
  * @method onRegionChange
  *
  * Listen for the region menu to change and update the IMT and Vs30
  *     menus and update the input tooltips for new values.
  */ 
  onRegionChange() {
    $(this.regionEl).on('change', (event) => {
      this.latEl.value = null;
      this.lonEl.value = null;
      this.addInputTooltip();
      this.setImtMenu();
      this.setVs30Menu();
    });
  }
  /**
  * @method onReturnPeriodChange
  *
  * Listen for a return period button to be clicked or the return period
  *     input to be changed. Update the value of the input with the new button
  *     value. If a value is input, check to see if the value matches 
  *     any of the button values and make that button active.
  */
  onReturnPeriodChange() {
    // Update input with button value
    $(this.returnPeriodBtnsEl).on('click', (event) => {
      let el = $(event.target).closest('.btn').find('input');
      let val = el.val(); 
      this.returnPeriodEl.value = val;
    });
    
    // See if input value matches a button value
    $(this.returnPeriodEl).on('input', (event) => {
      this.checkReturnPeriod();
      
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
    });
  }

  /**
  * @method plotSetup
  *
  * Set specific plot options.
  * @return {D3GeoDeagg} - New instance of the D3GeoDeagg class for plotting
  *     results.
  */
  plotSetup() {
    let viewOptions = {};
    let upperPlotOptions = {
      marginBottom: 0,
      marginLeft: 0,
      marginRight: 0,
      marginTop: 10,
    };
    let lowerPlotOptions = {};
    
    return new D3GeoDeagg(
        this.contentEl,
        viewOptions,
        upperPlotOptions,
        lowerPlotOptions)
        .withPlotHeader()
        .withPlotFooter();
  }
  
  /**
  * @method serializeUrl
  *
  * Get all current values from the control panel and serialize using
  *     the name attribute. Update the hash part of the URL to the 
  *     key value pairs.
  * @return {String} - URL to call webservices. 
  */
  serializeUrl() {
    let inputs = $(this.inputsEl).serialize();
    let url = this.webServiceUrl + '?' + inputs;
    window.location.hash = inputs;

    return url;
  }

  /**
  * @method setDefaultReturnPeriod
  *
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

  /**
  * @method setRegionMenu
  *
  * Set the region select menu given the supported regions from the 
  *     selected edition.
  */
  setRegionMenu() {
    let selectedEdition = Tools.stringToParameter(
        this.parameters.edition, this.editionEl.value);
    
    let supportedRegions = Tools.stringArrayToParameters(
        this.parameters.region, selectedEdition.supports.region);
    
    Tools.setSelectMenu(this.regionEl, supportedRegions); 
  }

  /**
  * @method setImtMenu
  *
  * Set the IMT select menu given the common supported IMT values from the 
  *     selected edition and region.
  */
  setImtMenu() {
    let selectedEdition = Tools.stringToParameter(
        this.parameters.edition, this.editionEl.value);
    
    let selectedRegion = Tools.stringToParameter(
        this.parameters.region, this.regionEl.value);
    
    let supports = [];
    supports.push(selectedEdition.supports.imt)
    supports.push(selectedRegion.supports.imt);

    let supportedImts = Tools.supportedParameters(
        this.parameters.imt, supports);
    
    Tools.setSelectMenu(this.imtEl, supportedImts); 
  }

  /**
  * @method setVs30
  *
  * Set the VS30 select menu given the common supported Vs30 values from the 
  *     selected edition and region.
  */
  setVs30Menu() {
    let selectedEdition = Tools.stringToParameter(
        this.parameters.edition, this.editionEl.value);
    
    let selectedRegion = Tools.stringToParameter(
        this.parameters.region, this.regionEl.value);
    
    let supports = [];
    supports.push(selectedEdition.supports.vs30)
    supports.push(selectedRegion.supports.vs30);

    let supportedVs30 = Tools.supportedParameters(
        this.parameters.vs30, supports);
    
    Tools.setSelectMenu(this.vs30El, supportedVs30); 
  }

  /**
  * @method updatePlot
  *
  * Call the deagg web service and plot the single source total component
  *     values using the D3GeoDeagg plotting class. 
  */
  updatePlot() {
    let url = this.serializeUrl();
    let metadata = this.getMetadata();

    let jsonCall = Tools.getJSON(url);
    this.spinner.on(jsonCall.reject, 'Calculating');

    jsonCall.promise.then((response) => {
      this.spinner.off();
      NshmpError.checkResponse(response, this.plot); 

      this.footer.setMetadata(response.server);
      
      // Find total data component 
      let totalData = response.response[0].data.find((d, i) => {
        return d.component == 'Total';
      });
      
      // Find the single sources
      let singleSources = totalData.sources.filter((d, i) => {
        return d.type == 'SINGLE';
      });
      
      let seriesData = [];
      let seriesLabels = [];
      let seriesIds = [];
      
      singleSources.forEach((d, i) => {
        seriesData.push([[d.longitude, d.latitude, d.contribution]]);
        seriesLabels.push(d.name);
        seriesIds.push(d.name.replace(/ /g, '_'));
      });
     
      metadata.set('url', [window.location.href]);
      metadata.set('date', [response.date]);
      
      let lat = response.response[0].metadata.latitude;
      let lon = response.response[0].metadata.longitude;
      // Where the map should rotate to
      let rotate = [-lon, -lat, 0];
      
      let siteTitle = this.testSitePicker
          .getTestSiteTitle(this.regionEl.value);
      let vs30 = $(':selected', this.vs30El).text();
      let imt = $(':selected', this.imtEl).text();
      let edition = $(':selected', this.editionEl).text();

      let title = edition + ', ' + siteTitle + ', ' + imt + ', ' + vs30;

      this.plot.setPlotTitle(title)
          .setSiteLocation({latitude: lat, longitude: lon})
          .setMetadata(metadata)
          .setUpperData(seriesData)
          .setUpperPlotFilename('geoDeagg')
          .setUpperDataTableTitle('Deaggregation Contribution')
          .setUpperPlotIds(seriesIds)
          .setUpperPlotLabels(seriesLabels)
          .plotData(this.plot.upperPanel, rotate);

      $(this.footer.rawBtnEl).off();
      $(this.footer.rawBtnEl).on('click', (event) => {
        window.open(url);
      });
  
    }).catch((errorMessage) => {
      this.spinner.off();
      NshmpError.throwError(errorMessage);
    });
  }

}
