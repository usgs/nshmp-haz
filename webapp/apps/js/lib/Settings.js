'use strict';

export default class Settings{

  constructor(btnEl,options){
    let _this = this;

    _this.btnEl;
    _this.containerEl;
    
    _this.btnEl = btnEl;
    _this.containerEl = document.querySelector("body"); 

    _this.dev01Url = "https://dev01-earthquake.cr.usgs.gov/";
    _this.dev02Url = "https://dev02-earthquake.cr.usgs.gov/";
    _this.prod01Url = "https://prod01-earthquake.cr.usgs.gov/";
    _this.prod02Url = "https://prod02-earthquake.cr.usgs.gov/";

    _this.options = {
        serverId: "dev01",
        onSave: ""
    };
    
    $.extend(_this.options,options);
  
    Settings.getSettings(_this);
  
  
    d3.select(btnEl)
        .on("click",function(){
          Settings.open(_this);
        });
  
  }


  static open(_this){
    Settings.getSettings(_this);

    let settingsD3 = d3.select(_this.containerEl)
        .append("div")
        .attr("class","Settings");
    
    _this.el = settingsD3.node();

    settingsD3.append("div")
        .attr("class","settings-overlay")
        .on("click",function(){
          Settings.close(_this);
        });
    
    let panelD3 = settingsD3.append("div")
        .attr("class","settings-panel panel panel-default");
  
  
    panelD3.append("div")
        .attr("class","panel-heading")
        .append("h3")
        .attr("class","panel-title")
        .text("Settings");
    
    // Panel Body
    let panelBodyD3 =panelD3.append("div")
        .attr("class","panel-body"); 
    
    let serverInfo = [
        ["dev01", "Development 01"],
        ["dev02", "Development 02"],
        ["prod01", "Production 01"],
        ["prod02", "Production 02"]
    ];

    let formD3 = panelBodyD3.append("form")
        .attr("class","settings-form form-horizontal");
    
    let serverD3 = formD3.append("div")
        .attr("class","form-group");
    serverD3.append("label")
        .attr("class","control-group col-sm-2")
        .attr("for","server-form")
        .text("Server:");
    serverD3.append("div")
        .attr("class","col-sm-4")
        .append("select")
        .attr("class","form-control")
        .attr("id","server-form")
        .attr("name","server-form");
    
    serverD3.select("#server-form")
        .selectAll("option")
        .data(serverInfo)
        .enter()
        .append("option")
        .attr("value",function(d,i){return d[0]})
        .text(function(d,i){return d[1]});
    
    _this.serverEl = serverD3.select("#server-form").node();
    _this.serverEl.value = _this.options.serverId;

    // Panel footer
    let footerD3 = panelD3.append("div")
        .attr("class","panel-footer"); 
    
    // Append update plot button to footer
    footerD3.append("button")
        .attr("id","save-btn")
        .attr("class","btn btn-primary")
        .text("Save")
        .on("click",function(){
          Settings.save(_this);
        });
    
    // Append raw data button to footer
    let btnRightD3 = footerD3.append("span")
        .append("div")
        .attr("class","btn-float-right")
        .append("button")
        .attr("id","cancel-btn")
        .attr("class","btn btn-default")
        .text("Cancel")
        .on("click",function(){
          Settings.close(_this);
        });
   
  
  
  }


  static close(_this){
    d3.select(_this.el).remove();
  }

  static save(_this){
    d3.select(_this.el).remove();
   
    _this.options.serverId = _this.serverEl.value;
    Settings.setSettings(_this);
    
    localStorage.setItem("serverId",_this.serverEl.value); 
  }



  static getSettings(_this){

    let serverId = localStorage.getItem("serverId");
    serverId = serverId ? serverId : _this.options.serverId;
    _this.options.serverId = serverId;
    
    Settings.setSettings(_this);
  }



  static setSettings(_this){
    let serverId = _this.options.serverId;

    /*
    switch (serverId){
      case "dev01":
        _this.serverUrl = _this.dev01Url;
        break;  
      case "dev02":
        _this.serverUrl = _this.dev02Url;
        break;  
      case "prod01":
        _this.serverUrl = _this.prod01Url;
        break;  
      case "prod02":
        _this.serverUrl = _this.prod02Url;
        break;  
    }
    */
    _this.serverUrl = _this.dev01Url;
  }


}
