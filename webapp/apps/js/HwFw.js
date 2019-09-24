
import { D3LineData } from './d3/data/D3LineData.js';
import { D3LineOptions } from './d3/options/D3LineOptions.js';
import { D3LinePlot } from './d3/D3LinePlot.js';
import { D3LineSubViewOptions } from './d3/options/D3LineSubViewOptions.js';
import { D3LineView } from './d3/view/D3LineView.js';
import { D3LineSubView } from './d3/view/D3LineSubView.js';
import { D3LineViewOptions } from './d3/options/D3LineViewOptions.js';
import { D3SaveFigureOptions } from './d3/options/D3SaveFigureOptions.js';

import Constraints from './lib/Constraints.js';
import { Gmm } from './lib/Gmm.js';
import Tools from './lib/Tools.js';
import NshmpError from './error/NshmpError.js';

/** 
* @class HwFw
* @extends Gmm
*
* @fileoverview Class for hw-fw.html, hanging wall effects web app.
* This class plots the results of nshmp-haz-ws/gmm/hw-fw web service.
* This class will first call out to nshmp-haz-ws/gmm/hw-fw web service
*     to obtain the usage and create the control panel with the following:
*     - Ground motions models
*     - Intensity measure type
*     - Magnitude
*     - Vs30
*     - Vs30 measured or inferred
*     - Z1.0
*     - Z2.5 
* Once the control panel is set, it can be used to select desired
*     parameters and plot ground motion vs. distance.
* A fault plane is shown underneath the ground motion vs. distance plot.
* To show hanging wall effects, three range sliders are shown next to the
*     fault plane and control the fault plane's:
*     - Dip (range: 0-90)
*     - Width (range: 1-30km)
*     - zTop (range: 0-10km)
* The fault plane is limited to having a fault bottom of 20km.
* Once the fault plane is changed with either of the sliders, the 
*   ground motions vs. distance plot is updated automatically. 
*
* @author bclayton@usgs.gov (Brandon Clayton)
*/
export class HwFw extends Gmm {
 
  /**
  * @param {HTMLElement} contentEl - Container element to put plots
  */ 
  constructor(config) {
    let webServiceUrl = '/nshmp-haz-ws/gmm/hw-fw';
    let webApp = 'HwFw';
    super(webApp, webServiceUrl, config);
    this.header.setTitle('Hanging Wall Effects');

    /**
    * @type {{
    *   lowerPlotWidth: {number} - Lower plot width in percentage,
    *   minDip: {number} - Minimum dip allowed in degrees,
    *   minWidth: {number} - Minimum width allowed in km,
    *   minZTop: {number} - Minimum zTop allowed in km,
    *   maxDip: {number} - Maximum dip allowed in degrees,
    *   maxWidth: {number} - Maximum width allowed in km,
    *   maxZTop: {number} - Maximum zTop allowed in km,
    *   maxFaultBottom: {number} - Maximum fault bottom allowed in km,
    *   rMaxDefault: {number} - Maximum distance,
    *   rMinDefault: {number} - Minimum distance,
    *   stepDip: {number} - Step in dip in degrees,
    *   stepWidth: {number} - Step in width in km,
    *   stepZTop: {number} - step in zTop in km
    * }} Object 
    */ 
    this.options = {
      lowerPlotWidth: 0.65,  
      minDip: 10,
      minWidth: 1,
      minZTop: 0,
      maxDip: 90,
      maxWidth: 30,
      maxZTop: 10,
      maxFaultBottom: 20,
      rMax: 70,
      rMin: -20,
      stepDip: 5,
      stepWidth: 0.5,
      stepZTop: 0.5,
    };
    
    /** @type {number} */
    this.rMax = this.options.rMax;
    /** @type {number} */
    this.rMin = this.options.rMin; 
    
    /** @type {HTMLElement} */
    this.contentEl = document.querySelector("#content");
    /** @type {HTMLElement} */
    this.dipEl = undefined; 
    /** @type {HTMLElement} */
    this.dipSliderEl = undefined; 
    /** @type {HTMLElement} */
    this.imtEl = document.querySelector('#imt');
    /** @type {HTMLElement} */
    this.widthEl = undefined; 
    /** @type {HTMLElement} */
    this.widthSliderEl = undefined;  
    /** @type {HTMLElement} */
    this.zTopEl = undefined; 
    /** @type {HTMLElement} */
    this.zTopSliderEl = undefined;
    
    this.xLimit = [ this.rMin, this.rMax ];

    this.gmmView = this.setupGMMView();
    this.gmmLinePlot = new D3LinePlot(this.gmmView);

    this.faultXLimit = this.gmmView.lowerSubView.options.defaultXLimit;
    this.faultYLimit = [ 0, this.options.maxFaultBottom ];
    
    $(this.imtEl).change((event) => { this.imtOnChange(); }); 
    
    this.faultSliders();
    
    this.getUsage(this.setSliderValues);
  }
  
