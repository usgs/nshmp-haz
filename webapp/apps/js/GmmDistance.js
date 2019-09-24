
import { D3LineData } from './d3/data/D3LineData.js';
import { D3LineOptions } from './d3/options/D3LineOptions.js';
import { D3LinePlot } from './d3/D3LinePlot.js';
import { D3LineSubViewOptions } from './d3/options/D3LineSubViewOptions.js';
import { D3LineView } from './d3/view/D3LineView.js';
import { D3LineSubView } from './d3/view/D3LineSubView.js';
import { D3LineLegendOptions } from './d3/options/D3LineLegendOptions.js';

import { Gmm } from './lib/Gmm.js';
import NshmpError from './error/NshmpError.js';
import Tools from './lib/Tools.js';

/** 
* @class GmmDistance 
* @extends Gmm
*
* @fileoverview Class for gmm-distance..html, ground motion Vs. 
*   distance web app.
* This class plots the results of nshmp-haz-ws/gmm/distance web service.
* This class will first call out to nshmp-haz-ws/gmm/distance web service
*     to obtain the usage and create the control panel with the following:
*     - Ground motions models
*     - Intensity measure type
*     - Magnitude
*     - zTop 
*     - Dip 
*     - Width 
*     - Vs30
*     - Vs30 measured or inferred
*     - Z1.0
*     - Z2.5 
* Once the control panel is set, it can be used to select desired
*     parameters and plot ground motion vs. distance.
* Already defined DOM elements:
*     - #gmms
*     - .gmm-alpha
*     - .gmm-group
*     - #gmm-sorter
*     - #inputs
*     - #Mw
*     - #vs30
*     - #z1p0
*     - #z2p5
*
* @author bclayton@usgs.gov (Brandon Clayton)
*/
export class GmmDistance extends Gmm {
 
  /**
  * @param {HTMLElement} contentEl - Container element to put plots
  */ 
  constructor(config) {
    let webServiceUrl = '/nshmp-haz-ws/gmm/distance';
    let webApp = 'GmmDistance';
    super(webApp, webServiceUrl, config);
    this.header.setTitle('Ground Motion Vs. Distance');
    
    /**
    * @type {{
    *   rMaxDefault: {number} - Maximum distance,
    *   rMinDefault: {number} - Minimum distance,
    * }} Object 
    */ 
    this.options = {
      rMax: 300,
      rMin: 0.1,
    };
    
    /** @type {number} */
    this.rMax = this.options.rMax;
    /** @type {number} */
    this.rMin = this.options.rMin; 

    /** @type {HTMLElement} */
    this.contentEl = document.querySelector('#content'); 
    /** @type {HTMLElement} */
    this.dipEl = document.querySelector('#dip');
    /** @type {HTMLElement} */
    this.imtEl = document.querySelector('#imt');
    /** @type {HTMLElement} */
    this.widthEl = document.querySelector('#width');
    /** @type {HTMLElement} */
    this.zTopEl = document.querySelector('#zTop');
    
    this.gmmView = this.setupGMMView();
    this.gmmLinePlot = new D3LinePlot(this.gmmView);

    $(this.imtEl).change((event) => { this.imtOnChange(); }); 
    
    this.getUsage();
  }
  
  /**
   * Get current chosen parameters.
   * @return {Map<String, Array<String>>} The metadata Map
   */
  getMetadata() {
    let gmms = this.getCurrentGmms();

    let metadata = new Map();
    metadata.set('Ground Motion Model:', gmms);
    metadata.set('Intensity Measure Type:', [$(this.imtEl).find(':selected').text()]);
    metadata.set('M<sub>W</sub>:', [this.MwEl.value]);
    metadata.set('Z<sub>Top</sub> (km):', [this.zTopEl.value]);
    metadata.set('Dip (°):', [this.dipEl.value]);
    metadata.set('Width (km):', [this.widthEl.value]);
    metadata.set('Minimum Rupture Distance (km):', [this.rMin]);
    metadata.set('Maximum Rupture Distance (km):', [this.rMax]);
    metadata.set('V<sub>S</sub>30 (m/s):', [this.vs30El.value]);
    metadata.set('Z<sub>1.0</sub> (km):', [this.z1p0El.value]);
    metadata.set('Z<sub>2.5</sub> (km):', [this.z2p5El.value]);
    
    return metadata;
  }

