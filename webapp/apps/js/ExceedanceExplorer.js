
import Footer from './lib/Footer.js';
import Header from './lib/Header.js';
import Constraints from './lib/Constraints.js';

import { D3LineSubViewOptions } from './d3/options/D3LineSubViewOptions.js';
import { D3LineView } from './d3/view/D3LineView.js';
import { D3LinePlot } from './d3/D3LinePlot.js';
import { D3XYPair } from './d3/data/D3XYPair.js';
import { D3LineData } from './d3/data/D3LineData.js';
import { D3LineOptions } from './d3/options/D3LineOptions.js';
import { D3LineLegendOptions } from './d3/options/D3LineLegendOptions.js';
import { Maths } from './calc/Maths.js';
import { ExceedanceModel } from './calc/ExceedanceModel.js';
import { Preconditions } from './error/Preconditions.js';
import { UncertaintyModel } from './calc/UncertaintyModel.js';

/**
 * @fileoverview Class for exceedance-explorer.html
 * 
 * @class ExceedanceExplorer
 */
export class ExceedanceExplorer {

  constructor() {
    /* Add footer */
    this.footer = new Footer()
        .removeButtons()
        .removeInfoIcon();

    /* Add header */
    this.header = new Header();

    /* Set HTML elements */
    this.formEl = document.querySelector('#inputs');
    this.controlPanelEl = document.querySelector('#control');
    this.medianEl = document.querySelector('#median');
    this.sigmaEl = document.querySelector('#sigma');
    this.truncationEl = document.querySelector('#truncation');
    this.truncationLevelEl = document.querySelector('#truncation-level');
    this.rateEl = document.querySelector('#rate');
    this.addPlotBtnEl = document.querySelector('#add-plot');
    this.clearPlotBtnEl = document.querySelector('#clear-plot');
    this.containerEl = document.querySelector('#content');
    this.removePlotBtnEl = document.querySelector('#remove-plot');

    this.removePlotBtnEl.disabled = true;
    this.clearPlotBtnEl.disabled = true;

    /* Default values */
    this.defaults = {
      median: 1,
      sigma: 0.5,
      truncation: true,
      truncationLevel: 3,
      rate: 1,
      xMin: 0.0001,
      xMax: 10.0,
      xPoints: 100
    };

    const formEls = [
      this.medianEl,
      this.rateEl,
      this.sigmaEl,
      this.truncationLevelEl
    ];

    this.addInputTooltips(formEls);
    this.onInputCheck(formEls);
    this.setDefaults();
    this.eventListeners();
    this.checkInputs();
    this.controlPanelEl.classList.remove('hidden');

    this.plotView = this.createView();
    this.plotView.setTitle('Exceedance');
   
    /* Create plot */
    this.plot = new D3LinePlot(this.plotView);
    this.exceedanceData = this.plot.upperLineData;

    this.sequence = this.createSequence();
    this.plotSelection();

    this.medianValues = [];
    this.sigmaValues = [];
    this.rateValues = [];
    this.truncationValues = [];
    this.truncationLevelValues = [];
  }

  /**
   * Add form input tooltips.
   * 
   * @param {HTMLElement[]} formEls 
   */
  addInputTooltips(formEls) {
    Preconditions.checkArgumentArrayInstanceOf(formEls, Element);

    for (let el of formEls) {
      Constraints.addTooltip(el, el.getAttribute('min'), el.getAttribute('max'));
    }
  }

  /**
   * Add a new plot
   */
  addPlot() {
    this.clearPlotBtnEl.disabled = false;

    let model = new UncertaintyModel(
        this.mean(),
        this.sigma(),
        this.truncationLevel() === 'N/A' ? 0 : this.truncationLevel());

    let sequence = [];
    let label = `μ=${this.median()}, σ=${this.sigma()},` +
        ` rate=${this.rate()}, n=${this.truncationLevel()}`;

    if (this.truncationEl.checked) {
      sequence = ExceedanceModel.truncationUpperOnlySequence(model, this.sequence);
    } else {
      sequence = ExceedanceModel.truncationOffSequence(model, this.sequence);
    }

    let xValues = sequence.map(xy => Math.exp(xy.x));
    let yValues = sequence.map(xy => Maths.round(xy.y * this.rate() , 4));

    let dataBuilder = this.getDataBuilder();

    let lineOptions = D3LineOptions.builder()
        .label(label)
        .markerSize(3)
        .lineWidth(1.25)
        .build();
    
    let data = dataBuilder
        .data(xValues, yValues, lineOptions)
        .removeSmallValues(1e-14)
        .build();

    this.exceedanceData = data;

    this.removePlotBtnEl.disabled = true;
    this.plot.clearAll();
    this.plot.plot(data);

    this.updateValues();
    this.setPlotData(data);
  }

  /**
   * Check for any form input errors
   */
  checkInputs() {
    const hasError = this.formEl.querySelectorAll('.has-error').length > 0;
    this.addPlotBtnEl.disabled = hasError;
  }

  /**
   * Clear all plots
   */
  clearPlot() {
    this.plot.clearAll();
    this.removePlotBtnEl.disabled = true;
    this.exceedanceData = D3LineData.builder()
        .subView(this.exceedanceData.subView)
        .build();
    
    this.medianValues = [];
    this.sigmaValues = [];
    this.rateValues = [];
    this.truncationValues = [];
    this.truncationLevelValues = [];
    
    this.clearPlotBtnEl.disabled = true;
  }

  /**
   * Create the XY sequence to plot
   */
  createSequence() {
    const xMin = this.defaults.xMin; 
    const xMax = this.defaults.xMax;
    const xPoints = this.defaults.xPoints

    return d3.ticks(Math.log(xMin), Math.log(xMax), xPoints).map((x) => {
      return new D3XYPair(x, 0);
    });
  }

