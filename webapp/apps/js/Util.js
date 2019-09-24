'use strict';

import Footer from './lib/Footer.js';
import Header from './lib/Header.js';

export default class Util{

  constructor(){
    let _this = this;

    _this.footer = new Footer();
    _this.header = new Header();
    _this.header.setTitle("Utilities");

    var urlPrefix = window.location.protocol 
        + "//" + window.location.host + "/nshmp-haz-ws";
    
    $(".serviceLink").each(function() {
      var serviceUrl = urlPrefix + $(this).text();
      $(this).empty().append($("<a>")
        .attr("href", serviceUrl)
        .text(serviceUrl));
    });
   
    
    $(".formatUrl").each(function(){
      var serviceUrl = urlPrefix + $(this).text();
      $(this).empty().text(serviceUrl);
    });

  }

}

