'use strict';

import D3View from './D3View.js';
import D3SaveFigure from './D3SaveFigure.js';
import D3Tooltip from './D3Tooltip.js';
import Tools from './Tools.js';
import NshmpError from '../error/NshmpError.js';

/**
* @class D3GeoDeagg
* @extends D3View
*
* @fileoverview This class plots geographic deaggregation source contribution
*     results.
* This class uses the D3 satellite projection for the globe and plots 
*     the deagg contribution as bars.
* All properties must be set before plotting, for example:
*     let myPlot = new D3GeoDeagg(...).withPlotHeader().withPlotFooter();
*     myPlot.setPanelTitle(' ')
*         .setSiteLocation({latitude: lat, longitude: lon})
*         .setUpperData(data)
*         .setUpperDataTableTitle(' ')
*         .setUpperPlotFilename(' ')
*         .setUpperPlotIds(ids)
*         .setUpperPlotLabels(labels)
*         .setUpperMetadata(metadata)
*         .plotData(myPlot.upperPanel); 
* 
* @author bclayton@usgs.gov (Brandon Clayton)
*/
export default class D3GeoDeagg extends D3View {

  /**
  * @param {!HTMLElement} containerEl - DOM element to append to
  * @param {LineViewOptions=} options - General options for plot panel
  * @param {PlotOptions=} plotOptionsUpper - Upper plot options
  * @param {PlotOptions=} plotOptionsLower - Lower plot options
  */
  constructor(
      containerEl, 
      viewOptions = {}, 
      plotOptionsUpper = {}, 
      plotOptionsLower = {}) {
    let mapViewOptions = {
      addGridLineCheckBtn: false,
      addLegendCheckBtn: false,
      centerXOffset: 5,
      centerYOffsetFunction: (centerY) => { 
        return (centerY + this.options.centerXOffset); 
      },
      deaggFill: '#2196F3',
      deaggNormalizationScale: 150,
      deaggSelectedFill: '#0D47A1',
      deaggStroke: 'black',
      deaggStrokeWidth: 1.5,
      deaggWidth: 10,
      defaultView: 'overview', 
      graticuleFill: 'white',
      graticuleStroke: 'lightgrey',
      graticuleStrokeWidth: 1,
      mapBorderStroke: 'black',
      mapBorderStrokeWidth: 0.5,
      mapCloseUp: {
        distance: 5,
        center: [0, 50],
        tilt: 25,
        scale: 2000,
      }, 
      mapFill: 'white',
      mapOverview: {
        distance: 5,
        center: [0, 0],
        tilt: 0,
        scale: 400,
      }, 
      mapStroke: 'black',
      mapStrokeWidth: 1,
      minMapCenterY: 0,
      minMapRotation: 6,
      minMapScale: 200,
      minMapTilt: 0,
      maxMapCenterY: 50,
      maxMapRotation: 60,
      maxMapScale: 10000, 
      maxMapTilt: 25,
      pointRadius: 4.5,
      siteLocationFill: 'red',
      siteLocationStroke: 'red',
      siteLocationStrokeWidth: 1,
      slowRotation: 6,
    };
   
    let plotOptions = {
      printTitle: false,
      tooltipText: [
          'Name:', 'Longitude (°):', 'Latitude (°):', 'Contribution (%):'
      ],
    };
    viewOptions = $.extend({}, mapViewOptions, viewOptions);
    plotOptionsUpper = $.extend({}, plotOptions, plotOptionsUpper);
    plotOptionsLower = $.extend({}, plotOptions, plotOptionsLower);

    super(containerEl, viewOptions, plotOptionsUpper, plotOptionsLower);
    
    /**
    * @type {Number} - Map rotation scaling factor. Multiply current
    *     map scale to obtain a rotation factor to slow down rotation
    *     of globe when using mouse.  
    */
    this.mapRotationFactor = 
        (this.options.maxMapRotation - this.options.minMapRotation) /
        (this.options.maxMapScale - this.options.minMapScale);

    // Update SVG structure for map plots
    this.updateSvgStructure();
    /** @type {Panel} */
    this.lowerPanel = this.updatePlotPanelObject(this.lowerPanel); 
    /** @type {Panel} */
    this.upperPanel = this.updatePlotPanelObject(this.upperPanel); 
    
    /** @type {String} */
    this.mapBorderUrl = '/nshmp-haz-ws/data/us.json';
    /** @type {String */
    this.mapUrl = '/nshmp-haz-ws/data/americas.json';
  
    let defaultView = this.options.defaultView == 'closeUp' ? 
        this.options.mapCloseUp : this.options.mapOverview;
    
    /** @type {D3Satellite} - D3 satellite (tilted perspective) projection */
    this.projection = d3.geoSatellite()
        .center(defaultView.center)
        .distance(defaultView.distance)
        .tilt(defaultView.tilt)
        .scale(defaultView.scale)
        .clipAngle(Math.acos( 1 / defaultView.distance) * 
            180 / Math.PI);
    
    /** @type {D3GeoPath} - D3 geographic path generator */
    this.path = d3.geoPath()
        .projection(this.projection);
    
    /** @type {D3Graticule} - D3 graticule generator */
    this.graticule = d3.geoGraticule();
  }