  /**
   * Create the D3LineView
   */
  createView() {
    const legendOptions = D3LineLegendOptions.upperBuilder()
        .location('bottom-left')
        .build();

    const upperSubViewOptions = D3LineSubViewOptions.upperBuilder()
        .filename('exceedance')
        .label('Exceedance Models')
        .legendOptions(legendOptions)
        .lineLabel('Exceedance Model')
        .xValueToExponent(true)
        .xAxisScale('log')
        .xLabel('Ground Motion (g)')
        .yLabel('Annual Frequency of Exceedance')
        .build();

    return D3LineView.builder()
        .containerEl(this.containerEl)
        .upperSubViewOptions(upperSubViewOptions)
        .build();
  }

  /**
   * Add event listeners
   */
  eventListeners() {
    this.truncationEl.addEventListener('change', () => {
      this.truncationLevelEl.disabled = !this.truncationEl.checked;
    });

    this.formEl.addEventListener('change', () => {
      this.checkInputs();
    });

    this.formEl.addEventListener('input', () => {
      this.checkInputs();
    });

    this.addPlotBtnEl.addEventListener('click', () => {
      this.addPlot();
    });

    this.clearPlotBtnEl.addEventListener('click', () => {
      this.clearPlot();
    });
  }

  /**
   * Return a D3LineData.Builder for plotting of the previous data
   * before adding a new plot 
   */
  getDataBuilder() {
    let dataBuilder = D3LineData.builder()
        .subView(this.plot.view.upperSubView);
    
    for (let series of this.exceedanceData.series) {
      let lineOptions = D3LineOptions.builder()
          .label(series.lineOptions.label)
          .markerSize(series.lineOptions.markerSize)
          .lineWidth(series.lineOptions.lineWidth)
          .build();

      dataBuilder.data(series.xValues, series.yValues, lineOptions);
    }

    return dataBuilder;
  }

  /**
   * Add form input checks.
   * 
   * @param {HTMLElement[]} formEls 
   */
  onInputCheck(formEls) {
    Preconditions.checkArgumentArrayInstanceOf(formEls, Element);

    for (let el of formEls) {
      Constraints.onInput(el, el.getAttribute('min'), el.getAttribute('max'));
    }
  }

  /**
   * Return the mean value, ln(median)
   */
  mean() {
    return Math.log(this.median()); 
  }

  /**
   * Return the median value
   */
  median() {
    return Number(this.medianEl.value);
  }

  metadata() {
    const metadata = new Map();

    metadata.set('Median (g)', this.medianValues);
    metadata.set('Sigma (natural log units)', this.sigmaValues);
    metadata.set('Truncation', this.truncationValues);
    metadata.set('Truncation Level (n)', this.truncationLevelValues);

    return metadata;
  }

  /**
   * Remove selected plot if remove selected button is clicked.
   */
  plotSelection() {
    let removePlotEvent = () => {};
    
    this.plot.onPlotSelection(this.exceedanceData, (selectedData) => {
      this.removePlotBtnEl.disabled = false;

      let plotEvent = () => {
        let index = this.exceedanceData.series.findIndex(series => {
          return series.lineOptions.id === selectedData.lineOptions.id; 
        });

        this.exceedanceData.series.splice(index, 1);

        this.plot.clearAll();
        this.removePlotBtnEl.disabled = true;
        this.removeValues(index);
        this.setPlotData(this.exceedanceData);

        if(this.exceedanceData.series.length == 0) {
          this.clearPlotBtnEl.disabled = true;
          return;
        }

        this.plot.plot(this.exceedanceData);
      };

      this.removePlotBtnEl.removeEventListener('click', removePlotEvent);
      this.removePlotBtnEl.addEventListener('click', plotEvent); 

      removePlotEvent = plotEvent;
    });
  }

  /**
   * Return the rate value
   */
  rate() {
    return Number(this.rateEl.value);
  }

  /**
   * Remove values
   */
  removeValues(index) {
    Preconditions.checkArgumentInteger(index);

    this.medianValues.splice(index, 1);
    this.sigmaValues.splice(index, 1);
    this.rateValues.splice(index, 1);
    this.truncationValues.splice(index, 1);
    this.truncationLevelValues.splice(index, 1);
  }

  /**
   * Set the default form values
   */
  setDefaults() {
    this.medianEl.value = this.defaults.median;
    this.sigmaEl.value = this.defaults.sigma;
    this.truncationLevelEl.value = this.defaults.truncationLevel;
    this.truncationEl.setAttribute('checked', !this.defaults.truncation);
    this.rateEl.value = this.defaults.rate;
    this.truncationLevelEl.disabled = !this.truncationEl.checked;
  }

  /**
   * Set the plot data and metadata
   * @param {D3LineData} data
   */
  setPlotData(data) {
    Preconditions.checkArgumentInstanceOf(data, D3LineData);

    this.plotView.setSaveData(data);
    this.plotView.createDataTable(data);
    this.plotView.setMetadata(this.metadata());
    this.plotView.createMetadataTable();
  }

  /**
   * Return the sigma value
   */
  sigma() {
    return Number(this.sigmaEl.value);
  }

  /**
   * Return the truncation value
   */
  truncation() {
    return this.truncationEl.checked ? 'On' : 'Off';
  }

  /**
   * Return the truncation level value
   */
  truncationLevel() {
    return this.truncationEl.checked ?
        Number(this.truncationLevelEl.value) : 'N/A';
  }

  /**
   * Update values
   */
  updateValues() {
    this.medianValues.push(this.median());
    this.sigmaValues.push(this.sigma());
    this.rateValues.push(this.rate());
    this.truncationValues.push(this.truncation());
    this.truncationLevelValues.push(this.truncationLevel());
  }

}