  /**
  * @method checkFaultExtent
  *
  * Check to see if the fault plane is or well be out of the 
  *     defined fault bottom maximum
  * @return {{
  *   maxDip: {number} - Max dip allowed given other values,
  *   maxWidth: {number} - Max width allowed given other values,
  *   maxZTop: {number} - Max zTop allowed given other values,
  *   pastExtent: {Boolean}
  * }} 
  */
  checkFaultExtent() { 
    let faultCheck = {};
    let dip = this.dip_val();
    let width = this.width_val();
    let zTop = this.zTop_val();
    let faultBottom = width * Math.sin(dip) + zTop;
    let maxFaultBottom = this.options.maxFaultBottom;
    faultCheck.maxDip = Math.asin((maxFaultBottom - zTop) / width);
    faultCheck.maxDip = faultCheck.maxDip * 180.0 / Math.PI;
    faultCheck.maxDip = isNaN(faultCheck.maxDip) ? 90 : faultCheck.maxDip;
    faultCheck.maxWidth = (maxFaultBottom - zTop) / Math.sin(dip);
    faultCheck.maxZTop = maxFaultBottom - width * Math.sin(dip); 
    faultCheck.pastExtent = faultBottom > maxFaultBottom ? true : false;
    
    return faultCheck;
  }
 
  /**
  * @method faultSliders
  *
  * Create range sliders for the fault plane plot
  */
  faultSliders() {
    let sliderInfo = [
      {
        name: 'Dip', 
        sliderId: 'dip-slider',
        valueId: 'dip', 
        min: this.options.minDip, 
        max: this.options.maxDip, 
        step: this.options.stepDip,
        unit: '°',
      },{
        name: 'Width', 
        sliderId: 'width-slider', 
        valueId: 'width', 
        min: this.options.minWidth, 
        max: this.options.maxWidth, 
        step: this.options.stepWidth,
        unit: 'km',
      },{
        name: 'zTop', 
        sliderId: 'zTop-slider', 
        valueId: 'zTop', 
        min: this.options.minZTop, 
        max: this.options.maxZTop, 
        step: this.options.stepZTop,
        unit: 'km',
      }
    ];      
   
    let width = (1 - this.options.lowerPlotWidth) * 100; 
    d3.select(this.gmmView.lowerSubView.svg.svgEl)
        .style('margin-right', width + '%');
  
    let faultFormD3 = d3.select(this.gmmView.lowerSubView.subViewBodyEl)
        .append('form')
        .attr('class', 'form fault-form');
  
    let divD3 = faultFormD3.selectAll('div')
        .data(sliderInfo)
        .enter()
        .append('div')
        .attr('class', 'slider-form');
        
    divD3.append('label')
        .attr('for', (d,i) => { return d.sliderId })
        .text((d,i) => { return d.name });
    
    let formD3 = divD3.append('div') 
        .attr('class', 'row');
        
    formD3.append('div')
        .attr('class', 'col-sm-12 col-md-12 col-lg-8')
        .html((d,i) => {
          return '<input class="slider" id=' + d.sliderId + ' type="range"' + 
            ' min=' + d.min + ' max=' + d.max + 
            ' step=' + d.step + ' />'
        });
   
    formD3.append('div')
        .attr('class', 'col-sm-12 col-md-6 col-lg-4')
        .append('div')
        .attr('class', 'input-group input-group-sm')
        .html((d,i) => { 
          return '<input class="form-control input-sm slider-value"' +
            ' id=' + d.valueId + ' type="number"' + 
            'name="' + d.valueId  + '"' +
            ' min=' + d.min + ' max=' + d.max + ' step="' + d.step + '" >' + 
            '<span class="input-group-addon input-sm"> ' + d.unit + ' </span>';
        });

    this.dipSliderEl = document.querySelector('#dip-slider');
    this.dipEl = document.querySelector('#dip');
    this.faultFormEl = document.querySelector('.fault-form');
    this.widthSliderEl = document.querySelector('#width-slider');
    this.widthEl = document.querySelector('#width');
    this.zTopSliderEl = document.querySelector('#zTop-slider');
    this.zTopEl = document.querySelector('#zTop');
    
    // Update tooltips   
    Constraints.addTooltip(
        this.dipEl, this.options.minDip, this.options.maxDip); 
    Constraints.addTooltip(
        this.widthEl, this.options.minWidth, this.options.maxWidth); 
    Constraints.addTooltip(
        this.zTopEl, this.options.minZTop, this.options.maxZTop); 
    
    // Listen for changes on fault form inputs and sliders
    $('.fault-form').bind('input keyup mouseup', (event) => { 
      this.inputsOnInput();
      this.faultSliderOnChange(event) 
    });
  }