  /**
  * @method checkMapView
  *
  * Check if current projection parameters match that of the 
  *     overview or close up projection parameters and update buttons. 
  * @param {Panel} panel - Panel with plot map.
  * @param {Array<Number, Number, Number>} rotate - Original rotation
  *     array for projection: [lambda, phi, gamma].
  */
  checkMapView(panel, originalRotate) {
    let overview = this.options.mapOverview;
    let closeUp = this.options.mapCloseUp;
    let distance = this.projection.distance();
    let clipAngle = this.projection.clipAngle();
    let scale = this.projection.scale();
    let center = this.projection.center();
    let rotate = this.projection.rotate();
    let tilt = this.projection.tilt();
    let offsetX = this.options.centerXOffset;
    let offsetY = this.options.centerYOffsetFunction(center[1]);
    let originalClipOverview = (Math.acos( 1 / overview.distance) * 
        180 / Math.PI);
    let originalClipCloseUp = (Math.acos( 1 / closeUp.distance) 
        * 180 / Math.PI);
    
    let originalλ = Number((originalRotate[0] - offsetX).toFixed(5));
    let originalφ = Number((originalRotate[1] + offsetY).toFixed(5));
    let originalɣ = originalRotate[2];
    let λ = Number(rotate[0].toFixed(5));
    let φ = Number(rotate[1].toFixed(5));
    let ɣ = rotate[2];
    let rotateCheck = originalλ == λ && originalφ == φ && originalɣ == ɣ;
    
    if (distance == overview.distance &&
        center[0] == overview.center[0] &&
        center[1] == overview.center[1] &&
        scale == overview.scale &&
        clipAngle == originalClipOverview && 
        rotateCheck) {
      d3.select(this.plotFooterEl)
          .select('.close-up-btn')
          .classed('active', false)
      
      d3.select(this.plotFooterEl)
          .select('.overview-btn')
          .classed('active', true)
    } else if (distance == closeUp.distance &&
        center[0] == closeUp.center[0] &&
        center[1] == closeUp.center[1] &&
        scale == closeUp.scale &&
        clipAngle == originalClipCloseUp && 
        rotateCheck) { 
      d3.select(this.plotFooterEl)
          .select('.close-up-btn')
          .classed('active', true)
      
      d3.select(this.plotFooterEl)
          .select('.overview-btn')
          .classed('active', false)
    } else {
      d3.select(this.plotFooterEl)
          .select('.close-up-btn')
          .classed('active', false)
      
      d3.select(this.plotFooterEl)
          .select('.overview-btn')
          .classed('active', false)
    }
  
  }

