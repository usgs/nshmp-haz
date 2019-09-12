'use strict';

import Header from './lib/Header.js';

/**
* @fileoverview Set all service information. 
* 
* @class Services
* @author bclayton@usgs.gov (Brandon Clayton)
*/
export default class Services {

  constructor(config) {
    /** @type {Header} */
    this.header = new Header();
    this.header.setTitle('Services');
    
    /** @type {String} */
    this.urlPrefix = config.server.dynamic.trim() != '' ? 
        config.server.dynamic + '/nshmp-haz-ws' :
        window.location.protocol + '//' + 
            window.location.host + '/nshmp-haz-ws';
    
    /** @type {HTMLElement} */
    this.servicesEl = undefined;
    /** @type {HTMLElement} */
    this.serviceMenuEl = undefined; 
    /** @type {HTMLElement} */
    this.menuListEl = undefined; 
    
    // Create services
    this.createOutline();
    this.hazardService();
    this.deaggService();
    this.rateService();
    this.probabilityService();
    this.spectraService();
    this.gmmDistanceService();
    this.hwFwService();
    
    //this.formatUrl();
    this.backToTop()
  }

  /**
  * @method backToTop
  *
  * Create an anchor for going back to the top of the page
  */ 
  backToTop() {
    d3.select(this.menuListEl)
        .append('li')
        .attr('class', 'back-to-top')
        .append('a')
        .attr('href', '#top')
        .text('Back to top');
  }
  
  /**
  * @method createOutline
  *
  * Create the outline of the web page
  */
  createOutline() {
    let containerD3 = d3.select('body')
        .append('div')
        .attr('class', 'container content')
        .attr('id', 'top');
        
    let rowD3 = containerD3.append('div')
        .attr('class', 'row');
    
    let servicesD3 = rowD3.append('div')
        .attr('class', 'col-sm-9 services')
        .attr('role', 'main');
    
    let serviceMenuD3 = rowD3.append('div')
        .attr('class', 'col-sm-3 service-menu')
        .attr('id', 'service-menu')
        .attr('role', 'complimentary')
        .append('div')
        .attr('class', 'affix hidden-xs');

    let menuListD3 = serviceMenuD3.append('ul')
        .attr('class', 'nav service-menu-list');
    
    containerD3.lower(); 
    d3.select(this.header.headerEl).lower();
    this.servicesEl = servicesD3.node();
    this.serviceMenuEl = serviceMenuD3.node();
    this.menuListEl = menuListD3.node(); 
  }

  /**
  * @method deaggService
  *
  * Create the service information for deagg
  */
  deaggService() {
    let svc = {};
    svc.name = 'Deaggregation';
    svc.service = 'deagg';
    svc.id = 'deagg';
    svc.usage = '/nshmp-haz-ws/deagg';
    
    svc.description = 'Deaggregate seismic hazard.'; 
    svc.formats = [
      '/deagg/edition/region/longitude/latitude/imt/vs30/returnperiod',
      '/deagg?edition=value&region=value&longitude=value&' +
          'latitude=value&imt=value&vs30=value&returnperiod=value',
    ];
    svc.parameters = [
      'edition <code>[E2008, E2014]</code>',
      'region <code>[COUS, WUS, CEUS]</code>',
      'longitude <code>(-360..360)</code> °',
      'latitude <code>[-90..90]</code> °',
      'imt (intensity measure type) <code>[PGA, SA0P2, SA1P0]</code>',
      'vs30 <code>[180, 259, 360, 537, 760, 1150, 2000]</code> m/s',
      'returnperiod <code>[1..4000]</code> years',
    ];
    svc.examples = [
      '/deagg/E2008/WUS/-118.25/34.05/PGA/760/2475',
      '/deagg?edition=E2008&region=WUS&longitude=-118.25&' + 
          'latitude=34.05&imt=PGA&vs30=760&returnperiod=2475',
    ];
    svc.info = 'The deaggregation service only supports calculations for' +
        ' a single IMT, which <em>must</em> be specified'; 
    
    this.makeService(svc);
  }

