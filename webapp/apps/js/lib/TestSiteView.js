'use strict';

/**
* @class TestSiteView
*
* @fileoverview Creates a Bootstrap modal overlay to plot test sites.
*
* @author Brandon Clayton
*/
export default class TestSiteView {

  /**
  * @param {HTMLElement} latEl - The HTML element associated with the
  *     latitude input.
  * @param {HTMLElement} lonEl - The HTML element associated with the
  *     longitude input.
  * @param {HTMLElement} activationBtnEl - The HTML element associated with
  *     a button that onced pressed would show the location widget.
  */
  constructor(latEl, lonEl, activationBtnEl) {
    /** @type {String} */
    this.webServiceUrl = '/nshmp-haz-ws/util/testsites';
    
    /** @type {Object} */
    this.viewOptions = {
      mapWidth: '75%',
      siteListWidth: '25%',
    };
    
    /** @type {HTMLElement} */
    this.latEl = latEl;
    /** @type {HTMLElement} */
    this.lonEl = lonEl;
    /** @type {HTMLElement} */
    this.activationBtnEl = activationBtnEl;
    
    /* Set btn to disabled until tes sties are loaded */
    d3.select(this.activationBtnEl)
        .property('disabled', true);

    /** @type {HTMLElement} */
    this.el = this.createOverlay();
    /** @type {HTMLElement} */
    this.mapBodyEl = this.el.querySelector('#map-body'); 
    /** @type {HTMLElement} */
    this.siteListEl = this.el.querySelector('#site-list'); 
    /** @type {HTMLElement} */
    this.viewOverlayFooterEl = this.el.querySelector('.modal-footer');
    /** @type {HTMLElement} */
    this.snapGridEl = this.el.querySelector('#snap-grid');
    /** @type {HTMLElement} */
    this.useLocationBtnEl = this.el.querySelector('#use-location');
    /** @type {HTMLElement} */
    //this.regionListEl = this.el.querySelector('#test-site-region-menu');
 
    this.onDocumentPress();
  }

  /**
  * @method createSiteList
  *
  * Create the site list in the modal body.
  * @param {Object} sites -  The feature collection of test sites.
  */
  createSiteList(sites) {
    d3.select(this.siteListEl)
        .selectAll('label')
        .remove();
    
    d3.select(this.siteListEl)
        .selectAll('label')
        .data(sites)
        .enter()
        .append('label')
        .attr('class', 'btn btn-sm btn-default')
        .attr('id', (feature) => { return feature.id; })
        .html((feature) => {
          return '<input type="radio" ' +
              'value="' + feature.id + '" />' +
              feature.properties.title;
        });
  }

  /**
  * @method createOverlay
  *
  * Create the location picker modal. Is hidden until show() method 
  *     is called.
  * @return {HTMLElement} The modal element.
  */
  createOverlay() {
    /* Modal */
    let overlayD3 = d3.select('body')
        .append('div')
        .attr('class', 'modal test-site-view')
        .attr('tabindex', '-1')
        .attr('role', 'dialog');

    /* Modal content */
    let contentD3 = overlayD3.append('div')
        .attr('class', 'modal-dialog modal-lg')
        .attr('role', 'document')
        .append('div')
        .attr('class', 'modal-content');
    
    let contentEl = contentD3.node(); 
    this.createOverlayHeader(contentEl);
    this.createOverlayBody(contentEl);
    this.createOverlayFooter(contentEl);

    overlayD3.lower();
    let el = overlayD3.node();
    
    return el;
  }

  /**
  * @method createOverlayBody
  *
  * Create modal body
  */
  createOverlayBody(modalEl) {
    let bodyD3 = d3.select(modalEl)
        .append('div')
        .attr('class', 'modal-body')
        .append('div')
        .attr('class', 'row');
    
    /* Map body */    
    bodyD3.append('div')
        .attr('id', 'map-body')
        .style('right', this.viewOptions.siteListWidth);
    
    /* Site list */
    bodyD3.append('div')
        .attr('id', 'site-list-body')
        .style('left', this.viewOptions.mapWidth)
        .append('div')
        .attr('class', 'form-group')
        .append('div')
        .attr('class', 'btn-group-vertical')
        .attr('id', 'site-list')
        .attr('data-toggle', 'buttons');
  }