  /**
   * Clear the deagg data
   * @param {PlotPanel} panel 
   */
  clearData(panel) {
    console.log(panel.mapEl);
    d3.select(panel.deaggEl)
        .selectAll('rect')
        .remove();
    
    d3.select(panel.siteEl)
        .selectAll('circle')
        .remove();
  }

  /**
  * @method createDeaggTooltip
  *
  * Create a tooltip for the deagg contribution.
  * @param {Panel} - Panel with map plot.
  */
  createDeaggTooltip(panel) {
    let tooltip;
    let tooltipOptions = {
      offsetX: this.options.deaggWidth,
      offsetY: panel.options.tooltipOffsetY, 
      padding: panel.options.tooltipPadding,
      selectionIncrement: panel.options.selectionIncrement,
    };
    
    d3.select(panel.deaggEl)
    .selectAll('rect').on('mouseenter', (d, i, els) => {
      let id = els[i].id;
      let label = this.idToLabel(panel, id);
      let lon = d[0];
      let lat = d[1];
      let deagg = d[2];
      let maxCont = this.maxDeaggContribution(panel.data);
      let mouse = d3.mouse(els[i]);
      let cx = mouse[0]; 
      let cy = mouse[1]; 
      cy = cy - deagg / maxCont * this.options.deaggNormalizationScale;
      let tooltipText = [
        label,
        panel.options.tooltipText[1] + ' ' + lon,
        panel.options.tooltipText[2] + ' ' + lat,
        panel.options.tooltipText[3] + ' ' + deagg,
      ];
      
      tooltip = new D3Tooltip.Builder()
          .coordinates(cx, cy)
          .dataEl(els[i])
          .options(tooltipOptions)
          .plotHeight(panel.svgHeight)
          .plotMarginLeft(panel.options.marginLeft)
          .plotMarginTop(panel.options.marginTop)
          .plotWidth(panel.svgWidth)
          .tooltipText(tooltipText)
          .tooltipEl(panel.tooltipEl)
          .build()
          .changeAttribute('fill', this.options.deaggSelectedFill);
    
    })
    .on('mouseleave', (event) => {
      tooltip.changeAttribute('fill', this.options.deaggFill)
          .destroy();
      
    });
  
  }

  /**
  * @method createSiteLocationTooltip
  *
  * Create the tooltip for the site location.
  * @param {Panel} - Panel with map plot.
  */
  createSiteLocationTooltip(panel) {
    let tooltip;
    let siteTooltipOptions = {
      fontSize: panel.options.tooltipFontSize,
      offsetX: panel.options.tooltipOffsetX,
      offsetY: panel.options.tooltipOffsetY,
      padding: panel.options.tooltipPadding,
      selectionIncrement: panel.options.selectionIncrement,
    };

    d3.select(panel.siteEl)
      .selectAll('circle')
      .on('mouseenter', (d, i, els) => {
          let cx = d3.select(els[i]).attr('cx');
          let cy = d3.select(els[i]).attr('cy');
          let lon = d[0];
          let lat = d[1];
          let tooltipText = [
            'Selected Site',
            'Longitude: ' + lon,
            'Latitude: ' + lat,
          ];
          
          tooltip = new D3Tooltip.Builder()
              .coordinates(cx, cy)
              .dataEl(event.target)
              .options(siteTooltipOptions)
              .plotHeight(panel.svgHeight)
              .plotWidth(panel.svgWidth)
              .tooltipText(tooltipText)
              .tooltipEl(panel.tooltipEl)
              .build()
              .changeSizeAttribute('r', true /* To increase */);
        })
        .on('mouseleave', (event) => {
          tooltip.changeSizeAttribute('r', false /* To increase */)
              .destroy();
        });
  }

