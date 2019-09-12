
import { D3LineData } from './d3/data/D3LineData.js';
import { D3LineLegendOptions } from './d3/options/D3LineLegendOptions.js';
import { D3LineOptions } from './d3/options/D3LineOptions.js';
import { D3LinePlot } from './d3/D3LinePlot.js';
import { D3LineSeriesData } from './d3/data/D3LineSeriesData.js';
import { D3LineSubView } from './d3/view/D3LineSubView.js';
import { D3LineSubViewOptions } from './d3/options/D3LineSubViewOptions.js';
import { D3LineView } from './d3/view/D3LineView.js';
import { D3LineViewOptions } from './d3/options/D3LineViewOptions.js';
import { D3TextOptions } from './d3/options/D3TextOptions.js';

import { Hazard } from './lib/HazardNew.js';
import { HazardServiceResponse } from './response/HazardServiceResponse.js';

import Constraints from './lib/Constraints.js';
import LeafletTestSitePicker from './lib/LeafletTestSitePicker.js';
import NshmpError from './error/NshmpError.js';
import { Preconditions } from './error/Preconditions.js';
import Tools from './lib/Tools.js';

/**
 * @fileoverview Class for the dynamic compare webpage, dynamic-compare.html.
 *
 * This class contains two plot panels with the following plots:
 *     first panel: 
 *         - Model comparison of ground motion Vs. annual frequency of exceedence 
 *         - Percent difference between the models
 *     second panel: 
 *         - Response spectrum of the models
 *         - Percent difference of the response spectrum
 *
 * The class first class out to the source model webservice, 
 *     nshmp-haz-ws/source/models, to get the usage and build the 
 *     following menus: 
 *         - Model
 *         - Second Model
 *         - IMT
 *         - Vs30
 * 
 * The IMT and Vs30 menus are created by the common supported values
 *     between the two selected models.
 * 
 * Bootstrap tooltips are created and updated for the latitude, longitude,
 *     and return period inputs.
 * 
 * The inputs, latitude, longitude, and return period, are monitored. If 
 *     a bad or out of range value is entered the update button will 
 *     remain disabled. Once all inputs are correctly entered the update
 *     button or enter can be pressed to render the results.
 * 
 * The return period allowable minimum and maximum values are updated
 *     based on the choosen models such that the response spectrum
 *     is defined for the entire bounds for both models. 
 *
 * The results are rendered using the D3 package. 
 *
 * @class DynamicCompare
 * @author Brandon Clayton
 */
export class DynamicCompare extends Hazard {

  /** @param {!Config} config - The config file */
  constructor(config) {
    let webApp = 'DynamicCompare';
    let webServiceUrl = '/nshmp-haz-ws/haz';
    super(webApp, webServiceUrl, config);
    this.header.setTitle('Dynamic Compare');

    this.options = {
      defaultFirstModel: 'WUS_2014',
      defaultSecondModel: 'WUS_2018',
      defaultImt: 'PGA',
      defaultReturnPeriod: 2475,
      defaultVs30: 760,
    };

    /** @type {HTMLElement} */
    this.contentEl = document.querySelector('#content');

    /** @type {HTMLElement} */
    this.firstModelEl = document.querySelector('#first-model');
    
    /** @type {HTMLElement} */
    this.secondModelEl = document.querySelector('#second-model'); 
    
    /** @type {HTMLElement} */
    this.modelsEl = document.querySelector('.model');
    
    /** @type {HTMLElement} */
    this.testSitePickerBtnEl = document.querySelector('#test-site-picker');
    
    /** @type {Object} */
    this.comparableModels = undefined;
   
    /* Get webservice usage */ 
    this.getUsage();

    /** X-axis domain for spectra plots - @type {Array<Number} */
    this.spectraXDomain = [0.001, 10.0];

    /* Default titles */
    this.hazardComponentPlotTitle = 'Hazard Component Curves';
    this.hazardPlotTitle = 'Hazard Curves';
    this.spectraComponentPlotTitle = 'Response Spectrum Component Curves';
    this.spectraPlotTitle = 'Response Spectrum';

    /* Spectra plot setup */
    this.spectraView = this.setupSpectraView();
    this.spectraLinePlot = new D3LinePlot(this.spectraView);
    this.spectraLinePlot.plotZeroRefLine(this.spectraView.lowerSubView);

    /* Hazard curve plot setup */
    this.hazardView = this.setupHazardView();
    this.hazardLinePlot = new D3LinePlot(this.hazardView);
    this.hazardLinePlot.plotZeroRefLine(this.hazardView.lowerSubView);

    /* Spectra component curves setup */
    this.spectraComponentView = this.setupSpectraComponentView();
    this.spectraComponentLinePlot = new D3LinePlot(this.spectraComponentView);

    /* Hazard component curves setup */
    this.hazardComponentView = this.setupHazardComponentView();
    this.hazardComponentLinePlot = new D3LinePlot(this.hazardComponentView);

    /* @type {LeafletTestSitePicker} */
    this.testSitePicker = new LeafletTestSitePicker(
        this.latEl,
        this.lonEl,
        this.testSitePickerBtnEl); 
  
    this.imtHandler = () => {};
    this.returnPeriodEventHandler = () => {};
    this.vs30EventHandler = () => {};

    this.addEventListener();
  }

  /** Add event listeners */
  addEventListener() {
    /* Check latitude values on change */
    $(this.latEl).on('input', (event) => {
      this.onCoordinate(event);
    });
   
    /* Check longitude values on change */ 
    $(this.lonEl).on('input', (event) => {
      this.onCoordinate(event);
    });
 
    /* Listen for input changes */ 
    this.footer.onInput(this.inputsEl, this.footerOptions);

    /* Update menus when first model changes */ 
    this.firstModelEl.addEventListener('change', () => {
      this.onFirstModelChange();
    });

    /** Update menus on second model change */
    this.secondModelEl.addEventListener('change', () => {
      this.onModelChange();
    });

    /** Check query on test site load */
    this.testSitePicker.on('testSiteLoad', (event) => {
      this.checkQuery();
    });

    /* Bring Leaflet map up when clicked */
    $(this.testSitePickerBtnEl).on('click', (event) => {
      let model = Tools.stringToParameter(
          this.parameters.models,
          this.firstModelEl.value);
      
      this.testSitePicker.plotMap(model.region);
    });
  }

