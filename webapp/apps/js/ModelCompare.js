
import { D3LineData } from './d3/data/D3LineData.js';
import { D3LineLegendOptions } from './d3/options/D3LineLegendOptions.js';
import { D3LineOptions } from './d3/options/D3LineOptions.js';
import { D3LinePlot } from './d3/D3LinePlot.js';
import { D3LineSubViewOptions } from './d3/options/D3LineSubViewOptions.js';
import { D3LineView } from './d3/view/D3LineView.js';
import { D3LineViewOptions } from './d3/options/D3LineViewOptions.js';

import { Hazard } from './lib/Hazard.js';
import NshmpError from './error/NshmpError.js';

export class ModelCompare extends Hazard {

  constructor(config) {
    super(config);

    this.header.setTitle("Model Comparison");

    this.options = {
      type: "compare",
      regionDefault: "COUS",
      imtDefault: "PGA",
      vs30Default: 760,
    };

    this.contentEl = document.querySelector('#content');
    this.hazardPlotTitle = 'Hazard Curves';

    /* Plot view options */
    this.viewOptions = D3LineViewOptions.builder()
        .titleFontSize(14)
        .build();

    /* Hazard curve plot setup */
    this.hazardView = this.setupHazardView();
    this.hazardLinePlot = new D3LinePlot(this.hazardView);

    this.comparableRegions = [
      {
        display: "Alaska",
        value: "AK",
        staticValue: "AK0P10",
        dynamicValue: "AK"
      }, {
        display: "Central & Eastern US",
        value: "CEUS",
        staticValue: "CEUS0P10",
        dynamicValue: "CEUS"
      }, {
        display: "Conterminous US",
        value: "COUS",
        staticValue: "COUS0P05",
        dynamicValue: "COUS"
      }, {
        display: "Western US",
        value: "WUS",
        staticValue: "WUS0P05",
        dynamicValue: "WUS"
      }
    ];

    let setParameters = (par) => {
      this.parameters = par;
      this.buildInputs()
    };

    this.getHazardParameters(setParameters);

    $(this.footer.updateBtnEl).click(() => {
      this.callHazard((result) => { this.callHazardCallback(result) });
    });

  }

  /**
   * Build the view for the hazard curves. 
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
        .filename('hazard-compare')
        .label('Hazard Curves')
        .lineLabel('Edition')
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

    this.testSitePicker.on('testSiteLoad', (event) => {
      this.checkQuery();
    });

    this.setParameterMenu("region", this.comparableRegions);
    this.setBounds();

    let supportedEditions = this.supportedEditions();
    this.setParameterMenu("edition", supportedEditions);
    d3.select(this.editionEl)
        .selectAll("option")
        .attr("selected", true);

    let supportedImt = this.supportedValues("imt");
    let supportedVs30 = this.supportedValues("vs30");
    this.setParameterMenu("imt", supportedImt);
    this.setParameterMenu("vs30", supportedVs30);

    $(this.regionEl).change(() => {
      this.hazardLinePlot.clearAll();

      this.clearCoordinates();
      this.setBounds();
      supportedEditions = this.supportedEditions();
      this.setParameterMenu("edition", supportedEditions);
      d3.select(this.editionEl)
          .selectAll("option")
          .attr("selected", true);

      supportedImt = this.supportedValues("imt");
      supportedVs30 = this.supportedValues("vs30");
      this.setParameterMenu("imt", supportedImt);
      this.setParameterMenu("vs30", supportedVs30);
    });

    $(this.editionEl).change(() => {
      supportedImt = this.supportedValues("imt");
      supportedVs30 = this.supportedValues("vs30");
      this.setParameterMenu("imt", supportedImt);
      this.setParameterMenu("vs30", supportedVs30);
    });

    $(this.controlEl).removeClass('hidden');

    let canSubmit = this.checkQuery();
    if (canSubmit) this.callHazard((result) => { this.callHazardCallback(result) });
  }

  /**
   * Get the metadata
   * @return {Map<String, Array<String>>} The metadata Map 
   */
  getMetadata() {
    let editionVals = $(this.editionEl).val();
    let editions = [];
    editionVals.forEach((val) => {
      editions.push(d3.select('#' + val).text());
    });

    let metadata = new Map();
    metadata.set('Region:', [$(this.regionEl).find(':selected').text()]);
    metadata.set('Edition:', editions);
    metadata.set('Latitude (°):', [this.latEl.value]);
    metadata.set('Longitude (°):', [this.lonEl.value]);
    metadata.set('Intensity Measure Type:', [$(this.imtEl).find(':selected').text()]);
    metadata.set('V<sub>S</sub>30:', [$(this.vs30El).find(':selected').text()]);

    return metadata;
  }

  supportedEditions() {
    var selectedRegion = this.comparableRegions.find((region) => {
      return region.value == this.regionEl.value;
    });
    var supportedEditions = this.parameters.edition
      .values.filter((editionValue) => {
        return editionValue.supports.region.find((regionValue) => {
          return regionValue == selectedRegion.staticValue ||
            regionValue == selectedRegion.dynamicValue;
        })
      });

    return supportedEditions;
  }

  plotHazardCurves(hazardResponse) {
    this.spinner.off();
    this.hazardLinePlot.clearAll();
    let lineData = this.hazardResponseToLineData(hazardResponse);

    this.hazardLinePlot.plot(lineData);
    this.updatePlotTitle(this.hazardView);

    let metadata = this.getMetadata();
    this.hazardView.setMetadata(metadata);
    this.hazardView.createMetadataTable();

    this.hazardView.setSaveData(lineData);
    this.hazardView.createDataTable(lineData);
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

  callHazardCallback(hazardReturn) {
    this.plotHazardCurves(hazardReturn);
    $(this.imtEl).off();
    $(this.imtEl).change(() => {
      this.plotHazardCurves(hazardReturn);
    });

  }

  hazardResponseToLineData(hazardResponses) {
    let dataBuilder = D3LineData.builder()
        .subView(this.hazardView.upperSubView)
        .removeSmallValues(this.Y_MIN_CUTOFF);

    if (hazardResponses.length > 10) {
      dataBuilder.colorScheme(d3.schemeCategory20);
    }

    for (let response of hazardResponses) {
      let dataType = response.dataType;

      let responseData = response.find((responseData) => {
        return responseData.metadata.imt.value == this.imtEl.value;
      });

      let data = responseData.data;
      let metadata = responseData.metadata;

      let xValues = [];
      let yValues = [];

      switch (dataType) {
        case 'dynamic':
          let componentData = data.find((d) => { return d.component == 'Total'; });
          xValues = metadata.xvalues;
          yValues = componentData.yvalues;
          break;
        case 'static':
          xValues = metadata.xvals;
          yValues = data[0].yvals;
          break;
        default:
          throw new NshmpError(`Response data type [${dataType}] not found`);
      }

      let lineOptions = D3LineOptions.builder()
          .id(metadata.edition.value)
          .label(metadata.edition.display)
          .build();

      dataBuilder.data(xValues, yValues, lineOptions);
    }

    return dataBuilder.build();
  }

}