  /**
  * @method maxDeaggContribution
  *
  * Find the maximum deagg contribution value.
  * @param {Array<Array<Number, Number, Number>>} data - Deagg data series:
  *     [ [lon_0, lat_0, z_0], [lon_n, lat_n, z_n] ]
  * @return {Number} Max deagg contribution in data series.
  */
  maxDeaggContribution(data) {
    return d3.max(data, (dataSeries, i) => {
      return d3.max(dataSeries, (dataArray) => {
        return dataArray[2];
      })
    });
  }
  
  /**
  * @method onMapAltRotation
  *
  * When mouse is down and meta or alt key is pressed, rotate the map 
  *     in gamma value on left and right mouse move and change distance,
  *     scale, clipAngle on mouse up and down.
  * @param {Panel} panel - Panel with plot map.
  * @param {Array<Number, Number, Number>} rotate - Original rotation
  *     array for projection: [lambda, phi, gamma].
  */
  onMapAltRotation(panel, originalRotate) {
    let mapCenterYFactor = 
        (this.options.maxMapCenterY - this.options.minMapCenterY) / 
        (this.options.maxMapTilt -  this.options.minMapTilt);

    $(panel.svgEl).off('mousemove');
    $(panel.svgEl).on('mousemove', (event) => {
      this.checkMapView(panel, originalRotate);
      let xMove = event.originalEvent.movementX;
      let yMove = event.originalEvent.movementY;
      let tilt = this.projection.tilt();
      tilt -= yMove;
      if (tilt < this.options.minMapTilt || 
            tilt > this.options.maxMapTilt) return;
      let currentRotate = this.projection.rotate();
      let scale = this.projection.scale();
      let slowRotation = scale * this.mapRotationFactor;
      let λ = currentRotate[0];
      let φ = currentRotate[1];
      φ -= yMove * 2; 
      let ɣ = currentRotate[2];
      ɣ += xMove / slowRotation;
       
      let newRotate = [λ, φ, ɣ];  
      let centerY = tilt * mapCenterYFactor;
      let center = [0, centerY];
      // Update projection
      this.projection.rotate(newRotate)
        .center(center)
        .tilt(tilt);
      // Update map
      this.updateMap(panel);
    });
  }
  
  /**
  * @method onMapMouseDown
  *
  * Listen for mouse down on map and call either onMapAltRotation 
  *     or onMapRotation depending if the meta or alt key is down.
  * @param {Panel} panel - Panel with plot map.
  * @param {Array<Number, Number, Number>} rotate - Original rotation
  *     array for projection: [lambda, phi, gamma].
  */
  onMapMouseDown(panel, originalRotate) {
    let leftClickKeyCode = 1;
    $(panel.svgEl).on('mousedown', (event) => {
      if (event.which != leftClickKeyCode) return;
      if (event.metaKey || event.altKey) {
        this.onMapAltRotation(panel, originalRotate);
      } else {
        this.onMapRotation(panel, originalRotate);
      }
    });
  }

  /**
  * @method onMapMouseUp
  *
  * When mouse is released, turn off mouse move listener.
  * @param {Panel} panel - Panel with map plot.
  */
  onMapMouseUp(panel) {
    $(panel.svgEl).on('mouseup', () => {
      $(panel.svgEl).off('mousemove');
    });
  }

