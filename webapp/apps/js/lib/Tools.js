
import { D3Utils } from '../d3/D3Utils.js';
import { Preconditions } from '../error/Preconditions.js';

/**
 * @class Tools
 *
 * @fileoverview This class contains static methods that can be used
 *     in any web app.
 *
 * @author bclayton@usgs.gov (Brandon Clayton)
 */
export default class Tools {

  /**
   * Check hazard component.
   * 
   * @param {String} component The component
   */
  static checkHazardComponent(component) {
    Preconditions.checkArgument(
        component == 'Total' || 
            component == 'Grid' ||
            component == 'Interface' || 
            component == 'Fault' ||
            component == 'Slab' ||
            component == 'System' ||
            component == 'Cluster' ||
            component == 'Area',
        `Component [${component}] not supported`);
  }

  /**
   * Check a web service response to see if
   *    response has "status" = "error".
   * 
   * If a web service has an error a NshmpError is thrown
   * 
   * @param {Object} responses The web service responses 
   */
  static checkResponse(response) {
    Preconditions.checkArgumentObject(response);
    Preconditions.checkStateObjectProperty(response, 'status');
    let status = response.status;

    if (status == 'error') {
      hasError = true;
      let errorMessage = `<p> ${response.message} </p> \n`;
      throw new NshmpError(errorMessage);
    }

  }

  /**
   * Check an array of web service responses to see if any web service
   *    response has "status" = "error".
   * 
   * If a web service has an error a NshmpError is thrown
   * 
   * @param {Array<Object>} responses The web service responses 
   */
  static checkResponses(responses) {
    Preconditions.checkArgumentArrayOf(responses, 'object');

    let errorMessage = '';
    let hasError = false;

    for (let response of responses) {
      Preconditions.checkStateObjectProperty(response, 'status');
      let status = response.status;

      if (status == 'error') {
        hasError = true;
        errorMessage += `<p> ${response.message} </p> \n`;
      }
    }

    if (hasError) {
      throw new NshmpError(errorMessage);
    }

  }

  /**
   * Decomposes a data series structrued for D3 to seperate X and 
   *     Y arrays.
   */
  static d3XYDataToArrays(dataSeries) {
    let seriesArrays = [];

    for (let data of dataSeries) {
      let x = [];
      let y = [];
      for (let dataPoint of data) {
        x.push(dataPoint[0]);
        y.push(dataPoint[1]);    
      }
      seriesArrays.push({xValues: x, yValues: y});
    }
    
    return seriesArrays;
  }

  /**
   * Convienice method for a HTTP request that returns an object
   *    with a JavaScript Promise and the Promises's reject function.
   * 
   * This is simply a Promise wrapper for a jQuery
   *    getJSON method that is resolved when getJSON is done
   *    and rejected on getJSON fail.
   * 
   * @typedef {Object} GetJSONObject - The Promise and Promise reject function
   * @property {Promise} promise - The Promise
   * @property {Function} reject - The reject function from the Promise
   * 
   * @param {String} url The HTTP request URL
   * @return {GetJSONObject} The Promise and Promise reject.
   */
  static getJSON(url) {
    let jsonReject;

    let promise = new Promise((resolve, reject) => {
      jsonReject = reject;
      
      let jsonCall = $.getJSON(url);
      jsonCall.done(resolve);
      
      let rejectMessage = 'Could not reach: ' + url;
      jsonCall.fail((err) => {
        reject(rejectMessage);
      });
    });

    return {promise: promise, reject: jsonReject}; 
  }

