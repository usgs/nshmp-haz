'use strict';

import Footer from './Footer.js';
import Header from './Header.js';
import LeafletTestSitePicker from './LeafletTestSitePicker.js';
import NshmpError from '../error/NshmpError.js';
import Settings from './Settings.js';
import Spinner from './Spinner.js';
import Tools from './Tools.js';

export class Hazard {

  constructor(config){
    this.footer = new Footer();
    this.footerOptions = {
      rawBtnDisable: true,
      updateBtnDisable: true
    };
    this.footer.setOptions(this.footerOptions);
    
    // Create header                                                            
    this.header = new Header();                                                
    
    // Create spinner                                                           
    this.spinner = new Spinner();                                              

    // Settings menu
    //this.settings = new Settings(this.footer.settingsBtnEl);
     
    this.controlEl = document.querySelector("#control");
    this.editionEl = document.getElementById("edition");
    this.regionEl = document.getElementById("region");
    this.imtEl = document.getElementById("imt");
    this.vs30El = document.getElementById("vs30");
    this.latBoundsEl = document.getElementById("lat-bounds");
    this.lonBoundsEl = document.getElementById("lon-bounds");
    this.latEl = document.getElementById("lat");
    this.lonEl = document.getElementById("lon");
    this.latFormEl = document.getElementById("lat-form");
    this.lonFormEl = document.getElementById("lon-form");

    this.Y_MIN_CUTOFF = 1e-16;

    this.config = config;

    $(this.lonEl).on('input', (event) => {
      this.checkCoordinates(false,true);
    });
    
    $(this.latEl).on('input', (event) => {
      this.checkCoordinates(true,false);
    });

    $(this.controlEl).on('input change', (event) => {
      let canSubmit = this.checkCoordinates(false,false);
      this.footerOptions = {
        updateBtnDisable: !canSubmit
      };
      this.footer.setOptions(this.footerOptions);
    });
 
    this.dynamicUrl = this.config.server.dynamic + "/nshmp-haz-ws/hazard";
    this.staticUrl  = this.config.server.static + "/hazws/staticcurve/1";
    
    this.testSitePickerBtnEl = document.querySelector('#test-site-picker');
  
    /* @type {LeafletTestSitePicker} */
    this.testSitePicker = new LeafletTestSitePicker(
        this.latEl,
        this.lonEl,
        this.testSitePickerBtnEl);
    
    /* Bring Leaflet map up when clicked */
    $(this.testSitePickerBtnEl).on('click', (event) => {
      this.testSitePicker.plotMap(this.region());
    });
  }

  /**
   * Get current region value
   */
  region() {
    let region = '';

    switch(this.regionEl.value) {
      case 'CEUS':
      case 'CEUS0P10':
        region = 'CEUS';
        break;
      case 'COUS':
      case 'COUS0P05':
        region = 'COUS';
        break;
      case 'WUS':
      case 'WUS0P05':
        region = 'WUS';
        break;
      case 'AK':
      case 'AK0P10':
        region = 'AK';
        break;
      case 'HI0P02':
        region = 'HAWAII';
        break;
      case 'PRIVI0P01':
      case 'GMNI0P10':
      case 'AMSAM0P05':
        region = null;
        break;
      default:
        region = null;
    }

    return region;
  }