  /**
  * @method gmmDistanceService
  *
  * Create the service information for gmm distance
  */
  gmmDistanceService() {
    let svc = {};
    svc.name = 'Ground Motion Vs. Distance';
    svc.service = 'gmm/distance';
    svc.id = 'gmm-distance';
    svc.usage = '/nshmp-haz-ws/gmm/distance';
    
    svc.description = 'Compute ground motion Vs. distance.'
    svc.formats = [
      '/gmm/distance?gmm=value&Mw=value&imt=value&dip=value&' + 
        'width=value&ztop=value&vs30=value&vsinf=boolean&' +
        'rMin=value&rMax=value&z2p5=value&z1p0=value'
    ];
    svc.parameters = [
      'gmm <code> [AS_97, ZHAO_16_UPPER_MANTLE]</code>',
      'Mw <code> [-2, 9.7]</code>',
      'imt (intensity measure type) <code>[PGA, SA10P0]</code>',
      'dip <code> [0, 90]</code> °',
      'width <code> [0, 60]</code> km',
      'ztop <code> [0, 700]</code> km',
      'vs30 <code> [150, 2000]</code> m/s',
      'vsinf <code> boolean</code>',
      'rMin <code> 0.001</code> km',
      'rMax <code> 300</code> km',
      'z1p0 <code> [0, 5]</code> km',
      'z2p5 <code> [0, 10]</code> km',
    ];
    svc.examples = [
      '/gmm/distance?gmm=AB_06_PRIME&gmm=CAMPBELL_03&gmm=FRANKEL_96&' + 
          'imt=PGA&Mw=6.5&zTop=0.5&dip=90&width=14&vs30=760&' + 
          'vsInf=true&z1p0=&z2p5=&rMin=0.001&rMax=300'
    ];
    svc.info = 'Not all parameters are used by every ground motion' +
        ' model (gmm). At least one "gmm" must be specified. Default' + 
        ' values will be used for any parameters not specified.'; 
     
    this.makeService(svc);
  }

  /**
  * @method hazardService
  *
  * Create the service information for hazard
  */
  hazardService() {
    let svc = {};
    svc.name = 'Hazard';
    svc.service = 'hazard';
    svc.id = 'hazard';
    svc.usage = '/nshmp-haz-ws/hazard';
    
    svc.description = 'Compute probabilisitic seismic hazard ' + 
        'curves at a site of interest.';
    svc.formats = [
        '/hazard/edition/region/longitude/latitude/imt/vs30',
        '/hazard?edition=value&region=value&longitude=value&' +
            'latitude=value&imt=value&vs30=value',
    ];
    svc.parameters = [
      'edition <code>[E2008, E2014]</code>',
      'region <code>[COUS, WUS, CEUS]</code>',
      'longitude <code>(-360..360)</code> °',
      'latitude <code>[-90..90]</code> °',
      'imt (intensity measure type) <code>[PGA, SA0P2, SA1P0]</code>',
      'vs30 <code>[180, 259, 360, 537, 760, 1150, 2000]</code> m/s',
    ];
    svc.examples = [
        '/hazard/E2008/WUS/-118.25/34.05/PGA/760',
        '/hazard?edition=E2008&region=WUS&longitude=-118.25&' + 
            'latitude=34.05&imt=PGA&vs30=760',
    ];
    svc.info = 'For the slash delimited format, multiple, comma-delimited' +
        ' IMTs may be supplied. Alternatively, "any" may be supplied as' + 
        ' the IMT id and the service will return curves for all supported' + 
        ' IMTs. For the name-value pair format, one may use multiple IMT' +
        ' name-value pairs, or omit the IMT argument altogether to return' +
        ' curves for all supported IMTs'
    
    this.makeService(svc);
  }   

  /**
  * @method gmmDistanceService
  *
  * Create the service information for hanging wall effects
  */
  hwFwService() {
    let svc = {};
    svc.name = 'Hanging Wall Effect';
    svc.service = 'gmm/hw-fw';
    svc.id = 'hw-fw';
    svc.usage = '/nshmp-haz-ws/gmm/hw-fw';
    
    svc.description = 'Compute ground motion Vs. distance.'
    svc.formats = [
      '/gmm/hw-fw?gmm=value&Mw=value&imt=value&dip=value&' + 
        'width=value&ztop=value&vs30=value&vsinf=boolean&' +
        'rMin=value&rMax=value&z2p5=value&z1p0=value'
    ];
    svc.parameters = [
      'gmm <code> [AS_97, ZHAO_16_UPPER_MANTLE]</code>',
      'Mw <code> [-2, 9.7]</code>',
      'imt (intensity measure type) <code>[PGA, SA10P0]</code>',
      'dip <code> [0, 90]</code> °',
      'width <code> [0, 60]</code> km',
      'ztop <code> [0, 700]</code> km',
      'vs30 <code> [150, 2000]</code> m/s',
      'vsinf <code> boolean </code>',
      'rMin <code> -20 </code> km',
      'rMax <code> 70 </code> km',
      'z1p0 <code> [0, 5]</code> km',
      'z2p5 <code> [0, 10]</code> km',
    ];
    svc.examples = [
      '/gmm/hw-fw?gmm=AB_06_PRIME&gmm=CAMPBELL_03&gmm=FRANKEL_96&' + 
          'imt=PGA&Mw=6.5&zTop=0.5&dip=90&width=14&vs30=760&' + 
          'vsInf=true&z1p0=&z2p5=&rMin=-20&rMax=70'
    ];
    svc.info = 'Not all parameters are used by every ground motion' +
        ' model (gmm). At least one "gmm" must be specified. Default' + 
        ' values will be used for any parameters not specified.'; 
     
    this.makeService(svc);
  }
  