  /**
   * Plot the ground motion vs. distance response.
   * 
   * @param {Object} response 
   */
  plotGMM(response) {
    this.gmmLinePlot.clearAll();
    let lineData = this.responseToLineData(response, this.gmmView.upperSubView);
    this.gmmLinePlot.plot(lineData);

    let metadata = this.getMetadata();
    this.gmmView.setMetadata(metadata);
    this.gmmView.createMetadataTable();

    this.gmmView.setSaveData(lineData);
    this.gmmView.createDataTable(lineData);
  }

  /**
   * Convert the response to line data.
   * 
   * @param {Object} response The response
   * @param {D3LineSubView} subView The sub view to plot the line data 
   */
  responseToLineData(response, subView) {
    let dataBuilder = D3LineData.builder().subView(subView);

    for (let responseData of response.means.data) {
      let lineOptions = D3LineOptions.builder()
          .id(responseData.id)
          .label(responseData.label)
          .markerSize(4)
          .build();

      dataBuilder.data(responseData.data.xs, responseData.data.ys, lineOptions);
    }

    return dataBuilder.build();
  }

  /**
  * @override
  * @method serializeGmmUrl
  *
  * Serialize all forms for ground motion web wervice and set 
  *     set the hash of the window location to reflect the form values.
  */
  serializeGmmUrl(){
    let controlInputs = $(this.inputsEl).serialize();
    let inputs = controlInputs + '&' + 
        '&rMin=' + this.rMin +
        '&rMax=' + this.rMax;
    let dynamic = this.config.server.dynamic;
    let url = dynamic + this.webServiceUrl + '?' + inputs;
    window.location.hash = inputs;
    
    return url;
  }
 
  /**
   * Setup the plot view
   */
  setupGMMView() {
    /* Upper sub view legend options */
    let legendOptions = D3LineLegendOptions.upperBuilder() 
        .location('bottom-left')
        .build();

    /* Upper sub view options: gmm vs distance */
    let upperSubViewOptions = D3LineSubViewOptions.upperBuilder()
        .filename('gmm-distance')
        .label('Ground Motion Vs. Distance')
        .legendOptions(legendOptions)
        .lineLabel('Ground Motion Model')
        .xAxisScale('log')
        .xLabel('Distance (km)')
        .yAxisScale('log')
        .yLabel('Median Ground Motion (g)')
        .build();

    let view = D3LineView.builder()
        .containerEl(this.contentEl)
        .upperSubViewOptions(upperSubViewOptions)
        .build();
 
    view.setTitle('Ground Motion Vs. Distance');

    return view;
  }

  /**
  * Call the ground motion web service and plot the results 
  */
  updatePlot() {
    let url = this.serializeGmmUrl(); 
    
    // Call ground motion gmm/distance web service 
    let jsonCall = Tools.getJSON(url);
    this.spinner.on(jsonCall.reject, 'Calculating');

    jsonCall.promise.then((response) => {
      this.spinner.off();
      NshmpError.checkResponse(response, this.plot);

      this.footer.setMetadata(response.server);

      let selectedImt = $(':selected', this.imtEl);
      let selectedImtDisplay = selectedImt.text();

      this.gmmView.setTitle(`Ground Motion Vs. Distance: ${selectedImtDisplay}`);
    
      this.plotGMM(response);

      $(this.footer.rawBtnEl).off() 
      $(this.footer.rawBtnEl).click((event) => {
        window.open(url);
      });
    }).catch((errorMessage) => {
      this.spinner.off();
      this.gmmLinePlot.clearAll();
      NshmpError.throwError(errorMessage);
    });
  }

}