  getHazardParameters(callback) {
    let jsonCall = Tools.getJSONs([this.dynamicUrl, this.staticUrl]); 
    this.spinner.on(jsonCall.reject, 'Calculating');

    Promise.all(jsonCall.promises).then((responses) => {    
      NshmpError.checkResponses(responses);
      let dynamicParameters = responses[0].parameters;
      let staticParameters = responses[1].parameters;

      var mainPars    = ["edition","region"];
      var editionType = ["static","dynamic"];

      for (var jt in editionType){
        var par = eval(editionType[jt]+"Parameters");
        for (var jp in mainPars){
          for (var jv in par[mainPars[jp]].values){
            par[mainPars[jp]].values[jv].dataType = editionType[jt];
          }
        }
      }
      
      //.................. Combine Static and Dynamic Parameters ...........
      var editionValues = staticParameters.edition.values
          .concat(dynamicParameters.edition.values);
      var regionValues = staticParameters.region.values
          .concat(dynamicParameters.region.values);
      var imtValues = staticParameters.imt.values;
      var vs30Values = staticParameters.vs30.values;

      //........ Sort Combined Parameters by Display Order Parameter .......
      editionValues.sort(this.sortDisplayorder); 
      regionValues.sort(this.sortDisplayorder);
      imtValues.sort(this.sortDisplayorder);
      vs30Values.sort(this.sortDisplayorder);

      //....... Create a Single Parameter Object for Static and Dynamic ....
      var combinedParameters = {
        edition: {
          label: dynamicParameters.edition.label,
          type: dynamicParameters.edition.type,
          values: editionValues
        },
        region: {
          label: dynamicParameters.region.label,
          type: dynamicParameters.region.type,
          values: regionValues
        },
        imt: { 
          label: dynamicParameters.imt.label,
          type: dynamicParameters.imt.type,
          values: imtValues
        },
        vs30: { 
          label: dynamicParameters.vs30.label,
          type: dynamicParameters.vs30.type,
          values: vs30Values
        }
      };

      callback(combinedParameters); 
    }).catch((errorMessage) => {
      this.spinner.off();
      NshmpError.throwError(errorMessage);
    }); 
  
  }
  
  /*
  - The sort_displayorder function takes a parameter, 
      like edition, and sorts them based on the display 
      order given in the two JSON files
  - This function returns the subtraction of the display 
      order values of two editions to see which one should be 
      displayed first (a negative value return is displayed first)
  */
  sortDisplayorder(a,b){
    return (a.displayorder - b.displayorder);
  }      

  setSelectMenu(el,options){
    
    d3.select(el)
        .selectAll("option")
        .data(options)
        .enter()
        .append("option")
        .attr("value",(d,i) => {return d.value})
        .attr("id",(d,i) => {return d.value})
        .text((d,i) => {return d.display.replace("&amp;","&")})
  }                                                                             

  /*
  -  This function is used for model-compare and model-explorer

  Format of Dynamic URL:
    https://earthquake.usgs.gov/nshmp-haz-ws/hazard?
        edition=value&region=value&longitude=value
        &latitude=value&imt=value&vs30=value

  Format of Static URL:
    https://earthquake.usgs.gov/hazws/staticcurve/1
    /{edition}/{region}/{longitude}/{latitude}/{imt}/{vs30}"
  */
  composeHazardUrl(edition,region,lat,lon,vs30,dataType){
    if (dataType == "static"){  
      var urlInfo =  {
        dataType: "static",
        url: this.staticUrl +
        edition + "/" + 
        region  + "/" +
        lon     + "/" +
        lat     + "/" +
        "any"   + "/" +
        vs30   
      };
    }else if (dataType == "dynamic"){
      var urlInfo =  {
        dataType: "dynamic", 
        url: this.dynamicUrl +
        "?edition="   + edition   +
        "&region="    + region    +
        "&longitude=" + lon       +
        "&latitude="  + lat       +
        "&vs30="      + vs30
      };
    }
    return urlInfo;
  }