  /**
  * @method makeService
  *
  * Create a panel with each service's information
  * @param {{
  *   name: {String} - Name of service,
  *   service: {String} - Service url,
  *   id: {String} - an id,
  *   usage: {String} - url,
  *   description: {String} - Description of service,
  *   formats: {Array<String>} - Service url format,
  *   parameters: {Array<String>} - Parameters used in the url,
  *   examples: {Array<String>} - Example urls,
  *   info: {String} - Any additional info abput service,
  * }} svc - Service object
  */
  makeService(svc) {
    this.serviceMenu(svc);
    
    let panelD3 = d3.select(this.servicesEl)
        .append('div')
        .attr('class', 'service')
        .attr('id', svc.id)
        .append('div')
        .attr('class', 'panel panel-default');
    
    // Service name     
    panelD3.append('div')
        .attr('class', 'panel-heading')
        .append('h3')
        .text(svc.service);

    let serviceD3 = panelD3.append('div')
        .attr('class', 'panel-body');
   
    // Service description 
    serviceD3.append('div')
        .attr('class', 'service-div')
        .text(svc.description);

    // Format list
    let formatD3 = serviceD3.append('div')
        .attr('class', 'service-div');
    formatD3.append('h4')
        .text('Formats');
    formatD3.append('ul')
        .selectAll('li')
        .data(svc.formats)
        .enter()
        .append('li')
        .attr('class', 'format-url list-group-item')
        .text((d, i) => { return this.urlPrefix + d });
   
    // Parameter list
    let parD3 = serviceD3.append('div')
        .attr('class', 'service-div');
    parD3.append('h4')
        .text('Parameters');
    parD3.append('ul')
        .selectAll('li')
        .data(svc.parameters)
        .enter()
        .append('li')
        .attr('class', 'list-group-item')
        .html((d, i) => {return d});
   
    // Additional info
    serviceD3.append('div')
        .attr('class', 'service-div')
        .html(svc.info);
    
    // Usage URL
    serviceD3.append('div')
        .attr('class', 'service-div')
        .html('See' + '<a href="' + svc.usage + '"> usage </a> ' +
            'for parameter dependencies');
  
    // Examples
    let exD3 = panelD3.append('div')
        .attr('class', 'panel-footer');
    exD3.append('h4')
        .attr('class', 'examples')
        .text('Examples');
    exD3.selectAll('div')
        .data(svc.examples)
        .enter()
        .append('div')
        .attr('class', 'service-link')
        .append('a')
        .attr('href', (d,i) => {return this.urlPrefix + d})
        .text((d, i) => {return this.urlPrefix + d});
  }
  
  /**
  * @method probabilityService
  *
  * Create the service information for probability
  */
  probabilityService() {
    let svc = {};
    svc.name = 'Probability';
    svc.service = 'probability';
    svc.id = 'probability';
    svc.usage = '/nshmp-haz-ws/probability';
    
    svc.description = 'Compute the Poisson probability of earthquake' +
        ' occurrence at a site of interest.';
    svc.formats = [
      '/probability/edition/region/longitude/latitude/distance',
      '/probability?edition=value&region=value&longitude=value&' +
          'latitude=value&distance=value&timespan=value',
    ];
    svc.parameters = [
      'edition <code>[E2008, E2014]</code>',
      'region <code>[COUS, WUS, CEUS]</code>',
      'longitude <code>(-360..360)</code> °',
      'latitude <code>[-90..90]</code> °',
      'distance <code>[0.01..1000]</code> km',
      'timespan <code>[1..10000]</code> years',
    ];
    svc.examples = [
      '/probability/E2008/WUS/-118.25/34.05/20/50',
      '/probability?edition=E2008&region=WUS&longitude=-118.25' +
          '&latitude=34.05&distance=20&timespan=50',
    ];
    svc.info = '';
     
    this.makeService(svc);
  }
  
