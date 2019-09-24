
import { D3LineData } from './d3/data/D3LineData.js';
import { D3LineLegendOptions } from './d3/options/D3LineLegendOptions.js';
import { D3LineOptions } from './d3/options/D3LineOptions.js';
import { D3LinePlot } from './d3/D3LinePlot.js';
import { D3LineSeriesData } from './d3/data/D3LineSeriesData.js';
import { D3LineSubViewOptions } from './d3/options/D3LineSubViewOptions.js';
import { D3LineView } from './d3/view/D3LineView.js';
import { D3LineViewOptions } from './d3/options/D3LineViewOptions.js';

import { Hazard } from './lib/Hazard.js';
import NshmpError from './error/NshmpError.js';

export class ModelExplorer extends Hazard {

  constructor(config) {
    super(config);

    this.header.setTitle("Model Explorer");

    this.options = {
      type: "explorer",
      editionDefault: "E2014",
      regionDefault: "COUS",
      imtDefault: "PGA",
      vs30Default: 760,
    };

    this.contentEl = document.querySelector("#content");
    this.hazardComponentPlotTitle = 'Hazard Component Curves';
    this.hazardPlotTitle = 'Hazard Curves';

    /* Plot view options */
    this.viewOptions = D3LineViewOptions.builder()
      .titleFontSize(14)
      .viewSize('min')
      .build();

    /* Hazard curve plot setup */
    this.hazardView = this.setupHazardView();
    this.hazardView.updateViewSize('max');
    this.hazardLinePlot = new D3LinePlot(this.hazardView);

    /* Hazard component curves setup */
    this.hazardComponentView = this.setupHazardComponentView();
    this.hazardComponentView.hide();
    this.hazardComponentLinePlot = new D3LinePlot(this.hazardComponentView);

    let setParameters = (par) => {
      this.parameters = par;
      this.buildInputs();
    };

    this.getHazardParameters(setParameters);

    $(this.footer.updateBtnEl).click(() => {
      this.callHazard((result) => { this.callHazardCallback(result); });
    });
  }


  /**
   * Build the view for the hazard component curves plot.
   * 
   * @returns {D3LineView} The hazard component line view
   */
  setupHazardComponentView() {
    /* Upper sub view legend options: hazard plot */
    let upperLegendOptions = D3LineLegendOptions.upperBuilder()
      .location('bottom-left')
      .build();

    /* Upper sub view options: hazard plot */
    let upperSubViewOptions = D3LineSubViewOptions.upperBuilder()
      .dragLineSnapTo(1e-10)
      .filename('hazard-explorer-components')
      .label('Hazard Component Curves')
      .legendOptions(upperLegendOptions)
      .lineLabel('IMT')
      .xLabel('Ground Motion (g)')
      .xAxisScale('log')
      .yAxisScale('log')
      .yLabel('Annual Frequency of Exceedence')
      .yValueToExponent(true)
      .build();

    /* Build the view */
    let view = D3LineView.builder()
      .containerEl(this.contentEl)
      .viewOptions(this.viewOptions)
      .upperSubViewOptions(upperSubViewOptions)
      .build();

    view.setTitle(this.hazardComponentPlotTitle);

    return view;
  }

  /**
   * Build the view for the hazard curve plot.
   * 
   * @returns {D3LineView} The hazard line view
   */
  setupHazardView() {
    /* Upper sub view legend options: hazard plot */
    let upperLegendOptions = D3LineLegendOptions.upperBuilder()
      .location('bottom-left')
      .build();

    /* Upper sub view options: hazard plot */
    let upperSubViewOptions = D3LineSubViewOptions.upperBuilder()
      .dragLineSnapTo(1e-8)
      .filename('hazard-explorer')
      .label('Hazard Curves')
      .lineLabel('IMT')
      .legendOptions(upperLegendOptions)
      .xAxisScale('log')
      .xLabel('Ground Motion (g)')
      .yAxisScale('log')
      .yLabel('Annual Frequency of Exceedence')
      .yValueToExponent(true)
      .build();

    /* Build the view */
    let view = D3LineView.builder()
      .addLowerSubView(false)
      .containerEl(this.contentEl)
      .viewOptions(this.viewOptions)
      .upperSubViewOptions(upperSubViewOptions)
      .build();

    view.setTitle(this.hazardPlotTitle);

    return view;
  }