  /**
   * Convienice method for multiple HTTP request that returns an object
   *    with the JavaScript Promises and the Promises's reject functions.
   * 
   * This is simply a Promise wrapper for a jQuery
   *    getJSON method that is resolved when getJSON is done
   *    and rejected on getJSON fail.
   * 
   * @typedef {Object} GetJSONsObject - The Promises and 
   *    Promise reject functions
   * @property {Array<Promise>} promise - The Promises
   * @property {Array<Function>} rejects - The reject functions 
   *    from the Promises
   * @property {Function} reject - A single reject function
   * 
   * @param {Array<String>} urls The HTTP request URLs
   * @return {GetJSONsObject} The Promises and Promise's rejects.
   */
  static getJSONs(urls) {
    let promises = [];
    let jsonRejects = [];

    for (let url of urls) {
      let jsonCall = Tools.getJSON(url);
      promises.push(jsonCall.promise);
      jsonRejects.push(jsonCall.reject);
    }

    return {promises: promises, rejects: jsonRejects, reject: jsonRejects[0]};
  }

 /**
  * Given an IMT, return the corresponding values.
  * 
  * @param {String} imt - IMT string.
  * @return {Number} the corresponding IMT period value.
  */ 
  static imtToValue(imt) {
    const IMT_VALUES = {
      'PGA': 0.001,
      'SA0P1': 0.1,
      'SA0P2': 0.2,
      'SA0P3': 0.3,
      'SA0P5': 0.5,
      'SA0P75': 0.75,
      'SA1P0': 1.0,
      'SA2P0': 2.0,
      'SA3P0': 3.0,
      'SA4P0': 4.0,
      'SA5P0': 5.0,
    };

    return IMT_VALUES[imt];
  }

  /**
   * Conveince method for calculating percent difference.
   */
  static percentDifference(x0, x1) {
    if (x0 == null || x1 == null) return null;

    Preconditions.checkArgumentNumber(x0);
    Preconditions.checkArgumentNumber(x1);

    let x = ((x0 - x1) / ((x0 + x1) / 2)) * 100.0;

    return Number(x.toFixed(4));
  }

  /**
   * Calculate percent difference on an array.
   * 
   * @param {Array<Number>} x0Values X values
   * @param {Array<Number>} x1Values X values
   * @para {Array<Number>} The percent difference array
   */
  static percentDifferenceArray(x0Values, x1Values) {
    D3Utils.checkArrayIsNumberOrNull(x0Values);
    D3Utils.checkArrayIsNumberOrNull(x1Values);

    Preconditions.checkArgument(
        x0Values.length == x1Values.length,
        'Array lengths must be the same');

    let xValues = [];

    for (let [ x0, x1 ] of d3.zip(x0Values, x1Values)) {
      xValues.push(Tools.percentDifference(x0, x1));
    }

    return xValues;
  }
  
  /**
   * Reset a radio button to a unchecked state.
   * 
   * @param {HTMLElement} inputEl The input form element with type radio.
   */
  static resetRadioButton(inputEl) {
    inputEl.checked = false;
  }

  /**
   * Interpolate between two values at a return period and 
   *     return that value at the return period.
   */
  static returnPeriodInterpolation(x0, x1, y0, y1, returnPeriod) {
    return x0 + 
        ((Math.log10(returnPeriod / y0) * (x1 - x0)) / Math.log10(y1 / y0)); 
  }
 
  /**
   * Add options to a select menu with and id, value, and text.
   * 
   * @param {HTMLElement} el - Select menu dom element to add options.
   * @param {Object} paramValues - Parameters to add as options, containing
   *     a value and display key.
   */
  static setSelectMenu(el, paramValues) {
    d3.select(el)
        .selectAll('option')
        .remove();

    d3.select(el)
        .selectAll('option')
        .data(paramValues.sort(Tools.sortByDisplayOrder))
        .enter()
        .append('option')
        .attr('id', (d, i) => { return d.value; })
        .attr('value', (d, i) => { return d.value; })
        .text((d, i) => { return d.display; });
  }
  
  /**
   * Sort parameters by display order.
   */
  static sortByDisplayOrder(parA, parB) {
    return (parA.displayorder - parB.displayorder);
  }

