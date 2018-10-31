%% nshmp-haz-ws Ground Motion Model (GMM) batch processing example script

clear;

%% URL to POST the CSV file of GMM inputs
% 
% Must set the URL host path, example: 
% 'http://localhost:8080/nshmp-haz/gmm/spectra'
%
% The GMMs must be specified in the URL query string.
%
% All GMM services are available to call for batch processing:
% https://dev01-earthquake.cr.usgs.gov/nshmp-haz-ws/apps/services.html#spectra
host = 'url/to/nshmp-haz-ws/gmm/spectra';

query = 'gmm=AB_06_PRIME&gmm=CAMPBELL_03&gmm=FRANKEL_96';

url = strcat(host, '?', query);


%% Read CSV file of GMM inputs
%
% Each column of the CSV file is a GMM input parameter with the 
% first row dictating that GMM input field.
%
% Example CSV to change only dip:
% dip,
% 0.0,
% 45.0,
% 90.0,
%
% For a full list of GMM input paramters see:
% http://usgs.github.io/nshmp-haz/javadoc/gov/usgs/earthquake/nshmp/gmm/GmmInput.html
%
% If 'null' is supplied as a value or a GMM input field and values are
% not given, the default values are used:
% http://usgs.github.io/nshmp-haz/javadoc/gov/usgs/earthquake/nshmp/gmm/GmmInput.Builder.html#withDefaults--
inputs = fileread('gmm-inputs.csv');


%% Set the response to JSON.
%
% The HTTP POST response is in JSON format. 
options = weboptions('ContentType', 'json');


%% Conduct HTTP POST Request
%
% Conduct a HTTP POST request, sending the CSV file of GMM inputs.
%
% The POST response is loaded into a structure
% following the returned JSON structure.
svcResponse = webwrite(url, inputs, options);


%% Check Response
%
% Check to see if the response returned an error and check
% to see if the field 'response' exists in the structure.
%
% If the URL does not contain a query string of GMMs the response 
% returned will be the service usage:
% https://dev01-earthquake.cr.usgs.gov/nshmp-haz-ws/gmm/spectra
%  
if strcmp('error', svcResponse.status) || ~isfield(svcResponse, 'response')
  return;
end


%% Retreive the data
%
% Loop through each response spectrum response and obtain the means 
% and sigmas.
for response = svcResponse.response'
  
  % Request structure contains the GMMs and GMM input parameters used 
  request = response.request;
  
  % The GMMs used for the calculation
  gmms = request.gmms;
  
  % The GMM input parameters used for the calculation
  input = request.input;
  
  % Get the means
  for means = response.means.data
    data = means.data;
    xMeans = data.xs;
    yMeans = data.ys;
  end
  
  % Get the sigmas
  for sigma = response.sigmas.data
    data = sigma.data;
    xSigma = data.xs;
    ySigma = data.ys;
  end
end
