
import { WebServiceResponse, ServiceParameter } from './WebServiceResponse.js';
import { Preconditions } from '../error/Preconditions.js';
import Tools from '../lib/Tools.js';

/**
 * @fileoverview Container class for hazard web service response.
 * 
 * @class HazardServiceResponse
 * @author Brandon Clayton
 */
export class HazardServiceResponse extends WebServiceResponse {

  /**
   * Create new HazardServiceResponse.
   * 
   * @param {Object} hazardResponse The hazard JSON response
   */
  constructor(hazardResponse) {
    Preconditions.checkArgumentObject(hazardResponse);
    super(hazardResponse);

    Preconditions.checkStateObjectProperty(hazardResponse, 'response');
    Preconditions.checkStateArrayOf(hazardResponse.response, 'object');

    /** @type Array<HazardResponse> The hazard responses */
    this.response = hazardResponse.response.map((response) => {
      return new HazardResponse(response);
    });
  }

  /**
   * Get a specific IMT response.
   *  
   * @param {String} imt The IMT
   * @returns {HazardResponse} The hazard response for the IMT
   */
  getResponse(imt) {
    Preconditions.checkArgumentString(imt);

    return this.response.find((response) => { 
      return response.metadata.imt.value == imt;
    });
  }

  /**
   * Calculate the response spectrum for a specific component 
   *    and return period.
   *  
   * @param {String} component The hazard component
   * @param {Number} returnPeriod The return period
   * @returns {Array<Array<Number>>} The response spectrum: 
   *    [ X values, Y values ]
   */
  calculateResponseSpectrum(component, returnPeriod) {
    let xValues = [];
    let yValues = [];

    for (let response of this.response) {
      xValues.push(Tools.imtToValue(response.metadata.imt.value));
      yValues.push(response.calculateResponseSpectrum(component, returnPeriod));
    }

    return [ xValues, yValues ];
  }

  /**
   * Convert to response spectrum and return new HazardResponseSpectrum
   * 
   * @param {Number} returnPeriod The return period
   * @returns {HazardResponseSpectrum} The response spectrum
   */
  toResponseSpectrum(returnPeriod) {
    Preconditions.checkArgumentNumber(returnPeriod);

    return new HazardResponseSpectrum(this, returnPeriod);
  }

}

/**
 * @fileoverview Container class for a hazard response for a IMT.
 * 
 * @class HazardResponse
 * @author Brandon Clayton
 */
export class HazardResponse {
  
  /**
   * Create new HazardResponse
   * 
   * @param {Object} response The JSON response
   */
  constructor(response) {
    Preconditions.checkArgumentObject(response);
    Preconditions.checkStateObjectProperty(response, 'metadata');
    Preconditions.checkStateObjectProperty(response, 'data');
    Preconditions.checkStateArrayOf(response.data, 'object');

    /** @type {HazardResponseMetadata} The response metadata */
    this.metadata = new HazardResponseMetadata(response);

    /** @type {Array<HazardResponseData>} The response data */
    this.data = response.data.map((data) => {
      return new HazardResponseData(data, this.metadata.xValues);
    });

  }

  /**
   * Calculate the response spectrum at a specific
   *    hazard component and return period.
   *  
   * @param {String} component The hazard component
   * @param {Number} returnPeriod The return period
   * @returns {Number} The response spectrum value
   */
  calculateResponseSpectrum(component, returnPeriod) {
    Tools.checkHazardComponent(component);
    Preconditions.checkArgumentNumber(returnPeriod);

    let responseData = this.getDataComponent(component);
    let xValues = this.metadata.xValues;
    let yValues = responseData.yValues;

    let afeIndexBelowReturnPeriod = yValues.findIndex((y) => {
      return y < returnPeriod;
    });

    let x0 = xValues[afeIndexBelowReturnPeriod - 1];
    let x1 = xValues[afeIndexBelowReturnPeriod];
    let y0 = yValues[afeIndexBelowReturnPeriod - 1];
    let y1 = yValues[afeIndexBelowReturnPeriod];

    let x = Tools.returnPeriodInterpolation(x0, x1, y0, y1, returnPeriod);
    x = isNaN(x) ? null : Number(x.toFixed(6));

    return x;
  }

  /**
   * Get a specific hazard component.
   * 
   * @param {String} component The component
   * @returns {HazardResponseData} The data
   */
  getDataComponent(component) {
    Tools.checkHazardComponent(component);

    return this.data.find((data) => {
      return data.component == component;
    });
  }

  /**
   * Get all data components except for Total
   * 
   * @returns {Array<HazardResponseData} The data
   */
  getDataComponents() {
    return this.data.filter((data) => {
      return data.component != 'Total';
    });
  }

}

/**
 * @fileoverview Container class for the HazardResponse metadata.
 * 
 * @class HazardResponseMetadata
 * @author Brandon Clayton
 */
export class HazardResponseMetadata {