  checkQuery(){
    let url = window.location.hash.substring(1);
    
    if (!url) return false;
    
    let pars = url.split("&");
    let key;
    let value;
    let lat;
    let lon;
    let vs30;
    let imt;
    let editions = [];
    let dataType = [];
    let regions = [];
    let urlInfo = [];
    pars.forEach((par,i) => {
      key = par.split("=")[0];
      value = par.split("=")[1];
      switch (key){
        case("lat"):
          lat = value;
          break;
        case("lon"):
          lon = value;
          break;
        case("vs30"):
          vs30 = value;
          break;
        case("imt"):
          imt = value;
          break;
        case("edition"):
          editions.push(value);
          break;
        case("dataType"):
          dataType.push(value);
          break;
        case("region"):
          regions.push(value);
          break;
      }
    });
    
    d3.select(this.editionEl)
        .selectAll("option")
        .property("selected",false);
   
    editions.forEach((edition,i) => {
      d3.select(this.editionEl)
          .select("#"+edition)
          .property("selected",true);
    });

    $(this.editionEl).trigger('change');

    this.latEl.value = lat;
    this.lonEl.value = lon;
    this.imtEl.value = imt;
    this.vs30El.value = vs30;

    if (this.options.type == "compare"){ 
      let comparableRegion = this.comparableRegions.find((d,i) => {
        return d.staticValue == regions[0] || d.dynamicValue == regions[0];
      });
      this.regionEl.value = comparableRegion.value;
      this.options.regionDefault = comparableRegion.value;
    }else{
      this.regionEl.value = regions[0];
      this.options.regionDefault = regions[0];
      this.options.editionDefault = editions[0];
    }
    
    this.options.imtDefault = imt;
    this.options.vs30Default = vs30;
    
    
    return true; 
  }

  setParameterMenu(par,supportedValues){
    let el = eval("this."+par+"El");
    d3.select(el)
        .selectAll("option")
        .remove();
    
    if ((this.options.type == "explorer" && par == "region") || 
          (this.options.type == "compare" && par == "edition" || "region"))
      this.setSelectMenu(el,supportedValues);
    else
      this.setSelectMenu(el,this.parameters[par].values);
    

    d3.select(el)
        .selectAll("option")
        .property("disabled",true)
        .filter((d,i) => {
          return supportedValues.some((sv,isv) => {
            return d.value == sv.value;
          })
        })
        .property("disabled",false);
    
    let defaultVal = this.options[par+"Default"];
    let isFound = supportedValues.some((val,i) => {
      return val.value == defaultVal; 
    });
    defaultVal = isFound ? defaultVal 
        : supportedValues[0].value;  
    el.value = defaultVal;                                                      
  }
  
  supportedValues(par){
    
    let type = this.options.type;
    let supports = [];                                                          
    let selectedEditions = this.editionEl.querySelectorAll(":checked");
    selectedEditions.forEach((e,i) => {
      let edition = this.parameters.edition.values.find((ev,iev) => {
        return ev.value == e.value;
      });
      supports.push(edition.supports[par]);
      let dataType = edition.dataType;
      if (type == "compare"){
        let comparableRegion = this.comparableRegions.find((r,ir) => {
          return r.value == this.regionEl.value;
        });
        let region = this.parameters.region.values.find((r,ir) => {
          return r.value == comparableRegion[dataType+"Value"];
        });
        supports.push(region.supports[par]);
      }else if (type == "explorer"){
        let region = this.parameters.region.values.find((r,ir) => {
          return r.value == this.regionEl.value; 
        });
        supports.push(region.supports[par]);
      }
    });
    
    let supportedValues = this.parameters[par].values.filter((p,ip) => {
      return supports.every((pc,ipc) => {
        return pc.includes(p.value);
      });
    });
    
    return supportedValues;
  }

  setBounds(){
    
    let latMax,
        latMin,
        lonMax,
        lonMin,
        region;
    
    region = this.parameters.region.values.find((d,i) => {
      return d.value == this.regionEl.value;
    });
    
    latMax = region.maxlatitude;
    latMin = region.minlatitude;
    lonMax = region.maxlongitude;
    lonMin = region.minlongitude;
    
    this.latBoundsEl.innerHTML = "<br>" + this.regionEl.value +
        " bounds: " + " ["+latMin+","+latMax+"]";
    
    this.lonBoundsEl.innerHTML = "<br>" + this.regionEl.value +
        " bounds: " + " ["+lonMin+","+lonMax+"]";
  
  }                                                                             
  