  /**
  * @method createOverlayFooter
  *
  * Create modal footer
  */
  createOverlayFooter(modalEl) {
    /* Modal footer */
    let footerD3 = d3.select(modalEl)
        .append('div')
        .attr('class', 'modal-footer')
        .style('text-align', 'left');
 
     
    let formD3 = footerD3.append('div')
        .attr('class', 'form-inline');
     
    /*
    formD3.append('div')
        .attr('class', 'pull-left')
        .append('select')
        .attr('class', 'form-control')
        .style('width', 'auto')
        .attr('id', 'test-site-region-menu');
    */
    /* Snap grid checkbox */
    footerD3.append('label')
        .attr('class', 'hidden')
        .style('padding-right', '2em')
        .html('<input type="checkbox" id="snap-grid"> Snap to 0.1°');
    
    /* Use location button */
    formD3.append('div')
        .attr('class', 'pull-right')
        .append('button')
        .attr('class', 'btn btn-primary')
        .attr('id', 'use-location')
        .attr('type', 'button')
        .text('Use location');
  }

  /**
  * @method createOverlayHeader 
  *
  * Create modal header
  */
  createOverlayHeader(modalEl) {
    let headerD3 = d3.select(modalEl)
        .append('div')
        .attr('class', 'modal-header');
    
    headerD3.append('button')
        .attr('type', 'button')
        .attr('class', 'close')
        .attr('data-dismiss', 'modal')
        .append('span')
        .attr('aria-hidden', true)
        .html('&times;');

    headerD3.append('h4')
        .attr('class', 'modal-title')
        .text('Test Sites');
    
  }

  /*
  * @method createRegionMenu
  */
  createRegionMenu() {
    d3.select(this.regionListEl)
        .selectAll('option')
        .data(this.testSites)
        .enter()
        .append('option')
        .attr('value', (feature) => { return feature.properties.regionId; })
        .text((feature) => { return feature.properties.regionTitle; })
  }

  /**
  * @method getCoordinates
  *
  * Get the lat and lon based on if snap grid is checked
  * @param {Object} data - The test site GeoJson.
  * @return {Array<Number>} [lon, lat].
  */
  getCoordinates(data) {
    let isSnapChecked = this.snapGridEl.checked;
    let coords = data.geometry.coordinates;
    
    let lon = isSnapChecked ? Math.round(coords[0] * 10.0) / 10.0 :
        coords[0];
    
    let lat = isSnapChecked ? Math.round(coords[1] * 10.0) / 10.0 :
        coords[1];
    
    return [lon, lat];
  }

  /**
  * @method getRegion
  *
  * Find the region feature collection.
  * @param {String} regionId - The region to find.
  */
  getRegion(regionId) {
    return this.testSites.filter((feature) => {                     
      return feature.properties.regionId == regionId;                           
    });
  }
  
  /**
  * @method onDocumentPress
  *
  * Listen for return key press and if the use location btn
  *     is not disabled, click it. This stops the event from 
  *     propagating up to the parents.
  */
  onDocumentPress() {
    let returnKeyCode = 13;

    $(this.el).keypress((event) => {
      event.stopPropagation();
      let isDisabled = d3.select(this.useLocationBtnEl)
          .property('disabled');
      if (event.which == returnKeyCode && !isDisabled) {
        $(this.useLocationBtnEl).click();
      }
    });
  }
  
  /**
  * @method show
  * 
  * Show the Bootstrap modal and test site plot.
  */
  show() {
    $(this.el).modal({backdrop: 'static'});
  }
  
}