  /**
  * @method onMapRotation
  *
  * When mouse is down, rotate the map in lambda (mouse left and right) and
  *     phi (mouse up and down). 
  * @param {Panel} panel - Panel with plot map.
  * @param {Array<Number, Number, Number>} rotate - Original rotation
  *     array for projection: [lambda, phi, gamma].
  */
  onMapRotation(panel, originalRotate) {
    let mouseNew;
    let mouseOld;
    $(panel.svgEl).off('mousemove');
    $(panel.svgEl).on('mousemove', (event) => {
      this.checkMapView(panel, originalRotate);
      mouseOld = mouseNew;
      mouseNew = [event.offsetX, event.offsetY];
      if (mouseOld == undefined) return;
      let scale = this.projection.scale();
      let slowRotation = scale * this.mapRotationFactor;
      let diffX = (mouseNew[0] - mouseOld[0]) / slowRotation; 
      let diffY = -(mouseNew[1] - mouseOld[1]) / slowRotation;
      let currentRotate = this.projection.rotate();
      let λ = currentRotate[0];
      let φ = currentRotate[1];
      let ɣ = currentRotate[2];
      let cosɣ = Math.cos(ɣ * Math.PI / 180.0 ); 
      let sinɣ = Math.sin(ɣ * Math.PI / 180.0 ); 
      λ += (diffX * cosɣ) + (diffY * sinɣ); 
      φ += -(diffX * sinɣ) + (diffY * cosɣ);
      let newRotate = [λ, φ, ɣ];   
      // Update projection
      this.projection.rotate(newRotate);
      // Update map
      this.updateMap(panel);
    });
  }
  
  /**
  * @method onMapViewChange
  *
  * On close up and overview button click, change the map view 
  *     accordingly.
  * @param {Panel} panel - Panel with map plot.
  * @param {Array<Number, Number, Number>} rotate - Original rotation
  *     array for projection: [lambda, phi, gamma].
  */
  onMapViewChange(panel, rotate) {
    $(this.plotFooterEl).find('.view-btns').on('click', (event) => {
      let distance;
      let tilt;
      let clipAngle;
      let scale;
      let center;
      let newRotate;
      let inputEl = $(event.target).find('input')[0];
      let value = inputEl.value; 
      let id = inputEl.id;
      let closeUp = this.options.mapCloseUp;
      let overview = this.options.mapOverview;
      let offsetX = this.options.centerXOffset;

      if (value == 'closeUp') {
        // Close up 
        distance = closeUp.distance;
        tilt = closeUp.tilt;
        center = closeUp.center; 
        clipAngle = Math.acos(1 / distance) * 180.0 / Math.PI;
        scale = closeUp.scale;
      } else {
        // Overview
        distance = overview.distance;
        tilt = overview.tilt;
        clipAngle = Math.acos(1 / distance) * 180.0 / Math.PI;
        center = overview.center;
        scale = overview.scale; 
      }
      let offsetY = this.options.centerYOffsetFunction(center[1]);
      newRotate = [rotate[0] - offsetX, rotate[1] + offsetY, rotate[2]];
      // Update projection 
      this.projection.distance(distance)
          .clipAngle(clipAngle)
          .rotate(newRotate)
          .center(center)
          .scale(scale)
          .tilt(tilt);
      // Update map
      this.updateMap(panel);
    });
  }

  /**
  * @method onMapZoom
  *
  * Listen for wheel in and out and scale the plot accordingly.
  * @param {Panel} panel - Panel with map plot.
  * @param {Array<Number, Number, Number>} rotate - Original rotation
  *     array for projection: [lambda, phi, gamma].
  */
  onMapZoom(panel, originalRotate) {
    $(panel.svgEl).on('wheel', (event) => {
      this.checkMapView(panel, originalRotate);
      let direction = event.originalEvent.deltaY;
      let currentScale = this.projection.scale();
      let scale = direction > 0 /* Up */ ? currentScale * 1.025 :
          currentScale * 0.975;
      if (scale < this.options.minMapScale || 
            scale > this.options.maxMapScale) return;
      // Update projection
      this.projection.scale(scale);
      // Update map
      this.updateMap(panel);
    });
  }
  
  /**
  * @method plotAmericas
  *
  * Plot North and South america borders.
  * @param {Panel} panel - Panel to plot maps.
  * @param {Array<Object>} americas - North and South America map. Array
  *     of GeoJson features.
  */
  plotAmericas(panel, americas) {
    d3.select(panel.mapEl)
        .selectAll('path')
        .remove();
    
    d3.select(panel.mapEl)
        .selectAll('path')
        .data(americas)
        .enter()
        .append('path')
        .attr('class', 'map')
        .attr('d', this.path)
        .attr('fill', this.options.mapFill)
        .attr('stroke', this.options.mapStroke)
        .attr('stroke-width', this.options.mapStrokeWidth);
  }

