'use strict';

/**
* @class Tooltip
*
* @fileoverview Create a tooltip 
*/
export default class D3Tooltip {
  
  /**
  * @param {D3TooltipBuilder} builder 
  */
  constructor(builder) {
    this.dataEl = builder.dataEl;
    this.tooltipEl = builder.tooltipEl;
    this.tooltipText = builder.tooltipText;
    this.tooltipX = builder.tooltipX;
    this.tooltipY = builder.tooltipY;
    this.plotMarginLeft = builder._plotMarginLeft;
    this.plotMarginTop = builder._plotMarginTop;
      
    this.plotHeight = builder.plotHeight - this.plotMarginTop;
    this.plotWidth = builder.plotWidth - this.plotMarginLeft;
    
    this.options = {
      fontSize: 12,
      offsetX: 2,
      offsetY: 8,
      padding: 10,
      selectionIncrement: 2,
    };
    $.extend(this.options, builder.options || {});

    /** @type {Number} */
    this.offsetX = this.options.offsetX; 
    /** @type {Number} */
    this.offsetY = this.options.offsetY;
    /** @type {Number} */
    this.padding = this.options.padding; 
    
    let tableD3 = d3.select(this.tooltipEl) 
        .append('foreignObject')
        .attr('height', '100%')
        .attr('width', '100%')
        .append('xhtml:table')
        .style('font-size', this.options.fontSize + 'px')
        .style('border-collapse', 'separate')
        .style('border', '1px solid gray')
        .style('border-radius', '5px')
        .style('box-shadow', '0 1px 1px rgba(0, 0, 0, 0.05)')
        .style('padding', this.padding + 'px')
        .style('background', 'white');
        
    tableD3.selectAll('tr')
        .data(this.tooltipText)
        .enter()
        .append('tr')
        .append('td')
        .text((d, i) => { return d; });
    this.tooltipTableEl = tableD3.node();
         
    this.tooltipHeight = parseFloat(
        d3.select(this.tooltipTableEl).style('height'));
    this.tooltipWidth = parseFloat(
        d3.select(this.tooltipTableEl).style('width'));
    let tooltipTranslation = this.tooltipLocation();
   
    d3.select(this.tooltipEl)
        .select('foreignObject')
        .attr('height', this.tooltipHeight)
        .attr('width', this.tooltipWidth)
        .attr('transform', tooltipTranslation);
    
    d3.select(this.tooltipEl)
        .raise();
  }

  /**
  * @method Builder
  *
  * D3Tooltip builder.
  */ 
  static get Builder() {
    return class Builder {
      constructor() {}
       
      build() {
        return new D3Tooltip(this);
      }
     
      coordinates(x, y) {
        this.tooltipX = parseFloat(x);
        this.tooltipY = parseFloat(y);
        return this;
      }

      dataEl(dataEl) {
        this.dataEl = dataEl;
        return this;
      }

      options(options) {
        this.options = options;
        return this;
      }

      plotHeight(plotHeight) {
        this.plotHeight = plotHeight;
        return this;
      }

      plotMarginLeft(_plotMarginLeft) {
        this._plotMarginLeft = _plotMarginLeft;
        return this;
      }
      
      plotMarginTop(_plotMarginTop) {
        this._plotMarginTop = _plotMarginTop;
        return this;
      }

      plotWidth(plotWidth) {
        this.plotWidth = plotWidth;
        return this;
      }

      tooltipEl(tooltipEl) {
        this.tooltipEl = tooltipEl;
        return this;
      }
      
      tooltipText(tooltipText) {
        this.tooltipText = tooltipText;
        return this;
      }
    }
  }

  /**
  * @method destroy
  *
  * Method to remove the tooltip and remove all variables 
  * @param {Panel} panel - Upper or lower panel object
  */
  destroy() {
    this.remove();

    for( let obj in this) {
      this[obj] = null;
    }
  }
 
  /**
  * @method changeAttribute
  *
  * Method to change an attribute on the data element
  */ 
  changeAttribute(attr, value) {
    d3.select(this.dataEl).attr(attr, value);
    return this;
  }

  /**
  * @method increaseRadius
  *
  * Method to increase or decrease an attribute on the data element. 
  */
  changeSizeAttribute(attr, toIncrease = true) {
    let value = parseFloat(d3.select(this.dataEl).attr(attr));
    value = toIncrease ? value + this.options.selectionIncrement :
        value - this.options.selectionIncrement;
    
    d3.select(this.dataEl).attr(attr, value);
    return this;  
  }

  /**
  * @method pointColor
  *
  * Change the dot color
  * @param {String} color - The color the dot should be
  */
  pointColor(color) {
    d3.select(this.selectedEl)
        .attr('fill', color);
  }
    
  /**
  * @method remove
  *
  * Method to remove the tooltip
  * @param {Panel} panel - Upper or lower panel object
  */
  remove() {
    d3.select(this.tooltipEl)
        .select('*')
        .remove();
  }
 
  /**
  * @method tooltipLocation 
  *
  * Find best location to put the tooltip
  * @return {String} - The translation needed for the tooltip. 
  */
  tooltipLocation() {
    let availableWidth = this.plotWidth - this.tooltipX;
    let x = ( this.tooltipWidth + this.offsetX ) > availableWidth ? 
        this.tooltipX - this.tooltipWidth - this.offsetX + availableWidth :
        this.tooltipX + this.offsetX;

    let availableHeight = this.plotHeight - this.tooltipY;
    let y = ( this.tooltipHeight + this.offsetY ) > availableHeight ?
        this.tooltipY - this.tooltipHeight - this.offsetY : 
        this.tooltipY + this.offsetY; 

    return `translate(${x}, ${y})`; 
  }

}