  /**
  * @method faultSlidersOnChange
  *
  * Update the fault plane plot with change in each slider or input
  *     field and update ground motion Vs. distance plot if inputted
  *     values are good. 
  * @param {!Event} event - Event that triggered the change 
  */
  faultSliderOnChange(event) {
    let minVal;
    let maxVal;
    let maxValStr; 
    let parEl; 
    let step;
    let sliderEl;
    let valueEl; 
      
    let id = event.target.id;
    let value = parseFloat(event.target.value);
    let inputType = event.target.type;
    let eventType = event.type;
    
    if (!id || id.length == 0 || isNaN(value)){ 
      return;  
    }

    if (id == this.dipSliderEl.id || id == this.dipEl.id) {
      parEl = this.dipEl; 
      sliderEl = this.dipSliderEl;
      valueEl = this.dipEl;
      maxValStr = 'maxDip'; 
      step = this.options.stepDip;
      maxVal = this.options.maxDip;
      minVal = this.options.minDip;
      
    } else if (id == this.widthSliderEl.id || id == this.widthEl.id) {
      parEl = this.widthEl;
      sliderEl = this.widthSliderEl;
      valueEl = this.widthEl;
      step = this.options.stepWidth;
      maxValStr = 'maxWidth';
      maxVal = this.options.maxWidth;
      minVal = this.options.minWidth;
    } else if (id == this.zTopSliderEl.id || id == this.zTopEl.id) {
      parEl = this.zTopEl;
      sliderEl = this.zTopSliderEl;
      valueEl = this.zTopEl;
      maxValStr = 'maxZTop';
      step = this.options.stepZTop;
      maxVal = this.options.maxZTop;
      minVal = this.options.minZTop;
    }
    
    let canSubmit = Constraints.check(valueEl, minVal, maxVal);
    if (!canSubmit) return; 
    
    parEl.value = value;
    sliderEl.value = value;
    valueEl.value = value;
    let faultCheck = this.checkFaultExtent();
    if (faultCheck.pastExtent) {
      // Round down to nearest step
      event.target.value = 
          Math.round((faultCheck[maxValStr] - step) / step) * step; 
      valueEl.value = event.target.value;
      parEl.value = event.target.value;
      return;
    }
     
    this.plotFaultPlane();
    if (inputType == 'range' && 
          (eventType == 'keyup' || eventType == 'mouseup')) {
      this.updatePlot();
    } else if (inputType != 'range') {
      this.updatePlot();
    }
  }