  /**
  * @method plotData
  *
  * Get maps and plot deagg contribution
  * @param {Panel} panel - Panel with map plot.
  * @param {Array<Number, Number, Number>=} rotate - Optional rotation
  *     array for projection: [lambda, phi, gamma].
  */
  plotData(panel, rotate = [0, 0, 0]) {
    let jsonCall = Tools.getJSONs([this.mapUrl, this.mapBorderUrl]);

    Promise.all(jsonCall.promises).then((maps) => {
      let map = maps[0];
      let mapBorders = maps[1];

      let usBorders = topojson.feature(
          mapBorders, 
          mapBorders.objects.states)
          .features; 

      let americas = map.features; 
      this.plotMapAndBorders(panel, rotate, americas, usBorders);
    }).catch((errorMessage) => {
      NshmpError.throwError(errorMessage);
    });
  }

  /**
  * @method plotDeaggContribution
  *
  * Plot deagg contribution on map.
  * @param {Panel} panel - Panel with map plot.
  */
  plotDeaggContribution(panel) {
    d3.select(panel.deaggEl)
        .selectAll('rect')
        .remove();
   
    let maxContribution = this.maxDeaggContribution(panel.data);

    d3.select(panel.deaggEl)
        .selectAll('rect')
        .data(() => { return panel.data.map((d, i) => { return d[0] })})
        .enter()
        .append('rect')
        .attr('class', 'deagg-contribution')
        .attr('id', (d, i) => { return panel.ids[i]; })
        .attr('x', (d, i) => { return this.projection(d.slice(0,3))[0]; })
        .attr('y', (d, i) => { return this.projection(d.slice(0,3))[1]; })
        .attr('height', (d, i) => { 
          return d[2] / maxContribution * 
              this.options.deaggNormalizationScale; 
        })
        .attr('transform', (d, i) => {
          return 'translate(0, ' + (-d[2] / maxContribution * 
              this.options.deaggNormalizationScale) + ')';
        })
        .attr('width', this.options.deaggWidth)
        .attr('fill', this.options.deaggFill)
        .attr('stroke', this.options.deaggStroke)
        .attr('stroke-width', this.options.deaggStrokeWidth);
  }

  /**
  * @method plotGraticule
  *
  * Plot the graticule.
  * @param {Panel} panel - Panel with map plot.
  */
  plotGraticule(panel) {
    d3.select(panel.graticuleEl)
        .selectAll('path')
        .remove();
    
    d3.select(panel.graticuleEl)
        .append('path')
        .attr('class', 'graticule')
        .datum(this.graticule)
        .attr('d', this.path)
        .attr('fill', this.options.graticuleFill)
        .attr('stroke', this.options.graticuleStroke)
        .attr('stroke-width', this.options.graticuleStokeWidth);
  }