  checkCoordinates(checkLat,checkLon){
    let latMax,
        latMin,
        lonMax,
        lonMin,
        region;
    
    region = this.parameters.region.values.find((d,i) => {
      return d.value == this.regionEl.value;
    });
    
    latMax = region.maxlatitude;
    latMin = region.minlatitude;
    lonMax = region.maxlongitude;
    lonMin = region.minlongitude;
    
    let lat = this.latEl.value;
    let lon = this.lonEl.value;
    
    let canLatSubmit = lat < latMin || lat > latMax
        || isNaN(lat) ? false : true;
    let canLonSubmit = lon < lonMin || lon > lonMax
        || isNaN(lon) ? false : true;
    
    if(checkLat){
      d3.select(this.latFormEl)
          .classed("has-error",!canLatSubmit);
      d3.select(this.latFormEl)
          .classed("has-success",canLatSubmit);
    }
    if(checkLon){
      d3.select(this.lonFormEl)
          .classed("has-error",!canLonSubmit);
      d3.select(this.lonFormEl)
          .classed("has-success",canLonSubmit);
    }
    
    return canLatSubmit && canLonSubmit ? true : false;
  }
                                                                                
  clearCoordinates(){
    this.latEl.value = "";
    this.lonEl.value = "";
    
    d3.select(this.latFormEl)
        .classed("has-error",false);
    d3.select(this.latFormEl)
        .classed("has-success",false);
    
    d3.select(this.lonFormEl)
        .classed("has-error",false);
    d3.select(this.lonFormEl)
        .classed("has-success",false);
  }

  getSelections(){
    
    $(this.footer.rawBtnEl).off();
    
    let selectedEditions = this.editionEl.querySelectorAll(":checked");
    let vs30 = this.vs30El.value;
    let lat = this.latEl.value;
    let lon = this.lonEl.value;
    let imt = this.imtEl.value;
    
    let type = this.options.type;
    if (type == "compare"){
      var regionInfo = this.comparableRegions.find((d,i) => {
        return d.value == this.regionEl.value;
      });
    }
    var urlInfo = [];
    let windowUrl = "lat="+lat+"&lon="+lon+"&vs30="+vs30+"&imt="+imt;
    selectedEditions.forEach((se,ise) => {
      var editionInfo = this.parameters.edition.values.find((d,i) => {
        return d.value == se.value;
      });
      var dataType = editionInfo.dataType;
      var editionVal = editionInfo.value;
      var regionVal  = type == "compare" ? regionInfo[dataType+"Value"]
          : this.regionEl.value;
      windowUrl += "&dataType="+dataType+"&edition="
          +editionVal+"&region="+regionVal;
      let url = this.composeHazardUrl(editionVal,regionVal,
          lat,lon,vs30,dataType);
      urlInfo.push(url);
    });
    
    window.location.hash = windowUrl;
    
    $(this.footer.rawBtnEl).click(() => {
      urlInfo.forEach((url,iu) => {
        window.open(url.url);
      })
    });
    
    return urlInfo; 
  }

  callHazard(callback){
    
    var canSubmit = this.checkCoordinates(true,true);
    if (!canSubmit) return;
    
    let urlInfo = this.getSelections();
    
    this.footerOptions = {
      rawBtnDisable: false,
      updateBtnDisable: false
    };
    this.footer.setOptions(this.footerOptions);
    
    let urls = [];
    for (var ju in urlInfo){
      urls.push(urlInfo[ju].url);
    }

    let jsonCall = Tools.getJSONs(urls);
    this.spinner.on(jsonCall.reject, 'Calculating');
    
    Promise.all(jsonCall.promises).then((responses) => {
      NshmpError.checkResponses(responses);

      let jsonResponse = [];
      
      responses.forEach((jsonReturn,i) => {
        jsonReturn.response.dataType = urlInfo[i].dataType;
        jsonResponse.push(jsonReturn.response);
      });
     
      let responseWithServer = responses.find((d, i) => {
        return d.server;
      });
      
      let server = responseWithServer != undefined ?
          responseWithServer.server : undefined;
      this.footer.setMetadata(server);

      callback(jsonResponse); 
    }).catch((errorMessage) => {
      this.spinner.off();
      NshmpError.throwError(errorMessage);
    });
  
  }

}
