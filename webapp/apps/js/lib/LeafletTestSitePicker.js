'use strict';

import TestSiteView from './TestSiteView.js';
import Tools from './Tools.js';
import NshmpError from '../error/NshmpError.js';

/**
* @class LeafletTestSitePicker
* @extends TestSiteView
*
* @fileoverview Location widget to choose a test site.
*
* @author Brandon Clayton
*/
export default class LeafletTestSitePciker extends TestSiteView {

  /**
  * @param {HTMLElement} latEl - The HTML element associated with the
  *     latitude input.
  * @param {HTMLElement} lonEl - The HTML element associated with the 
  *     longitude input.
  * @param {HTMLElement} activationBtnEl - The HTML element associated with 
  *     a button that onced pressed would show the location widget.
  */
  constructor(latEl, lonEl, activationBtnEl) {
    super(latEl, lonEl, activationBtnEl);
   
    /** @type {Object} */
    this.options = { 
      plotHeight: 1280,
      plotWidth: 1024,
      map: {
        minZoom: 1,
        maxZoom: 18,
        zoomSnap: 0.5,
      },
      site: {
        radius: 4,
        color: 'black',
        opactiy: 1,
        fillOpacity: 1,
        className: '',
      },
      siteSelected: {
        radius: 4,
        color: 'red',
        opactiy: 1,
        fillOpacity: 1,
        className: 'selected',
      },
      fitBounds: {
        animate: false,
      },
    };
  
    /** @type {HTMLElement} */ 
    this.leafletEl = this.mapBodyEl;
    /** @type {LeafletMap} */
    this.leafletMap = this.createBaseMap();
    /** @type {LeafletGeoJsonLayer} */
    this.geoJsonLayer = this.createGeoJson();
    
    /* Get test sites */ 
    this.getUsage();
   
    /** @type {EventListner} */
    this.on = document.addEventListener.bind(document); 

    /** 
    * @type {CustomEvent} New custom event to be called
    *     as this.on('testSiteLoad', () => {}). Gets triggered
    *     after the test sites have been loaded.
    */   
    this.onTestSiteLoadEvent = new CustomEvent(
        'testSiteLoad', 
        {bubbles: true, cancelable: true});
  }
  
  /**
  * @method checkForRegion
  *
  * Check if a region exists in the test sites and disabled button if
  *     there is no region.
  */
  checkForRegion(regionId) {
    let region = this.testSites.find((feature) => {
      return feature.properties.regionId == regionId;
    });

    if (region == undefined) {
      d3.select(this.activationBtnEl)
          .property('disabled', true);
    } else {
      d3.select(this.activationBtnEl)
          .property('disabled', false);
    }
  }
  
  /**
  * @method createBaseMap
  *
  * Create the Leaflet base map with greyscale, street, and satellite layers.
  * @return {LeafletMap} The leaflet map.
  */
  createBaseMap() {
    let mapUrl = 'https://api.tiles.mapbox.com/v4/{id}/{z}/{x}/{y}@2x.png' +
        '?access_token={accessToken}';
    
    let mapToken = 'pk.eyJ1IjoiYmNsYXl0b24iLCJhIjoiY2pmbWxmd3BhMHg1eTJ' + 
        '4bzR3M2Q3ZTM5YyJ9.lCQ8B6C9xhpZmd8BrukbDw';
    
    /* Greyscale layer */
    let greyscale = L.tileLayer(mapUrl, {
      accessToken: mapToken,
      id: 'mapbox.light',
    });
    
    /* Street layer */
    let street = L.tileLayer(mapUrl, {
      accessToken: mapToken,
      id: 'mapbox.streets',
    });
    
    /* Satellite layer */
    let satellite = L.tileLayer(mapUrl, {
      accessToken: mapToken,
      id: 'mapbox.satellite',
    });
   
    /* Topography layer */
    let topography = L.tileLayer(mapUrl, {
      accessToken: mapToken,
      id: 'mapbox.outdoors',
    });

    /* Pirate layer */
    let pirate = L.tileLayer(mapUrl, {
      accessToken: mapToken,
      id: 'mapbox.pirates',
    });
    
    /* Create the map */
    let leafletMap = L.map(this.leafletEl, {
      center: [0, 0],
      zoom: 0,
      layers: [greyscale],
      minZoom: this.options.map.minZoom,
      maxZoom: this.options.map.maxZoom,
      zoomSnap: this.options.map.zoomSnap,
    });
    
    /* Base maps */
    let baseMaps = {
      'Greyscale': greyscale,
      'Street': street,
      'Satellite': satellite,
      'Topography': topography,
      'Pirate': pirate,
    };
    
    /* Add layers to Leaflet map */
    L.control.layers(baseMaps, null).addTo(leafletMap);
    
    return leafletMap;
  }