  buildInputs() {
    this.spinner.off();

    let editionValues = this.parameters.edition.values;
    this.setParameterMenu("edition", editionValues);

    let supportedRegions = this.supportedRegions();
    this.setParameterMenu("region", supportedRegions);
    this.setBounds();

    let supportedImt = this.supportedValues("imt")
    let supportedVs30 = this.supportedValues("vs30")
    this.setParameterMenu("imt", supportedImt);
    this.setParameterMenu("vs30", supportedVs30);

    $(this.editionEl).change(() => {
      this.resetPlots();
      this.clearCoordinates();
      supportedRegions = this.supportedRegions();
      this.setParameterMenu("region", supportedRegions);
      this.setBounds();
      supportedImt = this.supportedValues("imt")
      supportedVs30 = this.supportedValues("vs30")
      this.setParameterMenu("imt", supportedImt);
      this.setParameterMenu("vs30", supportedVs30);
      this.testSitePicker.checkForRegion(this.region());
    });

    $(this.regionEl).change(() => {
      this.resetPlots();
      this.clearCoordinates();
      this.setBounds();
      supportedImt = this.supportedValues("imt")
      supportedVs30 = this.supportedValues("vs30")
      this.setParameterMenu("imt", supportedImt);
      this.setParameterMenu("vs30", supportedVs30);
      this.testSitePicker.checkForRegion(this.region());
    });

    $(this.controlEl).removeClass('hidden');

    this.testSitePicker.on('testSiteLoad', (event) => {
      let urlInfo = this.checkQuery();
      if (urlInfo) this.callHazard((result) => { this.callHazardCallback(result); });
    });
  }

  resetPlots() {
    this.hazardLinePlot.clearAll();
    this.hazardComponentLinePlot.clearAll();
    this.hazardView.setTitle(this.hazardPlotTitle);
    this.hazardComponentView.setTitle(this.hazardComponentPlotTitle);
  }

  /**
   * Get the metadata
   * @return {Map<String, Array<String>>} The metadata Map
   */
  getMetadata() {
    let metadata = new Map();
    metadata.set('Edition:', [$(this.editionEl).find(':selected').text()]);
    metadata.set('Region:', [$(this.regionEl).find(':selected').text()]);
    metadata.set('Latitude (°):', [this.latEl.value]);
    metadata.set('Longitude (°):', [this.lonEl.value]);
    metadata.set('Intensity Measure Type:', [$(this.imtEl).find(':selected').text()]);
    metadata.set('V<sub>S</sub>30:', [$(this.vs30El).find(':selected').text()]);

    return metadata;
  }

  supportedRegions() {
    let selectedEdition = this.parameters.edition
      .values.find((edition, i) => {
        return edition.value == this.editionEl.value;
      });

    let supportedRegions = this.parameters.region.values.filter((region, ir) => {
      return selectedEdition.supports.region.find((regionVal, irv) => {
        return regionVal == region.value;
      })
    });

    return supportedRegions;
  }

  callHazardCallback(hazardReturn) {
    this.plotHazardCurves(hazardReturn);
  }

  /**
   * 
   * @param {D3LineView} view 
   */
  updatePlotTitle(view) {
    let imt = $(':selected', this.imtEl).text();
    let vs30 = $(':selected', this.vs30El).text();
    let siteTitle = this.testSitePicker.getTestSiteTitle(this.region());
    let title = `${siteTitle}, ${imt}, ${vs30}`;

    view.setTitle(title);
  }

  onIMTChange(response) {
    this.hazardLinePlot.selectLine(
      this.imtEl.value,
      this.hazardResponseToLineData(response));

    this.updatePlotTitle(this.hazardView);
    if (response.dataType == 'dynamic') {
      this.plotComponentCurves(response);
    }
  }