  /**
   * Add an input tooltip for latitude, longitude, and return period 
   *     using Constraints.addTooltip.
   */
  addInputTooltip() {
    let model = Tools.stringToParameter(
        this.parameters.models,
        this.firstModelEl.value);

    let region = this.parameters.region.values.find((region) => {
      return region.value == model.region;
    });

    Constraints.addTooltip(
        this.latEl,
        region.minlatitude,
        region.maxlatitude); 
        
    Constraints.addTooltip(
        this.lonEl,
        region.minlongitude,
        region.maxlongitude); 
  
    let periodValues = this.parameters.returnPeriod.values;
    Constraints.addTooltip(
        this.returnPeriodEl,
        periodValues.minimum,
        periodValues.maximum);
  }

  /**
   * Add text with the ground motion difference.
   *  
   * @param {Array<HazardServiceResponse>} hazardResponses The hazard responses
   * @param {D3LineSubView} subView The sub view
   * @returns {SVGElement} The text element
   */
  addGroundMotionDifferenceText(hazardResponses, subView) {
    Preconditions.checkArgumentArrayInstanceOf(
        hazardResponses,
        HazardServiceResponse);

    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);

    let timeHorizon = this.returnPeriodEl.value;
    let returnPeriod = 1 / timeHorizon;
    let percentDifference = this.getGroundMotionDifference(hazardResponses);

    let xMax = this.hazardLinePlot.getXDomain(subView)[1];
    let text = `${timeHorizon} years, % diff = ${percentDifference}`;
    
    let textOptions = D3TextOptions.builder()
        .dy(10)
        .fontSize(18)
        .textAnchor('end')
        .build();

    let textEl = this.hazardLinePlot.addText(
        subView,
        xMax,
        returnPeriod,
        text,
        textOptions);