  /**
   * Given an array of strings of values find the corresponding usage
   *     paramaters with that value. 
   *     For example, editions: ['E2008', 'E2014'], would return 
   *     an array of objects corresponding to E2008 and E2014.
   * 
   * @param {Object} usageParams - Usage parmater from web service.
   *     For example: response.parameters.imt || response.parameters.edition
   * @param {Array<String>} values - String values to match in usage.
   * @return {Array<Object>} Array of usage objects.
   */
  static stringArrayToParameters(usageParams, values) {
    let parameters = usageParams.values.filter((par, i) => {
      return values.find((val, iv) => {
        return par.value == val;
      })
    });

    return parameters;
  }

  /**
   * Given a string of a value, find the corresponding usage
   *     paramaters with that value. 
   *     For example, editions: 'E2008', would return 
   *     an object corresponding to E2008.
   * 
   * @param {Object} usageParams - Usage parmater from web service.
   *     For example: response.parameters.imt || response.parameters.edition
   * @param {String} values - String value to match in usage.
   * @return {Object} Usage objects.
   */
  static stringToParameter(usageParams, value) {
    return usageParams.values.find((par, i) => {
      return par.value == value;
    });
  }
  
  /**
   * Given an array of an array of string values, find the common
   *     values that appear in each array. Then find all 
   *     parameter object in the usage that match the common
   *     values.
   * 
   * @param {Object} usageParams - Usage parmater from web service.
   *     For example: response.parameters.imt || response.parameters.edition
   * @param {Array<Array<String>>}
   * @return {Array<Object>} Array of usage objects.
   */
  static supportedParameters(usageParams, supports) {
    let uniqueValues = [];
    supports.toString().split(',').forEach((val) => {
      if ($.inArray(val, uniqueValues) == -1) {
        uniqueValues.push(val);
      }
    });
    
    let commonValues = uniqueValues.filter((val, iuv) => {
      return supports.every((support, is) => {
        return support.includes(val);
      })
    });
    
    return Tools.stringArrayToParameters(usageParams, commonValues);
  }
   
  /**
   * Take a URL string and convert into object of key/value pairs.
   * If there are multiple of the same key then the values will be put in 
   *   an array.
   * 
   * @param {String} url - String to convert to object.
   * @return {Object} - Object of key/value pairs from URL string.
   */
  static urlQueryStringToObject(url) {
    let urlObject = {};
    let pairs = url.split('&');
    pairs.forEach((pair, i) => {
      let key = pair.split('=')[0];
      let value = pair.split('=')[1];
      
      if (urlObject[key] != undefined && !Array.isArray(urlObject[key])) {
        urlObject[key] = [urlObject[key]]; 
      } 
      
      if (urlObject[key] != undefined && Array.isArray(urlObject[key])) {
        urlObject[key].push(value);
      } else 
        urlObject[key] = value;
    });
    
    return urlObject;
  }

  /**
   * Given an IMT period value in seconds, return the 
   *     corresponding IMT string.
   * 
   * @param {Number} value - The IMT value.
   */
  static valueToImt(value) {
    const IMT_VALUES = {
      'PGA': 'PGA',
      '0.1': 'SA0P1',
      '0.2': 'SA0P2',
      '0.3': 'SA0P3',
      '0.5': 'SA0P5',
      '0.75': 'SA0P75',
      '1': 'SA1P0',
      '2': 'SA2P0',
      '3': 'SA3P0',
      '4': 'SA4P0',
      '5': 'SA5P0',
    };

    return IMT_VALUES[value];
  }

  /**
   * Colors associated with hazard components.
   * 
   * @param {String} component The hazard component
   * @param {String} The color for the component
   */
  static hazardComponentToColor(component) {
    let colors = d3.schemeCategory10;

    const COMPONENT_COLORS  = {
      'Grid': colors[0],
      'Slab': colors[1],
      'Interface': colors[2],
      'Fault': colors[3],
      'System': colors[4],
      'Cluster': colors[5],
      'Area': colors[6],
    };

    return COMPONENT_COLORS[component];
  }
   
}
