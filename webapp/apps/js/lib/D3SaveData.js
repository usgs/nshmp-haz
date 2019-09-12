'use strict';

/**
* @class D3SaveData
* 
* @fileoverview  Save multiple data sets as a CSV or TSV file.
* Use Builder to set multiple data sets.
*/
export default class D3SaveData { 
  
  constructor(builder) {
    this.dataSeries = builder.data;
    this.filename = builder.filename;
    this.fileFormat = builder.fileFormat;
    this.dataSeriesLabels = builder.dataSeriesLabels;
    this.dataRowLabels = builder.dataRowLabels;
   
    let delimiter = this.fileFormat == 'txt' ? '\t' : ',';
    let dataRow = [];

    this.dataSeries.forEach((series, ids) => {
      series.forEach((data, id) => { 
        let dataTranspose = d3.transpose(data);
        dataRow.push([
            this.dataRowLabels[ids][0], 
            this.dataSeriesLabels[ids][id]]);
        
        dataTranspose.forEach((dataArray, ida) => {
          dataRow.push([
            this.dataRowLabels[ids][ida + 1], 
            dataArray.join(delimiter)
          ]);
        })
        dataRow.push('');
      });
      dataRow.push('\n');
    });
  
    let file = new Blob(
        [dataRow.join('\n')], 
        {type:'text/' + this.fileFormat}
    );
    let aEl = document.createElement('a');
    aEl.download = this.filename + '.' + this.fileFormat;
    aEl.href = URL.createObjectURL(file);
    aEl.click();
    aEl.remove();
  }

  /**
  * @method Builder
  * 
  * Builder for D3SaveData
  */
  static get Builder() {
    return class Builder {
      
      constructor() {
        this.data = [];
        this.dataRowLabels = [];
        this.dataSeriesLabels = [];
      }
    
      build() {
        return new D3SaveData(this);
      }

      addData(data) {
        this.data.push(data);
        return this;
      }

      addDataRowLabels(dataRowLabels) {
        this.dataRowLabels.push(dataRowLabels);
        return this;
      }

      addDataSeriesLabels(dataSeriesLabels) {
        this.dataSeriesLabels.push(dataSeriesLabels);
        return this;
      }
      
      filename(filename) {
        this.filename = filename;
        return this;
      }
      
      fileFormat(fileFormat) {
        this.fileFormat = fileFormat.toLowerCase();
        return this;
      }
      
    }
  }

}