    return textEl;
  }

  /**
   * Process usage response from nshmp-haz-ws/source/models and set menus.
   */
  buildInputs() {
    this.spinner.off();
    this.setComparableModels();
    
    this.setFirstModelMenu();
    this.setSecondModelMenu();
    this.secondModelEl.value = this.options.defaultSecondModel;
    this.setParameterMenu(this.imtEl, this.options.defaultImt);
    this.setParameterMenu(this.vs30El, this.options.defaultVs30);
    this.setDefaultReturnPeriod();
    this.addInputTooltip();

    $(this.controlPanelEl).removeClass('hidden');

  }
  
  /**
   * Check the current hash part of the URL for parameters, if they
   *     exist plot the results. 
   */
  checkQuery() {
    let url = window.location.hash.substring(1);
    let urlObject = Tools.urlQueryStringToObject(url);
    
    /* Make sure all pramameters are present in URL */
    if (!urlObject.hasOwnProperty('model') ||
        !urlObject.hasOwnProperty('latitude') ||
        !urlObject.hasOwnProperty('longitude') ||
        !urlObject.hasOwnProperty('imt') ||
        !urlObject.hasOwnProperty('returnperiod') ||
        !urlObject.hasOwnProperty('vs30')) return false;
  
    /* Update values for the menus */ 
    this.firstModelEl.value = urlObject.model[0];
    $(this.firstModelEl).trigger('change');
    this.secondModelEl.value = urlObject.model[1];
    this.latEl.value = urlObject.latitude;
    this.lonEl.value = urlObject.longitude;
    this.imtEl.value = urlObject.imt;
    this.vs30El.value = urlObject.vs30;
    this.returnPeriodEl.value = urlObject.returnperiod;
  
    /* Trigger events to update tooltips */
    $(this.latEl).trigger('input');
    $(this.lonEl).trigger('input'); 
    this.onReturnPeriodInput();
    this.addInputTooltip();

    /* Get and plot results */
    $(this.inputsEl).trigger('change');
    let keypress = jQuery.Event('keypress');
    keypress.which = 13;
    keypress.keyCode = 13;
    $(document).trigger(keypress);
  }

  /**
   * Clear all plots
   */
  clearPlots() {
    this.hazardComponentLinePlot.clearAll();
    this.hazardComponentView.setTitle(this.hazardComponentPlotTitle);

    this.hazardLinePlot.clearAll();
    this.hazardView.setTitle(this.hazardPlotTitle);
    this.hazardLinePlot.plotZeroRefLine(this.hazardView.lowerSubView);

    this.spectraComponentLinePlot.clearAll();
    this.spectraComponentView.setTitle(this.spectraComponentPlotTitle);
    
    this.spectraLinePlot.clearAll();
    this.spectraView.setTitle(this.spectraPlotTitle);
    this.spectraLinePlot.plotZeroRefLine(this.spectraView.lowerSubView);
  }

  /**
   * Calculate the ground motion difference. 
   * 
   * @param {Array<HazardServiceResponse>} hazardResponses The responses
   */
  getGroundMotionDifference(hazardResponses) {
    Preconditions.checkArgumentArrayInstanceOf(
        hazardResponses,
        HazardServiceResponse);

    let spectrum = [];
    let imt = this.imtEl.value;

    let timeHorizon = this.returnPeriodEl.value;
    let returnPeriod = 1 / timeHorizon;

    for (let hazardResponse of hazardResponses) {
      let response = hazardResponse.getResponse(imt);
      spectrum.push(response.calculateResponseSpectrum('Total', returnPeriod));
    }

    return Tools.percentDifference(spectrum[0], spectrum[1]);
  }

  /**
   * Return the Y limit for the hazard curves.
   *  
   * @param {Array<HazardServiceResponse>} hazardResponses The hazard responses
   * @param {String} imt The IMT 
   * @returns {Array<Number>} The Y limit
   */
  getHazardCurvesYLimit(hazardResponses, imt) {
    Preconditions.checkArgumentArrayInstanceOf(
        hazardResponses, 
        HazardServiceResponse);

    Preconditions.checkArgumentString(imt);

    let yValues = [];

    for (let hazardResponse of hazardResponses) {
      let response = hazardResponse.getResponse(imt);
  
      for (let data of response.data) {
        yValues.push(data.yValues);
      }
    }

    yValues = d3.merge(yValues).filter((y) => { return y > this.Y_MIN_CUTOFF; });

    let min = d3.min(yValues);
    let max = d3.max(yValues);

    return [ min, max ];
  }

  /**
   * Get the metadata, associated with the hazard plots,
   *     about the selected parameters in the control panel.
   * 
   * @return {Map<String, Array<String>>} The metadata Map
   */
  getMetadataHazard() {
    let models = [
      $(':selected', this.firstModelEl).text(),
      $(':selected', this.secondModelEl).text(),
    ];
   
    let metadata = new Map();
    metadata.set('Model:', models)  
    metadata.set('Latitude:', [this.latEl.value]);
    metadata.set('Longitude:', [this.lonEl.value]);
    metadata.set('Intensity Measure Type:', [$(':selected', this.imtEl).text()]);
    metadata.set('V<sub>s</sub>30:', [$(':selected', this.vs30El).text()]);
    
    return metadata;
  }
  
  /**
   * Get the metadata, associated with the response spectra plots,
   *     about the selected parameters in the control panel.
   * 
   * @return {Map<String, Array<String>>} The metadata Map
   */
  getMetadataSpectra() {
    let models = [
      $(':selected', this.firstModelEl).text(),
      $(':selected', this.secondModelEl).text(),
    ];
    
    let metadata = new Map();
    metadata.set('Model:', models)  
    metadata.set('Latitude:', [this.latEl.value]);
    metadata.set('Longitude:', [this.lonEl.value]);
    metadata.set('V<sub>s</sub>30:', [$(':selected', this.vs30El).text()]);
    metadata.set('Return Period (years):', [this.returnPeriodEl.value]);

    return metadata;
  }

  /**
   * Calculate the percent difference of the models.
   * 
   * @param {D3LineSubView} subView The sub view for the line data
   * @param {D3LineData} lineData The hazard line data 
   * @param {Array<Number>} xLimit The X limit for the data
   * @returns {D3LineData} The percent difference line data
   */
  getModelDifferenceData(subView, lineData, xLimit) {
    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentInstanceOf(lineData, D3LineData);
    Preconditions.checkArgumentArrayOf(xLimit, 'number');

    let firstModelSeries = lineData.series.find((series) => {
      return series.lineOptions.id == this.firstModelEl.value;
    });

    let secondModelSeries = lineData.series.find((series) => {
      return series.lineOptions.id == this.secondModelEl.value;
    });

    let firstModelData = D3LineSeriesData.intersectionX(
        firstModelSeries,
        secondModelSeries);

    let secondModelData = D3LineSeriesData.intersectionX(
        secondModelSeries,
        firstModelSeries);

    let firstModelYValues = firstModelData.map((xyPair) => { 
      return xyPair.y; 
    });

    let secondModelYValues = secondModelData.map((xyPair) => { 
      return xyPair.y; 
    });

    let xValues = firstModelData.map((xyPair) => { 
      return xyPair.x;
    });

    let yValues = Tools.percentDifferenceArray(
        firstModelYValues,
        secondModelYValues);
    
    let maxVal = d3.max(yValues, (/** @type {Number} */ y) => { 
      return Math.abs(y); 
    });

    let yLimit = [-maxVal, maxVal];

    let selectedFirstModel = $(':selected', this.firstModelEl).text();
    let selectedSecondModel = $(':selected', this.secondModelEl).text();

    let label = selectedFirstModel + ' Vs. ' + selectedSecondModel;  

    let lineOptions = D3LineOptions.builder()
        .label(label)
        .id('plot-difference')
        .build();

    let diffData = D3LineData.builder()
        .subView(subView)
        .data(xValues, yValues, lineOptions)
        .xLimit(xLimit)
        .yLimit(yLimit)
        .build();

    return diffData;
  }

  /**
   * Return the Y limit for the response spectrum.
   *  
   * @param {Array<HazardServiceResponse>} hazardResponses The hazard responses
   * @returns {Array<Number>} The Y limit
   */
  getResponseSpectraYLimit(hazardResponses) {
    Preconditions.checkArgumentArrayInstanceOf(
        hazardResponses, 
        HazardServiceResponse);

    let minReturnPeriod = 1 / this.parameters.returnPeriod.values.maximum; 
    let maxReturnPeriod = 1 / this.parameters.returnPeriod.values.minimum; 
    
    let spectras = [];

    for (let hazardResponse of hazardResponses) {
      let spectraMin = hazardResponse.toResponseSpectrum(minReturnPeriod);
      let spectraMax = hazardResponse.toResponseSpectrum(maxReturnPeriod);

      spectras.push(spectraMin);
      spectras.push(spectraMax);
    }

    let yValues = [];

    for (let spectra of spectras) {
      for (let data of spectra.data) {
        yValues.push(data.yValues);
      }
    }

    yValues = d3.merge(yValues).filter((y) => { return y > this.Y_MIN_CUTOFF; });

    let maxSpectraGm = d3.max(yValues);
    let minSpectraGm = d3.min(yValues);

    maxSpectraGm = isNaN(maxSpectraGm) ? 1.0 : maxSpectraGm;
    minSpectraGm = isNaN(minSpectraGm) ? 1e-4 : minSpectraGm;
    
    return [minSpectraGm, maxSpectraGm]; 
  }

  /**
   * On longitude or latitude input, check that the coordinate values 
   *     input are good values.
   * 
   * @param {Event} event - The event that triggered the input.
   */
  onCoordinate(event) {
    let model = Tools.stringToParameter(
        this.parameters.models,
        this.firstModelEl.value);

    let region = Tools.stringToParameter(this.parameters.region, model.region);

    this.checkCoordinates(event.target, region);
  }

  /**
   * Update menus and update the second model
   */
  onFirstModelChange() {
    this.setSecondModelMenu();
    this.onModelChange();
    this.latEl.value = null;
    this.lonEl.value = null;
  }

  /**
   * Handler to update the hazard plots on IMT change.
   *
   * @param {String} firstModel The first model value
   * @param {String} secondModel The second model value 
   * @param {Array<HazardServiceResponse>} hazardResponses The hazard responses
   */
  onIMTChange(firstModel, secondModel, hazardResponses) {
    Preconditions.checkArgumentString(firstModel);
    Preconditions.checkArgumentString(secondModel);
    Preconditions.checkArgumentArrayInstanceOf(
        hazardResponses, 
        HazardServiceResponse);

    this.serializeUrls();

    if (firstModel != this.firstModelEl.value ||
        secondModel != this.secondModelEl.value) {
      return;
    }

    this.plotHazardCurves(hazardResponses);
    this.plotHazardComponentCurves(hazardResponses);
  }

  /**
   * Update menus on model change
   */
  onModelChange() {
    this.clearPlots();
    this.setParameterMenu(this.imtEl, this.options.defaultImt);
    this.setParameterMenu(this.vs30El, this.options.defaultVs30);
    this.addInputTooltip();
  }

  /**
   * Handler to update the plots when the return period is changed.
   *  
   * @param {String} firstModel The first model value
   * @param {String} secondModel The second model value 
   * @param {Array<HazardServiceResponse>} hazardResponses The hazard responses
   */
  onReturnPeriodChange(firstModel, secondModel, hazardResponses) {
    Preconditions.checkArgumentString(firstModel);
    Preconditions.checkArgumentString(secondModel);
    Preconditions.checkArgumentArrayInstanceOf(
        hazardResponses, 
        HazardServiceResponse);

    if (firstModel != this.firstModelEl.value ||
        secondModel != this.secondModelEl.value) {
      return;
    }

    this.serializeUrls();
    this.plotResponseSpectrum(hazardResponses);
    this.plotResponseSpectrumComponents(hazardResponses);

    this.plotHazardCurves(hazardResponses);
    this.plotHazardComponentCurves(hazardResponses);
  }

  /**
   * Handler for the return period line being dragged on the hazard curve plot.
   * 
   * @param {Array<HazardServiceResponse>} hazardResponses The hazard responses
   * @param {D3LineSubView} subView The sub view
   * @param {Number} returnPeriod The return period
   * @param {SVGElement} textEl The return period difference text element
   */
  onReturnPeriodDrag(hazardResponses, subView, textEl, returnPeriod) {
    Preconditions.checkArgumentArrayInstanceOf(
        hazardResponses,
        HazardServiceResponse);

    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentInstanceOfSVGElement(textEl);
    Preconditions.checkArgumentNumber(returnPeriod);

    let timeHorizon = 1 / returnPeriod;
    timeHorizon = Number(timeHorizon.toFixed(2));
    this.returnPeriodEl.value = timeHorizon;

    this.checkReturnPeriodButtons();

    this.plotResponseSpectrum(hazardResponses);
    this.plotResponseSpectrumComponents(hazardResponses);
    this.updateGroundMotionDifferenceText(hazardResponses, subView, textEl);
    
    this.serializeUrls();
  }

  /**
   * Handler to update the plots on vs30 change.
   *
   * @param {String} firstModel The first model value
   * @param {String} secondModel The second model value
   * @param {Array<HazardServiceResponse>} hazardResponses The hazard responses
   * @param {Number} vs30Value The vs30
   */
  onVs30Change(firstModel, secondModel, hazardResponses, vs30) {
    Preconditions.checkArgumentString(firstModel);
    Preconditions.checkArgumentString(secondModel);
    Preconditions.checkArgumentString(vs30);
    Preconditions.checkArgumentArrayInstanceOf(
        hazardResponses, 
        HazardServiceResponse);

    if (vs30 != this.vs30El.value ||
        firstModel != this.firstModelEl.value ||
        secondModel != this.secondModelEl.value) {
      this.clearPlots();
    } else {
      this.serializeUrls();
      this.plotResponseSpectrum(hazardResponses);
      this.plotResponseSpectrumComponents(hazardResponses);

      this.plotHazardCurves(hazardResponses);
      this.plotHazardComponentCurves(hazardResponses);
    }
  }

  /**
   * Plot the hazard comonent curves.
   * 
   * @param {Array<HazardServiceResponse>} hazardResponses The hazard responses
   */
  plotHazardComponentCurves(hazardResponses) {
    Preconditions.checkArgumentArrayInstanceOf(
        hazardResponses, 
        HazardServiceResponse);

    let subView = this.hazardComponentView.upperSubView;

    this.hazardComponentLinePlot.clear(subView);

    let yLimit = this.getHazardCurvesYLimit(hazardResponses, this.imtEl.value);

    let lineData = [];
    let lineStyles = [ '-', '--' ];

    for (let index in hazardResponses) {
      let hazardResponse = hazardResponses[index];

      let lineStyle = lineStyles[index];

      let dataBuilder = D3LineData.builder()
          .subView(subView)
          .removeSmallValues(this.Y_MIN_CUTOFF)
          .yLimit(yLimit);

      let response = hazardResponse.getResponse(this.imtEl.value);
      let xValues = response.metadata.xValues;

      let model = response.metadata.model;

      for (let componentData of response.getDataComponents()) {
        let yValues = componentData.yValues;
        
        let lineOptions = D3LineOptions.builder()
            .color(Tools.hazardComponentToColor(componentData.component))
            .id(`${model.value}-${componentData.component}`)
            .label(`${model.year} ${model.region}: ${componentData.component}`)
            .lineStyle(lineStyle)
            .build();

        dataBuilder.data(xValues, yValues, lineOptions);
      }

      lineData.push(dataBuilder.build());
    }

    let hazardComponentData = D3LineData.of(...lineData); 

    this.hazardComponentLinePlot.plot(hazardComponentData);

    let imt = $(':selected', this.imtEl).text();
    let vs30 = $(':selected', this.vs30El).text();
    
    let model = Tools.stringToParameter(
        this.parameters.models,
        this.firstModelEl.value);
    
    let siteTitle = this.testSitePicker.getTestSiteTitle(model.region);
    let title = `Hazard Component Curves: ${siteTitle}, ${imt}, ${vs30}`;

    this.hazardComponentView.setTitle(title);

    let metadata = this.getMetadataHazard();
    this.hazardComponentView.setMetadata(metadata);
    this.hazardComponentView.createMetadataTable();

    this.hazardComponentView.setSaveData(hazardComponentData);
    this.hazardComponentView.createDataTable(hazardComponentData);
  }

  /**
   * Plot the total hazard curve.
   *  
   * @param {Array<HazardServiceResponse>} hazardResponses The hazard responses
   */
  plotHazardCurves(hazardResponses) {
    Preconditions.checkArgumentArrayInstanceOf(
        hazardResponses, 
        HazardServiceResponse);

    let subView = this.hazardView.upperSubView;

    this.hazardLinePlot.clear(subView);

    let metadata = this.getMetadataHazard();

    let yLimit = this.getHazardCurvesYLimit(hazardResponses, this.imtEl.value);

    let dataBuilder = D3LineData.builder()
        .subView(subView)
        .removeSmallValues(this.Y_MIN_CUTOFF)
        .yLimit(yLimit);

    for (let hazardResponse of hazardResponses) {
      let response = hazardResponse.getResponse(this.imtEl.value);
      let totalHazardData = response.getDataComponent('Total');

      let xValues = response.metadata.xValues;
      let yValues = totalHazardData.yValues;

      let model = response.metadata.model;

      let lineOptions = D3LineOptions.builder()
          .id(model.value)
          .label(model.display)
          .build();

      dataBuilder.data(xValues, yValues, lineOptions);
    }
    
    let hazardData = dataBuilder.build();

    this.hazardLinePlot.plot(hazardData);

    let timeHorizon = this.returnPeriodEl.value;
    let returnPeriod = 1 / timeHorizon;

    let returnPeriodPlotEl = this.hazardLinePlot.plotHorizontalRefLine(
          subView,
          returnPeriod);

    let textEl = this.addGroundMotionDifferenceText(hazardResponses, subView);

    let returnPeriodValues = this.parameters.returnPeriod.values;

    let yLimitDrag = [ 
        1 / returnPeriodValues.maximum,
        1 / returnPeriodValues.minimum ];

    this.hazardLinePlot.makeDraggableInY(
        subView,
        returnPeriodPlotEl,
        yLimitDrag,
        (returnPeriod) => {
          this.onReturnPeriodDrag(
              hazardResponses,
              subView,
              textEl,
              returnPeriod);
        });

    let imt = $(':selected', this.imtEl).text();
    let vs30 = $(':selected', this.vs30El).text();
    
    let model = Tools.stringToParameter(
        this.parameters.models,
        this.firstModelEl.value);
    
    let siteTitle = this.testSitePicker.getTestSiteTitle(model.region);
    let title = `Hazard Curves: ${siteTitle}, ${imt}, ${vs30}`;

    this.hazardView.setTitle(title);

    let hazardDiffData = this.plotHazardCurveDifference(hazardData);

    this.hazardView.setMetadata(metadata);
    this.hazardView.createMetadataTable();

    this.hazardView.setSaveData(hazardData, hazardDiffData);
    this.hazardView.createDataTable(hazardData, hazardDiffData);
  }

  /**
   * Plot the total hazard curve difference.
   *  
   * @param {D3LineData} hazardData The hazard data
   */
  plotHazardCurveDifference(hazardData) {
    let subView = this.hazardView.lowerSubView;

    this.hazardLinePlot.clear(subView);

    let xLimit = hazardData.getXLimit(); 

    let hazardDiffData = this.getModelDifferenceData(subView, hazardData, xLimit);

    this.hazardLinePlot.plot(hazardDiffData);
    this.hazardLinePlot.plotZeroRefLine(subView);

    return hazardDiffData;
  }

  /**
   * Plot the response spectrum calculated from the total hazard component.
   *  
   * @param {Array<HazardServiceResponse>} hazardResponses The hazard responses
   */
  plotResponseSpectrum(hazardResponses) {
    Preconditions.checkArgumentArrayInstanceOf(
        hazardResponses, 
        HazardServiceResponse);

    let subView = this.spectraView.upperSubView;

    this.spectraLinePlot.clear(subView);

    let returnPeriod = 1 / this.returnPeriodEl.value; 

    let dataBuilder = D3LineData.builder()
        .subView(subView);

    let pgaBuilder = D3LineData.builder()
        .subView(subView);

    for (let hazardResponse of hazardResponses) {
      let spectra = hazardResponse.calculateResponseSpectrum('Total', returnPeriod);
      let xValues = spectra[0];
      let yValues = spectra[1];

      let model = hazardResponse.response[0].metadata.model; 

      let iPGA = xValues.indexOf(Tools.imtToValue('PGA')); 
      let pgaX = xValues.splice(iPGA, 1);
      let pgaY = yValues.splice(iPGA, 1);

      let pgaOptions = D3LineOptions.builder()
          .id(model.value)
          .label(model.display)
          .lineStyle('none')
          .markerStyle('s')
          .showInLegend(false)
          .build();

      pgaBuilder.data(pgaX, pgaY, pgaOptions, [ 'PGA' ]);

      let lineOptions = D3LineOptions.builder()
          .id(model.value)
          .label(model.display)
          .build();

      dataBuilder.data(xValues, yValues, lineOptions);
    }
   
    let xLimit = this.spectraXDomain;
    let yLimit = this.getResponseSpectraYLimit(hazardResponses);
    
    let spectraLineData = dataBuilder
        .xLimit(xLimit)
        .yLimit(yLimit)
        .build();

    let spectraPGAData = pgaBuilder
        .xLimit(xLimit)
        .yLimit(yLimit)
        .build();

    this.spectraLinePlot.plot(spectraPGAData);
    this.spectraLinePlot.plot(spectraLineData);

    let model = Tools.stringToParameter(
        this.parameters.models,
        this.firstModelEl.value);
    
    let vs30 = $(':selected', this.vs30El).text();
    let siteTitle = this.testSitePicker.getTestSiteTitle(model.region);
    
    let title = `Response Spectrum at ${this.returnPeriodEl.value}` + 
        ` years, ${siteTitle}, ${vs30}`;

    this.spectraView.setTitle(title);

    let metadata = this.getMetadataSpectra();
    this.spectraView.setMetadata(metadata);
    this.spectraView.createMetadataTable();

    let spectraDiffData = this.plotResponseSpectrumDifference(spectraLineData);

    let spectraData = spectraPGAData.concat(spectraLineData); 
    this.spectraView.createDataTable(spectraData, spectraDiffData);
    this.spectraView.setSaveData(spectraData, spectraDiffData);
  }

  /**
   * Plot the response spectrum component curves.
   *  
   * @param {Array<HazardServiceResponse>} hazardResponses The hazard responses
   */
  plotResponseSpectrumComponents(hazardResponses) {
    Preconditions.checkArgumentArrayInstanceOf(
        hazardResponses, 
        HazardServiceResponse);

    let subView = this.spectraComponentView.upperSubView;

    this.spectraComponentLinePlot.clear(subView);

    let returnPeriod = 1 / this.returnPeriodEl.value; 

    let lineStyles = [ '-', '--' ];
    let markerStyles = [ 's', '*' ]
    let lineData = [];
    let pgaData = [];

    let xLimit = this.spectraXDomain;
    let yLimit = this.getResponseSpectraYLimit(hazardResponses);
    
    for (let index in hazardResponses) {
      let hazardResponse = hazardResponses[index];

      let lineStyle = lineStyles[index];
      let markerStyle = markerStyles[index];

      let spectra = hazardResponse.toResponseSpectrum(returnPeriod);

      let dataBuilder = D3LineData.builder()
          .subView(subView)
          .xLimit(xLimit)
          .yLimit(yLimit);

      let pgaBuilder = D3LineData.builder()
          .subView(subView)
          .xLimit(xLimit)
          .yLimit(yLimit);

      let model = hazardResponse.response[0].metadata.model; 

      for (let componentData of spectra.getDataComponents()) {
        let xValues = componentData.xValues;
        let yValues = componentData.yValues;
        
        let iPGA = xValues.indexOf(Tools.imtToValue('PGA')); 
        let pgaX = xValues.splice(iPGA, 1);
        let pgaY = yValues.splice(iPGA, 1);

        let pgaOptions = D3LineOptions.builder()
            .color(Tools.hazardComponentToColor(componentData.component))
            .id(`${model.value}-${componentData.component}`)
            .label(`${model.year} ${model.region}: ${componentData.component}`)
            .lineStyle('none')
            .markerStyle(markerStyle)
            .showInLegend(false)
            .build();

        pgaBuilder.data(pgaX, pgaY, pgaOptions, [ 'PGA' ]);

        let lineOptions = D3LineOptions.builder()
            .color(Tools.hazardComponentToColor(componentData.component))
            .id(`${model.value}-${componentData.component}`)
            .label(`${model.year} ${model.region}: ${componentData.component}`)
            .lineStyle(lineStyle)
            .build();

        dataBuilder.data(xValues, yValues, lineOptions);
      }

      lineData.push(dataBuilder.build());
      pgaData.push(pgaBuilder.build());
    }

    let spectraComponentLineData = D3LineData.of(...lineData);
    let spectraComponentPGAData = D3LineData.of(...pgaData);

    this.spectraComponentLinePlot.plot(spectraComponentPGAData);
    this.spectraComponentLinePlot.plot(spectraComponentLineData);

    let model = Tools.stringToParameter(
        this.parameters.models,
        this.firstModelEl.value);
    
    let vs30 = $(':selected', this.vs30El).text();
    let siteTitle = this.testSitePicker.getTestSiteTitle(model.region);
    
    let title = `Response Spectrum at ${this.returnPeriodEl.value}` + 
        ` years, ${siteTitle}, ${vs30}`;

    this.spectraComponentView.setTitle(title);

    let metadata = this.getMetadataSpectra();
    this.spectraComponentView.setMetadata(metadata);
    this.spectraComponentView.createMetadataTable();

    let spectraData = spectraComponentPGAData.concat(spectraComponentLineData); 
    this.spectraComponentView.createDataTable(spectraData);
    this.spectraComponentView.setSaveData(spectraData);
  }

  /**
   * Plot the response spectrum percent difference.
   *  
   * @param {D3LineData} spectraData The spectra data
   */
  plotResponseSpectrumDifference(spectraData) {
    let subView = this.spectraView.lowerSubView;

    this.spectraLinePlot.clear(subView);

    let spectraDiffData = this.getModelDifferenceData(
        subView,
        spectraData,
        this.spectraXDomain);

    this.spectraLinePlot.plot(spectraDiffData);
    this.spectraLinePlot.plotZeroRefLine(subView);
     
    return spectraDiffData;
  }

  /**
   * @override
   * Get URLs to query.
   */
  serializeUrls() {
    let urls = [];
    let inputs = $(this.inputsEl).serialize();
    let windowUrl = '';

    for (let modelEl of [this.firstModelEl, this.secondModelEl]) {
      let model = Tools.stringToParameter(
          this.parameters.models, 
          modelEl.value);

      urls.push(`${this.dynamicUrl}?model=${model.value}&${inputs}`);

      windowUrl += `&model=${model.value}`; 
    }

    windowUrl += `&${inputs}&imt=${this.imtEl.value}` +
        `&returnperiod=${this.returnPeriodEl.value}`;
    
    window.location.hash = windowUrl.substring(1);
    return urls;
  }

  /**
   * Given the models in nshmp-haz-ws/source/models find only models
   *     that can be compared, ones that have that same region.
   */
  setComparableModels() {
    this.comparableModels = this.parameters.models.values.filter((model) => {
      let regions = this.parameters.models.values.filter((modelCheck) => {
        return model.region == modelCheck.region;
      });
      return regions.length > 1;
    });
  }

  /**
   * Set the first model select menu with only comparable models.
   * See setComparableModels().
   */
  setFirstModelMenu() {
    Tools.setSelectMenu(this.firstModelEl, this.comparableModels); 
    this.firstModelEl.value = this.options.defaultFirstModel;
  }

  /**
   * Set select menus with supported values of the selected models.
   * 
   * @param {HTMLElement} el - The dom element of the select menu to set.
   */
  setParameterMenu(el, defaultValue) {
    let firstModel = Tools.stringToParameter(
        this.parameters.models,
        this.firstModelEl.value);
    
    let secondModel = Tools.stringToParameter(
        this.parameters.models,
        this.secondModelEl.value);
   
    let supports = [];
    supports.push(firstModel.supports[el.id]);
    supports.push(secondModel.supports[el.id]);
    
    let supportedValues = Tools.supportedParameters(
        this.parameters[el.id ], 
        supports);
    
    Tools.setSelectMenu(el, supportedValues); 
    
    let hasDefaultValue = supportedValues.some((val) => {
      return defaultValue == val.value; 
    });

    if (hasDefaultValue) el.value = defaultValue;
  }

  /**
   * Set the second model select menu with only comparable models to 
   *     the first selected model.
   */
  setSecondModelMenu() {
    let selectedModel = Tools.stringToParameter(
        this.parameters.models,
        this.firstModelEl.value);
    
    let comparableModels = this.comparableModels.filter((model) => {
      return selectedModel.region == model.region && 
          selectedModel != model;
    });
    
    Tools.setSelectMenu(this.secondModelEl, comparableModels);
  }

  /**
   * Build the view for the hazard component curves plot
   * 
   * @returns {D3LineView} The spectra line view
   */
  setupHazardComponentView() {
    /* Upper sub view options: hazard plot */
    let upperSubViewOptions = D3LineSubViewOptions.upperBuilder()
        .dragLineSnapTo(1e-10)
        .filename('dynamic-compare-hazard-components')
        .label('Hazard Component Curves')
        .lineLabel('Model')
        .xLabel('Ground Motion (g)')
        .xAxisScale('log')
        .yAxisScale('log')
        .yLabel('Annual Frequency of Exceedence')
        .yValueToExponent(true)
        .build();

    /* Plot view options */
    let viewOptions = D3LineViewOptions.builder()
        .titleFontSize(14)
        .viewSize('min')
        .build(); 

    /* Build the view */
    let view = D3LineView.builder()
        .containerEl(this.contentEl)
        .viewOptions(viewOptions)
        .upperSubViewOptions(upperSubViewOptions)
        .build();

    view.setTitle(this.hazardComponentPlotTitle);
        
    return view;
  }

  /**
   * Build the view for the hazard curve plot with % difference 
   * 
   * @returns {D3LineView} The spectra line view
   */
  setupHazardView() {
    /* Lower sub view options: % difference plot */
    let lowerSubViewOptions = D3LineSubViewOptions.lowerBuilder()
        .defaultYLimit([ -1.0, 1.0 ])
        .filename('dynamic-compare-hazard-difference')
        .label('Hazard Curves Percent Difference')
        .lineLabel('Model')
        .showLegend(false)
        .xLabel('Ground Motion (g)')
        .yLabel('% Difference')
        .build();

    /* Upper sub view legend options: hazard plot */
    let upperLegendOptions = D3LineLegendOptions.upperBuilder()
        .location('bottom-left')
        .build();

    /* Upper sub view options: hazard plot */
    let upperSubViewOptions = D3LineSubViewOptions.upperBuilder()
        .dragLineSnapTo(1e-8)
        .filename('dynamic-compare-hazard')
        .label('Hazard Curves')
        .lineLabel('Model')
        .legendOptions(upperLegendOptions)
        .xLabel('Ground Motion (g)')
        .yAxisScale('log')
        .yLabel('Annual Frequency of Exceedence')
        .yValueToExponent(true)
        .build();

    /* Plot view options */
    let viewOptions = D3LineViewOptions.builder()
        .syncXAxisScale(true, 'log')
        .syncYAxisScale(false)
        .titleFontSize(14)
        .viewSize('min')
        .build(); 

    /* Build the view */
    let view = D3LineView.builder()
        .addLowerSubView(true)
        .containerEl(this.contentEl)
        .viewOptions(viewOptions)
        .lowerSubViewOptions(lowerSubViewOptions)
        .upperSubViewOptions(upperSubViewOptions)
        .build();

    view.setTitle(this.hazardPlotTitle);
        
    return view;
  }

  /**
   * Build the view for the spectra plot with % difference 
   * 
   * @returns {D3LineView} The spectra line view
   */
  setupSpectraComponentView() {
    /* Upper sub view options: spectra plot */
    let upperSubViewOptions = D3LineSubViewOptions.upperBuilder()
        .defaultXLimit(this.spectraXDomain)
        .filename('dynamic-compare-spectra-components')
        .label('Response Spectrum Component Curves')
        .lineLabel('Model')
        .xAxisScale('log')
        .yAxisScale('log')
        .xLabel('Spectral Period (s)')
        .yLabel('Ground Motion (g)')
        .build();

    /* Plot view options */
    let viewOptions = D3LineViewOptions.builder()
        .titleFontSize(14)
        .viewSize('min')
        .build(); 

    /* Build the view */
    let view = D3LineView.builder()
        .containerEl(this.contentEl)
        .viewOptions(viewOptions)
        .upperSubViewOptions(upperSubViewOptions)
        .build();

    view.setTitle(this.spectraComponentPlotTitle);
        
    return view;
  }

  /**
   * Build the view for the spectra plot with % difference 
   * 
   * @returns {D3LineView} The spectra line view
   */
  setupSpectraView() {
    /* Lower sub view options: % difference plot */
    let lowerSubViewOptions = D3LineSubViewOptions.lowerBuilder()
        .defaultXLimit(this.spectraXDomain)
        .defaultYLimit([ -1.0, 1.0 ])
        .filename('dynamic-compare-spectra-difference')
        .label('Response Spectrum Percent Difference')
        .lineLabel('Model')
        .showLegend(false)
        .xLabel('Period (s)')
        .yLabel('% Difference')
        .build();
    
    /* Upper sub view legend options: hazard plot */
    let upperLegendOptions = D3LineLegendOptions.upperBuilder()
        .location('bottom-left')
        .build();

    /* Upper sub view options: spectra plot */
    let upperSubViewOptions = D3LineSubViewOptions.upperBuilder()
        .defaultXLimit(this.spectraXDomain)
        .filename('dynamic-compare-spectra')
        .label('Response Spectrum')
        .legendOptions(upperLegendOptions)
        .lineLabel('Model')
        .xLabel('Spectral Period (s)')
        .yAxisScale('log')
        .yLabel('Ground Motion (g)')
        .build();

    /* Plot view options */
    let viewOptions = D3LineViewOptions.builder()
        .syncXAxisScale(true, 'log')
        .syncYAxisScale(false)
        .titleFontSize(14)
        .viewSize('min')
        .build(); 

    /* Build the view */
    let view = D3LineView.builder()
        .addLowerSubView(true)
        .containerEl(this.contentEl)
        .viewOptions(viewOptions)
        .lowerSubViewOptions(lowerSubViewOptions)
        .upperSubViewOptions(upperSubViewOptions)
        .build();

    view.setTitle(this.spectraPlotTitle);
        
    return view;
  }

  /**
   * Update the ground motion difference text. 
   * 
   * @param {Array<HazardServiceResponse>} hazardResponses The responses
   * @param {D3LineSubView} subView The sub view
   * @param {SVGElement} textEl The SVG text element to update
   */
  updateGroundMotionDifferenceText(hazardResponses, subView, textEl) {
    Preconditions.checkArgumentArrayInstanceOf(
        hazardResponses,
        HazardServiceResponse);

    Preconditions.checkArgumentInstanceOf(subView, D3LineSubView);
    Preconditions.checkArgumentInstanceOfSVGElement(textEl);

    let timeHorizon = this.returnPeriodEl.value;
    let returnPeriod = 1 / timeHorizon;
    let percentDifference = this.getGroundMotionDifference(hazardResponses);
    let text = `${timeHorizon} years, % diff = ${percentDifference}`;

    let xMax = this.hazardLinePlot.getXDomain(subView)[1];
    this.hazardLinePlot.moveText(subView, xMax, returnPeriod, textEl);
    this.hazardLinePlot.updateText(textEl, text);
  }

  /**
   * Call the hazard web service for each model and plot the resuls.
   */
  updatePlot() { 
    let urls = this.serializeUrls();

    let jsonCall = Tools.getJSONs(urls);     
    this.spinner.on(jsonCall.reject, 'Calculating'); 
    
    Promise.all(jsonCall.promises).then((results) => {
      this.spinner.off();

      Tools.checkResponses(results);

      let hazardResponses = results.map((result) => {
        return new HazardServiceResponse(result);
      });
      
      /* Set footer metadata */
      this.footer.setWebServiceMetadata(hazardResponses[0]); 
      
      /* Update tooltips for input */
      this.addInputTooltip();

      /* Plot response spectrum */
      this.plotResponseSpectrum(hazardResponses);

      /* Plot hazard curves */
      this.plotHazardCurves(hazardResponses);

      /* Plot spectra component curves  */
      this.plotResponseSpectrumComponents(hazardResponses);

      /* Plot hazard component curves */
      this.plotHazardComponentCurves(hazardResponses);
     
      let firstModel = this.firstModelEl.value;
      let secondModel = this.secondModelEl.value;
      let vs30 = this.vs30El.value;

      this.updatePlotIMT(firstModel, secondModel, hazardResponses);
      this.updatePlotReturnPeriod(firstModel, secondModel, hazardResponses);
      this.updatePlotVs30(firstModel, secondModel, hazardResponses, vs30);

      /* Get raw data */
      this.footer.onRawDataBtn(urls); 
    }).catch((errorMessage) => {
      this.spinner.off();
      NshmpError.throwError(errorMessage);
    });
  }

  /** Update IMT event handler */
  updatePlotIMT(firstModel, secondModel, hazardResponses) {
    let imtHandler = () => { 
      this.onIMTChange(firstModel, secondModel, hazardResponses)
    };

    this.imtEl.removeEventListener('change', this.imtHandler);
    this.imtEl.addEventListener('change', imtHandler);
    this.imtHandler = imtHandler;
  }

  /** Update return period event handler */
  updatePlotReturnPeriod(firstModel, secondModel, hazardResponses) {
    let returnPeriodEventHandler = () => {
      this.onReturnPeriodChange(firstModel, secondModel, hazardResponses);
    };

    this.returnPeriodEl.removeEventListener(
        'returnPeriodChange',
        this.returnPeriodEventHandler)

    this.returnPeriodEl.addEventListener(
        'returnPeriodChange',
        returnPeriodEventHandler);

    this.returnPeriodEventHandler = returnPeriodEventHandler;
  }

  /** Update vs30 event handler */
  updatePlotVs30(firstModel, secondModel, hazardResponses, vs30) {
    let vs30EventHandler = () => {
      this.onVs30Change(firstModel, secondModel, hazardResponses, vs30);
    };

    this.vs30El.removeEventListener('change', this.vs30EventHandler);
    this.vs30El.addEventListener('change', vs30EventHandler);
    this.vs30EventHandler = vs30EventHandler;
  }

}