  /**
  * @method plotMapAndBorders
  *
  * Plot North America, US state borders, deagg contribution, 
  *     graticule, and setup listeners for map interactions.
  * @param {Panel} panel - Panel with map plot.
  * @param {Array<Number, Number, Number>} rotate - Original rotation
  *     array for projection: [lambda, phi, gamma].
  * @param {Array<Object>} americas - North and South America map. Array
  *     of GeoJson features.
  * @param {Array<Object>} usBorders - US state borders. Array 
  *     of GeoJson features.
  */
  plotMapAndBorders(panel, rotate, americas, usBorders) {
    d3.select(this.el)
        .classed('hidden', false);
    
    d3.select(this.tableEl)
        .classed('hidden', true);

    d3.select(this.metadataTableEl)
        .classed('hidden', true);
    
    d3.select(this.plotBodyEl)
        .classed('hidden', false);

    d3.select(panel.plotBodyEl)
        .classed('hidden', false);

    d3.select(this.plotFooterEl)
        .selectAll('label')
        .classed('active', false)
        .classed('focus', false);
    
    d3.select(this.plotFooterEl)
        .select('.plot-btn')
        .classed('active', true);
    
    // Create the data table
    this.createDataTable(panel);

    // Update projection 
    let tilt = this.projection.tilt();
    let distance = this.projection.distance();
    let scale = this.projection.scale();
    let center = this.projection.center();
    let offsetX = this.options.centerXOffset;
    let offsetY = this.options.centerYOffsetFunction(center[1]); 
    let newRotate = [
      rotate[0] - offsetX,
      rotate[1] + offsetY,
      rotate[2]
    ];  
    this.projection.translate([panel.svgWidth / 2, panel.svgHeight / 2])
        .rotate(newRotate);
    this.checkMapView(panel, rotate);
    
    //Plot graticule
    this.plotGraticule(panel);
    // Plot map
    this.plotAmericas(panel, americas);
    // Plot map borders
    this.plotStateBorders(panel, usBorders);
    //Plot site
    this.plotSiteLocation(panel);
    //Plot contribution
    this.plotDeaggContribution(panel); 
     
    // Listeners 
    this.onMapMouseDown(panel, rotate);
    this.onMapMouseUp(panel);
    this.onMapZoom(panel, rotate);
    this.onMapViewChange(panel, rotate); 
    
    //Tooltips
    this.createSiteLocationTooltip(panel);
    this.createDeaggTooltip(panel);
  }

  /**
  * @method plotSiteLocation
  *
  * Plot the site location choosen.
  * @param {Panel} panel - Panel with map plot.
  */
  plotSiteLocation(panel) {
    d3.select(panel.siteEl)
        .selectAll('circle')
        .remove();
    
    d3.select(panel.siteEl)
        .selectAll('circle')
        .data([this.siteLocation])
        .enter()
        .append('circle')
        .attr('class', 'site-location')
        .attr('cx', (d, i) => { return this.projection(d)[0]; })
        .attr('cy', (d, i) => { return this.projection(d)[1]; })
        .attr('r', this.options.pointRadius)
        .attr('fill', this.options.siteLocationFill)
        .attr('stroke', this.options.siteLocationStroke)
        .attr('stroke-width', this.options.siteLocationStrokeWidth)
        .style('cursor', 'pointer');
  }

  /**
  * @method plotStateBorders
  *
  * Plot the US state borders
  * @param {Panel} panel - Panel with map plot.
  * @param {Array<Object>} usBorders - US state borders. Array 
  *     of GeoJson features.
  */
  plotStateBorders(panel, usBorders) {
    d3.select(panel.mapBorderEl)
        .selectAll('path')
        .remove();
    
    d3.select(panel.mapBorderEl)
        .selectAll('path')
        .data(usBorders)
        .enter()
        .append('path')
        .attr('class', 'borders')
        .attr('d', this.path)
        .attr('fill', 'none')
        .attr('stroke', this.options.mapBorderStroke)
        .attr('stroke-width', this.options.mapBorderStrokeWidth); 
  }

   /**
   * Save the figure
   * @param {PlotPanel} panel Plot panel to save
   * @param {D3SaveFigureOptions} saveOptions The save options 
   * @param {String} plotFormat The plot format to save
   * @param {Boolean} previewFigure Whether to preview figure 
   */
  saveFigure(panel, saveOptions, plotFormat, previewFigure) {
    let svgCloneD3 = d3.select(panel.svgEl.cloneNode(true));
    
    let builder = D3SaveFigure.builder()
        .currentSvgHeight(panel.svgHeight)
        .currentSvgWidth(panel.svgWidth)
        .filename(panel.plotFilename)
        .options(saveOptions)
        .metadata(this.metadata) 
        .plotFormat(plotFormat)
        .plotTitle(this.plotTitleEl.textContent)
        .svgEl(svgCloneD3.node());

      if(previewFigure) builder.previewFigure();
      builder.build();
  }