  /**
   * Create new HazardResponseMetadata.
   *  
   * @param {Object} response The JSON response
   */
  constructor(response) {
    Preconditions.checkArgumentObject(response);
    Preconditions.checkStateObjectProperty(response, 'metadata');
    let metadata = response.metadata;

    Preconditions.checkStateObjectProperty(metadata, 'model');
    Preconditions.checkStateObjectProperty(metadata, 'latitude');
    Preconditions.checkStateObjectProperty(metadata, 'longitude');
    Preconditions.checkStateObjectProperty(metadata, 'imt');
    Preconditions.checkStateObjectProperty(metadata, 'vs30');
    Preconditions.checkStateObjectProperty(metadata, 'xlabel');
    Preconditions.checkStateObjectProperty(metadata, 'ylabel');
    Preconditions.checkStateObjectProperty(metadata, 'xvalues');
    Preconditions.checkStateArrayOf(metadata.xvalues, 'number');

    /** @type {HazardSourceModel} The source model */
    this.model = new HazardSourceModel(metadata.model);

    /** @type {Number} The latitude */
    this.latitude = metadata.latitude;
    
    /** @type {Number} The longitude */
    this.longitude = metadata.longitude;

    /** @type {ServiceParameter} The IMT parameter */
    this.imt = new ServiceParameter(metadata.imt);

    /** @type {ServiceParameter} The vs30 parameter */
    this.vs30 = new ServiceParameter(metadata.vs30); 
    
    /** @type {String} The X label */
    this.xLabel = metadata.xlabel;
    
    /** @type {Array<Number>} The X values */
    this.xValues = metadata.xvalues;

    /** @type {String} The Y label */
    this.yLabel = metadata.ylabel;
  }

}

/**
 * @fileoverview Container class for the hazard model.
 * 
 * @class HazardSourceModel
 * @author Brandon Clayton
 */
export class HazardSourceModel extends ServiceParameter {

  constructor(model) {
    Preconditions.checkArgumentObject(model);
    super(model);

    Preconditions.checkStateObjectProperty(model, 'region');
    Preconditions.checkStateObjectProperty(model, 'path');
    Preconditions.checkStateObjectProperty(model, 'supports');
    Preconditions.checkStateObjectProperty(model, 'year');

    /** @type {String} The model region */
    this.region = model.region;

    /** @type {String} The path to the model */
    this.path = model.path;

    /** @type {HazardSourceModelSupports} The supported IMTs and vs30s */
    this.supports = new HazardSourceModelSupports(model.supports);

    /** @type {Number} The model year */
    this.year = model.year;
  }

}

/**
 * @fileoverview Container class for the hazard source model supports object.
 * 
 * @class HazardSourceModelSupports
 * @author Brandon Clayton
 */
export class HazardSourceModelSupports {
  
  constructor(supports) {
    Preconditions.checkArgumentObject(supports);
    Preconditions.checkStateObjectProperty(supports, 'imt');
    Preconditions.checkStateObjectProperty(supports, 'vs30');

    /** @type {Array<String>} The supported IMTs */
    this.imt = supports.imt;

    /** @type {Array<String>} The supported vs30s */
    this.vs30 = supports.vs30;
  }

}

/**
 * @fileoverview Container class for the hazard data.
 * 
 * @class HazardResponseData
 * @author Brandon Clayton
 */
export class HazardResponseData { 

  /**
   * Create new HazardResponseData.
   * 
   * @param {Object} data The data JSON
   * @param {Array<Number>} xValues The X values from metadata
   */
  constructor(data, xValues) {
    Preconditions.checkArgumentObject(data);
    Preconditions.checkArgumentArrayOf(xValues, 'number');
    
    Preconditions.checkStateObjectProperty(data, 'component');
    Preconditions.checkStateObjectProperty(data, 'yvalues');
    Preconditions.checkStateArrayOf(data.yvalues, 'number');

    /** @type {String} The hazard curve component */
    this.component = data.component;

    /** @type {Array<Number>} The X values */
    this.xValues = xValues; 

    /** @type {Array<Number>} The Y values */
    this.yValues = data.yvalues;
  }

}

/**
 * @fileoverview Container class from the hazard response 
 *    spectrum calculations.
 * 
 * @class HazardResponseSpectrum
 * @author Brandon Clayton 
 */
export class HazardResponseSpectrum {

  /**
   * Create new HazardResponseSpectrum.
   * 
   * @param {HazardServiceResponse} serviceResponse The hazard service response
   * @param {Number} returnPeriod The return period
   */
  constructor(serviceResponse, returnPeriod) {
    Preconditions.checkArgumentInstanceOf(serviceResponse, HazardServiceResponse);
    Preconditions.checkArgumentNumber(returnPeriod);

    this.data = serviceResponse.response[0].data.map((data) => {
      return new ResponseSpectrumData(
          serviceResponse,
          data.component,
          returnPeriod);
    });
  }
  
  /**
   * Get all hazard data components except for Total.
   */
  getDataComponents() {
    return this.data.filter((data) => {
      return data.component != 'Total';
    });
  }

  /**
   * Get a specific hazard data component.
   * 
   * @param {String} component The component
   */
  getDataComponent(component) {
    Tools.checkHazardComponent(component);

    return this.data.find((data) => {
      return data.component == component;
    });
  }

}

/**
 * @fileoverview Container class for response spectrum data.
 * 
 * @class ResponseSpectrumData
 * @author Brandon Clayton
 */
export class ResponseSpectrumData {

  /**
   * Create new ResponseSpectrumData.
   * 
   * @param {HazardServiceResponse} serviceResponse The service response
   * @param {String} component The hazard component 
   * @param {Number} returnPeriod The return period
   */
  constructor(serviceResponse, component, returnPeriod) {
    Preconditions.checkArgumentInstanceOf(
        serviceResponse,
        HazardServiceResponse);

    Preconditions.checkArgumentNumber(returnPeriod);

    let spectra = serviceResponse.calculateResponseSpectrum(
        component,
        returnPeriod);

    this.component = component;

    this.xValues = spectra[0];

    this.yValues = spectra[1];
  }

}