  /**
  * @method createGeoJson
  * 
  * Create an empty Leaflet GeoJson layer to add test sites to.
  * Test sites are rendered in SVG.
  * @return {LeafletGeoJsonLayer} The GeoJson layer.
  */ 
  createGeoJson() {
    let geoJsonLayer = L.geoJSON(null, {
      /* 
      * Render test site in SVG. 
      * See if lat and lon values match a 
      *     test site and use selected site options if so.
      */
      pointToLayer: (feature, latlng) => {
        let coords = this.getCoordinates(feature);
        latlng.lng = coords[0];
        latlng.lat = coords[1];
        let options = this.options.site;
                
        if (this.lonEl.value == coords[0] && 
            this.latEl.value == coords[1]) {
          d3.select(this.useLocationBtnEl)
              .property('disabled', false);
          
          d3.select(this.siteListEl)
              .select('#' + feature.id)
              .classed('active', true)
              .node()
              .scrollIntoView();
          
          options = this.options.siteSelected;
        } 

        return L.circleMarker(latlng, options);
      },
      /* Create tooltips for each location. */
      onEachFeature: (feature, layer) => {
        let coords = this.getCoordinates(feature);
        let tooltip = '<b> ' + feature.properties.title + '</b><br>' +
            'Longitude: ' + coords[0] + '<br>' +
            'Latitude: ' + coords[1];
        
        layer.bindTooltip(tooltip);     
      },
    });
    
    geoJsonLayer.addTo(this.leafletMap);
    
    return geoJsonLayer;
  }

  /**
  * @method findTestSite
  *
  * Search all test sites in a particular region 
  *     to see if the current lat and lon values match a test site.
  * @return {Object || Undefined} The test site. 
  */ 
  findTestSite(regionId) {
    let region = this.getRegion(regionId);

    let site = region.find((site) => {
      let coords = this.getCoordinates(site);
      return this.lonEl.value == coords[0] &&
          this.latEl.value == coords[1];
    });
    
    return site != undefined ? site : undefined; 
  }

  /**
  * @method getSelectedSiteFromClassName
  *
  * Find a selected test site based on the class name 
  *     attribute.
  */
  getSelectedSiteFromClassName() {
    let layers = this.geoJsonLayer.getLayers();
    let selectedLayer = layers.find((layer) => {
      return layer.options.className == 
          this.options.siteSelected.className;
    });
    
    return selectedLayer.feature; 
  }
  
  /**
   * Find a selected site based on the location id.
   * 
   * @param {String} locationId The location id 
   */
  getSelectedSiteFromId(locationId) {
    let layers = this.geoJsonLayer.getLayers();
    let selectedLayer = layers.find((layer) => {
      return layer.feature.id == locationId;
    });
 
    return selectedLayer; 
  }

  /**
  * @method getTestSiteTitle 
  *
  * Find a test site that matched current lat and lon values
  *     and return a string as: 'Location (lon, lat)'
  * If the lat and lon values do not match a test site then 
  *     return just lat and lon value as string.
  * @return {String} The location string.    
  */
  getTestSiteTitle(regionId) {
    let testSite = this.findTestSite(regionId);
    let loc = testSite != undefined ? testSite.properties.title : '';

    return loc + ' (' + this.lonEl.value + ', ' + this.latEl.value + ')';
  }

  /**
  * @method getUsage
  *
  * Get the test sites from /nshmp-haz-ws/util/testsites.
  * Update the activationBtnEl to be clickable.
  * Trigger the custom event on('testSiteLoad').
  */
  getUsage() {
    let jsonCall = Tools.getJSON(this.webServiceUrl);

    jsonCall.promise.then((usage) => {
      NshmpError.checkResponse(usage);
      this.testSites = usage.features;
      document.dispatchEvent(this.onTestSiteLoadEvent);
      d3.select(this.activationBtnEl)
          .property('disabled', false);
    }).catch((errorMessage) => {
      NshmpError.throwError(errorMessage);
    });
  }
  