  /**
  * @method updatePlotPanelObject
  *
  * Add properties to the panel object.
  * @return {Panel} - Updated panel.
  */
  updatePlotPanelObject(panelObject) {
    let panelEl = panelObject.plotBodyEl;
    let panelUpdate = {
      deaggEl: panelEl.querySelector('.deagg-contribution-group'),
      mapEl: panelEl.querySelector('.map-group'),
      mapBorderEl: panelEl.querySelector('.map-border-group'),
      graticuleEl: panelEl.querySelector('.graticule-group'),
      siteEl: panelEl.querySelector('.site-location-group'),
    };

    return $.extend({}, panelObject, panelUpdate);
  }

  /**
  * @method updateMap
  *
  * Update graticule, North and South america, US state borders,
  *     and deagg contribution.
  * @param {Panel} panel - Panel with map plots. 
  */
  updateMap(panel) {
    // Update site location
    d3.select(panel.siteEl)
        .selectAll('circle')
        .attr('cx', (d, i) => { return this.projection(d)[0]; })
        .attr('cy', (d, i) => { return this.projection(d)[1]; })

    // Update Graticule
    d3.select(panel.graticuleEl)
        .select('path')
        .datum(this.graticule)
        .attr('d', this.path);

    // Update Americas map
    d3.select(panel.mapEl)
        .selectAll('path')
        .attr('d', this.path);
    
    // Update state borders                                                         
    d3.select(panel.mapBorderEl)
        .selectAll('path')
        .attr('d', this.path);
    
    // Update deagg contribution
    d3.select(panel.deaggEl)
        .selectAll('rect')
        .attr('x', (d, i) => { return this.projection(d.slice(0,3))[0]; })
        .attr('y', (d, i) => { return this.projection(d.slice(0,3))[1]; });
  }

  /**
  * @method updateSvgStructure
  *
  * Update the SVG structure for geo deagg specific.
  */
  updateSvgStructure() {
    let svgD3 = d3.select(this.el)
        .select('.panel-outer')
        .selectAll('svg')
        .attr('class', 'D3GeoDeagg')
        .style('user-select', 'none')
        .style('-webkit-user-select', 'none')
        .selectAll('.plot');
    
    svgD3.append('g').attr('class', 'graticule-group');
    svgD3.append('g').attr('class', 'map-group');
    svgD3.append('g').attr('class', 'map-border-group');
    svgD3.append('g').attr('class', 'site-location-group');
    svgD3.append('g').attr('class', 'deagg-contribution-group');
  }

  /**
  * @override 
  * @method withPlotFooter
  *
  * Creates the footer in the plot panel a plot/data button.
  *     This method is chainable.
  * @return {D3GeoDeagg} - Return the class instance to be chainable
  */
  withPlotFooter() {
    let buttons = [
      {
        class: 'view-btns',
        col: 'col-xs-5',
        btns: [
          {
            name: 'overview',
            value: 'over',
            text: 'Overview',
            class: 'overview-btn',
          }, {
            name: 'close-up',
            value: 'closeUp',
            text: 'Close Up',
            class: 'close-up-btn',
          }
        ]
      },{
        class: 'plot-data-btns',
        col: 'col-xs-7',
        btns: [
          {
            name: 'plot',
            value: 'plot',
            text: 'Plot',
            class: 'plot-btn',
          }, {
            name: 'data',
            value: 'data',
            text: 'Data',
            class: 'data-btn',
          }, {
            name: 'metadata',
            value: 'metadata',
            text: 'Metadata',
            class: 'metadata-btn',
          }
        ]
      }
    ];
    
    this.createPanelFooter(buttons); 
    this.onPlotDataViewSwitch();
    
    return this;
  }

}