  plotHazardCurves(responses) {
    this.spinner.off();
    this.hazardLinePlot.clearAll();
    let response = responses[0];

    let dataType = response.dataType;

    let lineData = this.hazardResponseToLineData(response);
    this.hazardLinePlot.plot(lineData);
    this.hazardLinePlot.selectLine(this.imtEl.value, lineData);
    this.updatePlotTitle(this.hazardView);

    $(this.imtEl).off();

    $(this.imtEl).change(() => {
      this.onIMTChange(response);
    });

    this.hazardLinePlot.onPlotSelection(
      lineData,
      (/** @type {D3LineSeriesData} */ dataSeries) => {
        this.onIMTSelection(dataSeries, response);
      });

    switch (dataType) {
      case 'dynamic':
        this.hazardView.updateViewSize('min');
        this.hazardComponentView.show();
        this.hazardComponentView.updateViewSize('min');
        this.plotComponentCurves(response);
        break;
      case 'static':
        this.hazardView.updateViewSize('max');
        this.hazardComponentView.hide();
        break;
      default:
        throw new NshmpError(`Response data type [${dataType}] not found`);
    }

    let metadata = this.getMetadata();
    this.hazardView.setMetadata(metadata);
    this.hazardView.createMetadataTable();

    this.hazardView.setSaveData(lineData);
    this.hazardView.createDataTable(lineData);
  }

  /**
   * 
   * @param {D3LineSeriesData} dataSeries 
   */
  onIMTSelection(dataSeries, hazardResponse) {
    this.imtEl.value = dataSeries.lineOptions.id;
    this.updatePlotTitle(this.hazardView);

    if (hazardResponse.dataType == 'dynamic') {
      this.plotComponentCurves(hazardResponse);
    }
  }

  hazardResponseToLineData(response) {
    let dataBuilder = D3LineData.builder()
      .subView(this.hazardView.upperSubView)
      .removeSmallValues(this.Y_MIN_CUTOFF);

    if (response.length > 10) {
      dataBuilder.colorScheme(d3.schemeCategory20);
    }

    let dataType = response.dataType;

    for (let responseData of response) {
      let data = responseData.data;
      let xValues = [];
      let yValues = [];

      switch (dataType) {
        case 'dynamic':
          let componentData = data.find((d) => { return d.component == 'Total'; });
          xValues = responseData.metadata.xvalues;
          yValues = componentData.yvalues;
          break;
        case 'static':
          xValues = responseData.metadata.xvals;
          yValues = data[0].yvals;
          break;
        default:
          throw new NshmpError(`Response data type [${dataType}] not found`);
      }

      let lineOptions = D3LineOptions.builder()
        .id(responseData.metadata.imt.value)
        .label(responseData.metadata.imt.display)
        .build();

      dataBuilder.data(xValues, yValues, lineOptions);
    }

    return dataBuilder.build();
  }

  plotComponentCurves(response) {
    this.hazardComponentLinePlot.clearAll();
    let lineData = this.hazardResponseToComponentLineData(response);
    this.hazardComponentLinePlot.plot(lineData);
    this.updatePlotTitle(this.hazardComponentView);

    let metadata = this.getMetadata();
    this.hazardComponentView.setMetadata(metadata);
    this.hazardComponentView.createMetadataTable();

    this.hazardComponentView.setSaveData(lineData);
    this.hazardComponentView.createDataTable(lineData);
  }

  hazardResponseToComponentLineData(hazardResponse) {
    let response = hazardResponse.find((response) => {
      return response.metadata.imt.value == this.imtEl.value;
    });

    let metadata = response.metadata;

    let components = response.data.filter((data) => {
      return data.component != 'Total';
    });

    let xValues = metadata.xvalues;

    let dataBuilder = D3LineData.builder()
      .subView(this.hazardComponentView.upperSubView)
      .removeSmallValues(this.Y_MIN_CUTOFF);

    for (let componentData of components) {
      let lineOptions = D3LineOptions.builder()
        .id(componentData.component)
        .label(componentData.component)
        .build();

      dataBuilder.data(xValues, componentData.yvalues, lineOptions);
    }

    return dataBuilder.build();
  }

}