  /**
  * @method onSiteListSelect
  *
  * Listen for a mouseover, mouseout, and mousedown on the 
  *     site list.
  * Mousedown: Update the corresponding site in the map with 
  *     this.options.selectedSite
  * Mouseover: Show the tooltip on the corresponding site in the map.
  * Mouseout: Remove tooltip.
  */
  onSiteListSelect() {
    /* 
    * Update options to options.selectedSite when
    *     site list clicked. 
    */
    $(this.siteListEl).on('mousedown keyup keydown', (event) => {
      let locationId = event.target.value || event.target.id;
      let selectedLayer = this.getSelectedSiteFromId(locationId); 
      this.geoJsonLayer.setStyle(this.options.site);
      selectedLayer.setStyle(this.options.siteSelected);  
      selectedLayer.bringToFront();
      
      d3.select(this.useLocationBtnEl)
          .property('disabled', false);
    });
  
    /* Show tooltip on site in map */
    $(this.siteListEl).on('mouseover', (event) => {
      let locationId = event.target.value || event.target.id;
      let selectedLayer = this.getSelectedSiteFromId(locationId); 
      
      selectedLayer.bringToFront()
          .openTooltip();
    });
    
    /* Remove tooltip */
    $(this.siteListEl).on('mouseout', (event) => {
      let locationId = event.target.value || event.target.id;
      let selectedLayer = this.getSelectedSiteFromId(locationId); 
      selectedLayer.closeTooltip();
    });
  }

  /**
  * @method onSiteSelect
  * 
  * Listen for a test site to be clicked and update the options
  *     using this.options.siteSelected and select the
  *     site in the site list and scroll it into view.
  */
  onSiteSelect() {
    this.geoJsonLayer.on('click', (event) => {
      this.geoJsonLayer.setStyle(this.options.site);
      let layer = event.layer;
      layer.setStyle(this.options.siteSelected);
      
      d3.select(this.siteListEl)
          .selectAll('label')
          .classed('active', false);

      d3.select(this.siteListEl)
          .select('#' + layer.feature.id)
          .classed('active', true)
          .node()
          .scrollIntoView();

      d3.select(this.useLocationBtnEl)
          .property('disabled', false);
    });
  }
  
  /**
  * @method onSnapGrid
  * 
  * Place holder method for when the snap to grid is check.
  */
  onSnapGrid() {
    $(this.snapGridEl).off();
    $(this.snapGridEl).on('click', (event) => {
      this.updateTestSites();
    });
  }

  /**
  * @method onUseLocation
  *
  * Listen for the use location button to be clicked.
  * Update the lat and lon values, trigger input event on the 
  *     lat and lon element, and close the location widget.
  */
  onUseLocation() {
    $(this.useLocationBtnEl).on('click', (event) => {
      let selectedSite = this.getSelectedSiteFromClassName(); 
      let coords = this.getCoordinates(selectedSite);
      
      this.lonEl.value = coords[0];
      this.latEl.value = coords[1];
      
      $(this.lonEl).trigger('input');
      $(this.latEl).trigger('input');
      
      $(this.el).modal('hide');
    });
  }

  /**
  * @method plotMap
  * 
  * Given a region, plot the corresponding test sites.
  * When plotData is called the location widget automatically appears.
  * @param {String} regionId - The region to plot. 
  */
  plotMap(regionId) {
    /* Show location widget */
    this.show();
    
    d3.select(this.useLocationBtnEl)
        .property('disabled', true);

    /* Replot */
    this.leafletMap.invalidateSize();
    
    /* Plot test sites */
    let regionGeoJson = this.getRegion(regionId);
    this.createSiteList(regionGeoJson);
    this.geoJsonLayer.clearLayers(); 
    this.geoJsonLayer.addData(regionGeoJson);
    
    /* Zoom to region bounds */
    this.leafletMap.fitBounds(
        this.geoJsonLayer.getBounds(),
        this.options.fitBounds);
    
    /* Listeners */
    this.onSiteSelect(); 
    this.onSiteListSelect();
    //this.onSnapGrid();
    this.onUseLocation();
     
    /* Replot when container size changes */
    $(window).resize((event) => {
      this.leafletMap.invalidateSize();
      
      this.leafletMap.fitBounds(
          this.geoJsonLayer.getBounds(),
          this.options.fitBounds);
    });
  }

  /**
  * @method updateTestSites
  * 
  * Update the lat and lon of a test site.
  */
  updateTestSites() {
    this.geoJsonLayer.eachLayer((layer) => {
      let coords = this.getCoordinates(layer.feature);
      let latlng = L.GeoJSON.coordsToLatLng(coords);
      layer.setLatLng(latlng);
    });
  }

}
