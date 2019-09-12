'use strict';

import Footer from './lib/Footer.js';
import Header from './lib/Header.js';
import Settings from './lib/Settings.js';

/**
* @fileoverview Class for index.html, this class 
*     creates the header and footer for the index page.
* 
* @class Dashboard
* @author bclayton@usgs.gov
*/
export default class Dashboard {
  
  /** 
  * @param {Object} config - config.json object, output from 
  *     Config.getConfig()
  */
  constructor(config) {
    /** @type {Footer} */
    this.footer = new Footer().removeButtons().removeInfoIcon();
    /** @type {Settings} */
    //this.settings = new Settings(footer.settingsBtnEl);
    /** @type {Header} */
    this.header = new Header();
    this.header.setTitle("Dashboard");
 
    /** @ type {Array{Object}} */
    this.webapps = [
      {
        label: 'Response Spectra',
        href: 'apps/spectra-plot.html',
      }, {
        label: 'Services',
        href: 'apps/services.html',
      }
    ];
    
    this.createDashboard();
  }

  /**
  * @method createDashboard
  *
  * Create the panels for the dashboard
  */
  createDashboard() {
    let elD3 = d3.select('body')
        .append('div')
        .attr('id', 'container')
        .append('div')
        .attr('id', 'dash');
  
    elD3.selectAll('div')
        .data(this.webapps)
        .enter()
        .append('div')
        .attr('class', 'col-sm-offset-4 col-sm-4 col-sm-offset-4')
        .on('click', (d, i) => { window.location = d.href; })
        .append('div')
        .attr('class', 'panel panel-default')
        .append('div')
        .attr('class', 'panel-heading')
        .append('h2')
        .attr('class', 'panel-title')
        .text((d, i) => {return d.label;});
  }

}
