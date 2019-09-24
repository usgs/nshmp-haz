
import { Preconditions } from '../error/Preconditions.js';
import Tools from '../lib/Tools.js';

/**
 * @fileoverview Container class for general nshmp-haz-ws 
 *    web service responses.
 * 
 * @class WebServiceResponse
 * @author Brandon Clayton
 */
export class WebServiceResponse {

  /**
   * Create new WebServiceResponse given the JSON return from a 
   *    nshmp-haz-ws call.
   *  
   * @param {Object} response The JSON response from web service call
   */
  constructor(response) {
    Tools.checkResponse(response);
    
    Preconditions.checkArgumentObject(response);
    Preconditions.checkStateObjectProperty(response, 'status');
    Preconditions.checkStateObjectProperty(response, 'date');
    Preconditions.checkStateObjectProperty(response, 'url');
    Preconditions.checkStateObjectProperty(response, 'server');

    /** @type {String} The date of request */
    this.date = response.date;

    /** @type {Server} The server metadata */
    this.server = new Server(response);

    /** @type {String} The response status */
    this.status = response.status;

    /** @type {String} The URL query */
    this.url = response.url;

    /** @type {Object} The raw response */
    this._rawResponse = response;
  }

}

/**
 * @fileoverview Container class for the server object in the response.
 * 
 * @class Server
 * @author Brandon Clayton
 */
export class Server {

  /**
   * Create new Server object
   * 
   * @param {Object} The JSON response
   */
  constructor(response) {
    Preconditions.checkArgumentObject(response);
    
    Preconditions.checkStateObjectProperty(response, 'server');
    let server = response.server;
    
    Preconditions.checkStateObjectProperty(server, 'threads');
    Preconditions.checkStateObjectProperty(server, 'servlet');
    Preconditions.checkStateObjectProperty(server, 'calc');
    Preconditions.checkStateObjectProperty(server, 'nshmp-haz');
    Preconditions.checkStateObjectProperty(server, 'nshmp-haz-ws');

    /** @type {Number} The number of threads used*/
    this.threads = server.threads;

    /** @type {String} The servlet call time */
    this.servlet = server.servlet;

    /** @type {String} Calculation time */
    this.calc = server.calc;

    /** @type {ServiceCodeMetadata} Metadata about nshmp-haz */
    this.nshmpHaz = new ServiceCodeMetadata(server['nshmp-haz'])
    
    /** @type {ServiceCodeMetadata} Metadata about nshmp-haz-ws */
    this.nshmpHazWs = new ServiceCodeMetadata(server['nshmp-haz-ws'])
  }

}

/**
 * @fileoverview Container class for the service code metadata under
 *    the server object.
 * 
 * @class ServiceCodeMetadata
 * @author Brandon Clayton
 */
export class ServiceCodeMetadata { 

  /**
   * Create new service code metadata object.
   * 
   * @param {Object} service The service object: 'nshmp-haz' || 'nshmp-haz-ws'
   */
  constructor(service) {
    Preconditions.checkArgumentObject(service);
    Preconditions.checkStateObjectProperty(service, 'url');
    Preconditions.checkStateObjectProperty(service, 'version');

    /** @type {String} The repository url */
    this.url = service.url;

    /** @type {String} The code version */
    this.version = service.version;
  }

}

/**
 * @fileoverview Container class for a generic service parameter.
 * 
 * @class ServiceParameter
 * @author Brandon Clayton
 */
export class ServiceParameter {

  /**
   * Create new service parameter.
   * 
   * @param {Object} parameter The parameter from response
   */
  constructor(parameter) {
    Preconditions.checkArgumentObject(parameter);
    Preconditions.checkStateObjectProperty(parameter, 'id');
    Preconditions.checkStateObjectProperty(parameter, 'value');
    Preconditions.checkStateObjectProperty(parameter, 'display');
    Preconditions.checkStateObjectProperty(parameter, 'displayorder');

    /** @type {Number} The parameter id */
    this.id = parameter.id;

    /** @type {String} The parameters value */
    this.value = parameter.value;

    /** @type {String} The parameter display */
    this.display = parameter.display;
    
    /** @type {Number} The display order */
    this.displayOrder = parameter.displayorder;
  }

}
