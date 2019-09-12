'use strict';

/**
* @class Header
*
* @fileoverview Creates the header to be used for all nshmp-haz-ws webapps.
*
* @typedef {Object} HeaderOptions - Object for header options
* @property {String} position - CSS positions: fixed || absolute
*
* @typedef {Array<Object>} HeaderMenu - Header menu labels and hrefs
* @property {String} label - The header menu text to be shown
* @property {String} href - The href for the corresponding menu
*
* @author bclayton@usgs.gov (Brandon Clayton)
*/
export default class Header{

  /**
  * @param {HTMLElement=} containerEl -  Optional container element 
  *     to put the header. Default is body.
  */
  constructor(containerEl = document.querySelector('body')) {
    document.title = "NSHMP-HAZ-WS";
    
    /** @type {HeaderOptions} */
    this.options = {
      position: 'fixed',
    };

    /** @type {HeaderMenu} */
    this.menuItems = [
      { 
        label: 'Dashboard', 
        href: '/nshmp-haz-ws/',
      }, {
        label: 'Dynamic Comparison', 
        href: '/nshmp-haz-ws/apps/dynamic-compare.html',
      }, {
        label: 'Geographic Deaggregation',
        href: '/nshmp-haz-ws/apps/geo-deagg.html',
      }, {
        label: 'Ground Motion Vs. Distance', 
        href: '/nshmp-haz-ws/apps/gmm-distance.html',
      }, {
        label: 'Hanging Wall Effects', 
        href: '/nshmp-haz-ws/apps/hw-fw.html',
      }, {
        label: 'Model Compare', 
        href: '/nshmp-haz-ws/apps/model-compare.html',
      }, {
        label: 'Model Explorer', 
        href: '/nshmp-haz-ws/apps/model-explorer.html',
      }, {
        label: 'Response Spectra', 
        href: '/nshmp-haz-ws/apps/spectra-plot.html',
      }, {
        label: 'Exceedance Explorer',
        href: '/nshmp-haz-ws/apps/exceedance-explorer.html'
      }, {
        label: 'Services',  
        href: '/nshmp-haz-ws/apps/services.html',
      }
    ];
            
    // Append header to body
    let headerD3 = d3.select(containerEl)
        .append('div')
        .attr('id', 'header');
    
    // Append webapp title
    headerD3.append('span')
        .attr('class', 'title')
        .attr('id', 'header-title')
        .text('');
    
    // // Create dropdown 
    // headerD3.append('div')
    //     .attr('class', 'dropdown-toggle')
    //     .attr('id', 'header-menu')
    //     .attr('data-toggle', 'dropdown')
    //     .append('span')
    //     .attr('class', 'glyphicon glyphicon-menu-hamburger');
    
    // // Append unordered list
    // let headerMenuD3 = headerD3.append('ul')
    //     .attr('class', 'dropdown-menu dropdown-menu-right')
    //     .attr('aria-labelledby', 'header-menu');
    
    // // Create dropdown list of all webapps 
    // headerMenuD3.selectAll('li')
    //     .data(this.menuItems)
    //     .enter()
    //     .append('li')
    //     .append('a')
    //     .text((d,i) => {return d.label})
    //     .attr('href', (d,i) => {return d.href});

    headerD3.lower(); 
    
    /** @type {HTMLElement} */
    this.containerEl = containerEl;
    /** @type {HTMLElement} */
    this.headerEl = headerD3.node(); 
    /** @type {HTMLElement} */
    this.headerListEl = this.headerEl.querySelector('ul');
    /** @type {HTMLElement} */
    this.headerTitleEl = this.headerEl.querySelector('#header-title');
  }

  /**
  * @method setCustomMenu
  *
  * Create a custom dropdown menu for the header
  * @param {HeaderMenu} menuItems - New custom header menu
  */
  setCustomMenu(menuItems) {
    d3.select(this.headerListEl)
        .selectAll('li')
        .remove();
  
    // Create dropdown list of all webapps 
    d3.select(this.headerListEl)
        .selectAll("li")
        .data(menuItems)
        .enter()
        .append('li')
        .append('a')
        .text((d,i) => {return d.label})
        .attr('href', (d,i) => {return d.href});

  }
  
  /**                                                                           
  * @method setOptions
  *
  * Set the header options.
  * @param {HeaderOptions} options - Header options
  * @return {Header} - Return class to be chainable
  */
  setOptions(options) {
    options.position = options.position == 'fixed' ||
        options.position == 'absolute' ? options.position : 'fixed'; 
     
    $.extend(this.options, options);
    this.updateOptions();
    return this;
  }
  
  /**
  * @method setTitle
  *
  * Set the header title next to the header menu.
  * @param {String} title - The header title
  * @return {Header} - Return class to be chainable
  */
  setTitle(title) {
    d3.select(this.headerTitleEl)
        .text(title);

    document.title = "NSHMP: " + title;
    return this;
  }
  
  /** 
  * @method updateOptions
  * 
  * Update the header options: the position of the header (fixed || absolute}
  */
  updateOptions() {
    d3.select(this.headerEl)
        .style('position', this.options.position); 
  }

}
