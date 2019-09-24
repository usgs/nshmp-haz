
import { D3LineData } from './data/D3LineData.js';

import { Preconditions } from '../error/Preconditions.js';

/**
 * @fileoverview Save D3LineData to a CSV file
 * 
 * Use D3SaveLineData.saveCSV
 * 
 * @class D3SaveLineData
 * @author Brandon Clayton
 */
export class D3SaveLineData {

  /**
   * @private 
   * Use D3SaveLineData.saveCSV
   *  
   * @param {String} fileFormat The file format: 'csv'
   * @param  {...D3LineData} lineDatas The D3LineData(s)
   */
  constructor(fileFormat, ...lineDatas) {
    Preconditions.checkArgument(
        fileFormat == 'csv',
        `File format [${fileFormat}] not supported`);
    Preconditions.checkArgumentArrayInstanceOf(lineDatas, D3LineData);

    let fileData = [];

    for (let lineData of lineDatas) {
      let subViewOptions = lineData.subView.options;

      for (let series of lineData.series) {
        fileData.push([ subViewOptions.lineLabel, series.lineOptions.label ]); 
        let xValues = [];
        let yValues = [];

        for (let xyPair of series.data) {
          let x = subViewOptions.xValueToExponent ? 
              xyPair.x.toExponential(subViewOptions.xExponentFractionDigits) :
              xyPair.x;

          let y = subViewOptions.yValueToExponent ? 
              xyPair.y.toExponential(subViewOptions.yExponentFractionDigits) :
              xyPair.y;

          xValues.push(xyPair.xString || x);
          yValues.push(xyPair.yString || y);
        }

        fileData.push([ subViewOptions.xLabel, xValues.join(',') ]);
        fileData.push([ subViewOptions.yLabel, yValues.join(',') ]);
        fileData.push('');
      }
      
      let file = new Blob(
          [ fileData.join('\n') ], 
          { type: `text/${fileFormat}` });

      let aEl = document.createElement('a');
      aEl.download = `${subViewOptions.filename}.${fileFormat}`;
      aEl.href = URL.createObjectURL(file);
      aEl.click();
      aEl.remove();
    }
  }

  /**
   * Save D3LineData(s) to CSV files.
   * 
   * @param  {...D3LineData} lineDatas The data
   */
  static saveCSV(...lineDatas) {
    Preconditions.checkArgumentArrayInstanceOf(lineDatas, D3LineData);
    new D3SaveLineData('csv', ...lineDatas);
  }

}