  /**
   * Get current fault plane line data
   */
  getFaultPlaneLineData() {
    let dip = this.dip_val();
    let width = this.width_val();
    let zTop = this.zTop_val();
    
    let xMin = 0;
    let xMax = width * Math.cos(dip);
    let x = [xMin, Number(xMax.toFixed(4))];
    
    let yMin = zTop;
    let yMax =  width * Math.sin(dip) + zTop;
    let y = [yMin, Number(yMax.toFixed(4))];
    
    let lineOptions = D3LineOptions.builder()
        .id('fault')
        .label('Fault Plane')
        .markerSize(0)
        .build();

    let lineData = D3LineData.builder()
        .subView(this.gmmView.lowerSubView)
        .data(x, y, lineOptions)
        .xLimit(this.faultXLimit)
        .yLimit(this.faultYLimit)
        .build();

    return lineData;
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

  plotFaultPlane() {
    this.gmmLinePlot.clear(this.gmmView.lowerSubView);
    let lineData = this.getFaultPlaneLineData();
    this.gmmLinePlot.plot(lineData);
  }

  /**
   * Plot the ground motion vs. distance response.
   * 
   * @param {Object} response 
   */
  plotGMM(response) {
    this.gmmLinePlot.clear(this.gmmView.upperSubView);
    let lineData = this.responseToLineData(response, this.gmmView.upperSubView);
    this.gmmLinePlot.plot(lineData);

    let metadata = this.getMetadata();
    this.gmmView.setMetadata(metadata);
    this.gmmView.createMetadataTable();

    this.gmmView.setSaveData(lineData);
    this.gmmView.createDataTable(lineData);

    this.plotFaultPlane();
  }

  /**
   * Convert the response to line data.
   * 
   * @param {Object} response The response
   * @param {D3LineSubView} subView The sub view to plot the line data 
   */
  responseToLineData(response, subView) {
    let dataBuilder = D3LineData.builder().subView(subView).xLimit(this.xLimit);

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
   * Setup the plot view
   */
  setupGMMView() {
    /* Upper sub view options: gmm vs distance */
    let upperSubViewOptions = D3LineSubViewOptions.upperBuilder()
        .defaultXLimit(this.xLimit)
        .filename('hanging-wall-effects')
        .label('Ground Motion Vs. Distance')
        .lineLabel('Ground Motion Model')
        .xAxisScale('linear')
        .xLabel('Distance (km)')
        .yAxisScale('linear')
        .yLabel('Median Ground Motion (g)')
        .build();
   
    let lowerPlotWidth = Math.floor(upperSubViewOptions.plotWidth * this.options.lowerPlotWidth);

    let margins = upperSubViewOptions.paddingLeft + 
        upperSubViewOptions.paddingRight + upperSubViewOptions.marginLeft +
        upperSubViewOptions.marginRight;

    let upperXWidth = upperSubViewOptions.plotWidth - margins;
    let lowerXWidth = lowerPlotWidth - margins;
  
    /* Calculate lower plot X limit to match upper plot */
    let lowerXMax = ((lowerXWidth * (this.rMax - this.rMin)) / upperXWidth) + this.rMin;

    /* Lower sub view save figure options */
    let lowerSaveOptions = D3SaveFigureOptions.builder()
        .addTitle(false)
        .build();

    /* Lower sub view options: fault plane */
    let lowerSubViewOptions = D3LineSubViewOptions.lowerBuilder()
        .defaultXLimit([ this.options.rMin, lowerXMax ])
        .filename('fault-plane')
        .label('Ground Motion Vs. Distance')
        .lineLabel('Ground Motion Model')
        .marginBottom(20)
        .marginTop(20)
        .paddingTop(30)
        .plotWidth(lowerPlotWidth)
        .saveFigureOptions(lowerSaveOptions)
        .showLegend(false)
        .xAxisLocation('top')
        .xAxisNice(false)
        .xAxisScale('linear')
        .xLabel('Distance (km)')
        .xTickMarks(Math.floor(upperSubViewOptions.xTickMarks / 2))
        .yAxisReverse(true)
        .yAxisScale('linear')
        .yLabel('Median Ground Motion (g)')
        .build();

    let viewOptions = D3LineViewOptions.builder()
        .disableXAxisBtns(true)
        .syncYAxisScale(false)
        .build();

    let view = D3LineView.builder()
        .addLowerSubView(true)
        .containerEl(this.contentEl)
        .upperSubViewOptions(upperSubViewOptions)
        .lowerSubViewOptions(lowerSubViewOptions)
        .viewOptions(viewOptions)
        .build();
 
    view.setTitle('Hanging Wall Effects');

    return view;
  }

  /**
  * @method setSliderValues
  *
  * Set the slider values to match the input fields
  */
  setSliderValues() {
    this.dipSliderEl.value = this.dipEl.value;
    this.widthSliderEl.value = this.widthEl.value;
    this.zTopSliderEl.value = this.zTopEl.value;
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
    let faultInputs = $(this.faultFormEl).serialize();
    let inputs = controlInputs + '&' + faultInputs + 
        '&rMin=' + this.rMin +
        '&rMax=' + this.rMax;
    let dynamic = this.config.server.dynamic;
    let url = dynamic + this.webServiceUrl + '?' + inputs;
    window.location.hash = inputs;
    
    return url; 
  }
 
  /**
  * Call the ground motion web service and plot the results 
  */
  updatePlot() {
    let url = this.serializeGmmUrl(); 
    // Call ground motion hw-fw web service 
    let jsonCall = Tools.getJSON(url);
    this.spinner.on(jsonCall.reject, 'Calculating');

    jsonCall.promise.then((response) => {
      this.spinner.off();
      this.footer.setMetadata(response.server);

      let selectedImt = $(':selected', this.imtEl);
      let selectedImtDisplay = selectedImt.text();
      this.gmmView.setTitle(`Hanging Wall Effects: ${selectedImtDisplay}`);

      this.plotGMM(response);

      $(this.footer.rawBtnEl).off() 
      $(this.footer.rawBtnEl).click((event) => {
        window.open(url);
      });
    }).catch((errorMessage) => {
      this.spinner.off();
      NshmpError.throwError(errorMessage);
    });
  }

}