  /**
  * @method rateService
  *
  * Create the service information for rate
  */
  rateService() {
    let svc = {};
    svc.name = 'Rate';
    svc.service = 'rate';
    svc.id = 'rate';
    svc.usage = '/nshmp-haz-ws/rate';
    
    svc.description = 'Compute the annual rate of earthquakes at a site' +
        ' of interest.'; 
    svc.formats = [
      '/rate/edition/region/longitude/latitude/distance',
      '/rate?edition=value&region=value&longitude=value&' + 
          'latitude=value&distance=value',
    ];
    svc.parameters = [
      'edition <code>[E2008, E2014]</code>',
      'region <code>[COUS, WUS, CEUS]</code>',
      'longitude <code>(-360..360)</code> °',
      'latitude <code>[-90..90]</code> °',
      'distance <code>[0.01..1000]</code> km',
    ];
    svc.examples = [
      '/rate/E2008/WUS/-118.25/34.05/20',
      '/rate?edition=E2008&region=WUS&longitude=-118.25&' + 
          'latitude=34.05&distance=20',
    ];
    svc.info = '';
     
    this.makeService(svc);
  }

  /**
  * @method serviceMenu
  *
  * Add onto the service menu
  * @param {{
  *   name: {String} - Name of service,
  *   service: {String} - Service url,
  *   id: {String} - an id,
  *   usage: {String} - url,
  *   description: {String} - Description of service,
  *   formats: {Array<String>} - Service url format,
  *   parameters: {Array<String>} - Parameters used in the url,
  *   examples: {Array<String>} - Example urls,
  *   info: {String} - Any additional info abput service,
  * }} svc - Service object
  */
  serviceMenu(svc) {
    d3.select(this.menuListEl).append('li')
        .append('a')
        .attr('href', '#' + svc.id) 
        .text(svc.name);
  }

  /**
  * @method spectraService
  *
  * Create the service information for response spectra
  */
  spectraService() {
    let svc = {};
    svc.name = 'Response Spectra';
    svc.service = 'gmm/spectra';
    svc.id = 'spectra';
    svc.usage = '/nshmp-haz-ws/gmm/spectra';
    
    svc.description = 'Compute determinisitic reponse spectra.'
    svc.formats = [
    '/gmm/spectra?gmm=value&Mw=value&rjb=value&rrup=value&' +
        'rx=value&dip=value&width=value&ztop=value&' + 
        'zhyp=value&rake=value&vs30=value&vsinf=boolean&' +
        'z2p5=value&z1p0=value'
    ];
    svc.parameters = [
      'gmm <code> [AS_97, ZHAO_16_UPPER_MANTLE]</code>',
      'Mw <code> [-2, 9.7]</code>',
      'rjb <code> [0, 1000]</code> km',
      'rrup <code> [0, 1000]</code> km',
      'rx <code> [0, 1000]</code> km',
      'dip <code> [0, 90]</code> °',
      'width <code> [0, 60]</code> km',
      'ztop <code> [0, 700]</code> km',
      'zhyp <code> [0, 700]</code> km',
      'rake <code> [-180, 180]</code> °',
      'vs30 <code> [150, 2000]</code> m/s',
      'vsinf <code> boolean </code>',
      'z1p0 <code> [0, 5]</code> km',
      'z2p5 <code> [0, 10]</code> km',
    ];
    svc.examples = [
    '/gmm/spectra?gmm=AB_06_PRIME&gmm=CAMPBELL_03&gmm=FRANKEL_96&' +
        'mw=8.7&rjb=10.0&rrup=23.0&' +
        'rx=32.0&dip=30.0&width=25.0&ztop=10.0&' + 
        'zhyp=20.0&rake=90.0&vs30=760.0&vsinf=true&' +
        'z2p5=NaN&z1p0=NaN'
    ];
    svc.info = 'Not all parameters are used by every ground motion' +
        ' model (gmm). At least one "gmm" must be specified. Default' + 
        ' values will be used for any parameters not specified.'; 
     
    this.makeService(svc);
  }
  
}
