## nshmp-haz-ws Ground Motion Model (GMM) batch processing example script

import requests

## Read CSV file of GMM inputs
#
# Each column of the CSV file is a GMM input parameter with the 
# first row dictating that GMM input field.
#
# Example CSV to change only dip:
# dip,
# 0.0,
# 45.0,
# 90.0,
#
# For a full list of GMM input paramters see:
# http://usgs.github.io/nshmp-haz/javadoc/gov/usgs/earthquake/nshmp/gmm/GmmInput.html
#
# If 'null' is supplied as a value or a GMM input field and values are
# not given, the default values are used:
# http://usgs.github.io/nshmp-haz/javadoc/gov/usgs/earthquake/nshmp/gmm/GmmInput.Builder.html#withDefaults--
file = open('gmm-inputs.csv', 'r')

inputs = file.read()

file.close()


## URL to POST the CSV file of GMM inputs
# 
# Must update the URL host if not on localhost.
#
# The GMMs must be specified in the URL query string.
#
# All GMM services are available to call for batch processing.
host = 'http://localhost:8080'

service = '/nshmp-haz-ws/gmm/spectra'

url = host + service

query = { 'gmm': [ 'AB_06_PRIME', 'CAMPBELL_03', 'FRANKEL_96' ] }


## Conduct HTTP POST Request
#
# Conduct a HTTP POST request, sending the CSV file of GMM inputs.
#
# The POST response is loaded into a object 
# following the returned JSON structure.
svcResponse = requests.post(url, data = inputs, params = query).json()


## Check Response
#
# Check to see if the response returned an error and check
# to see if the field 'response' exists in the object.
#
# If the URL does not contain a query string of GMMs the response 
# returned will be the service usage.
if svcResponse['status'] == 'error' and ~hasattr(svcResponse, 'response'):
  exit()


## Retreive the data
#
# Loop through each response spectrum response and obtain the means 
# and sigmas.
for response in svcResponse['response']:

  # Request structure contains the GMMs and GMM input parameters used 
  request = response['request']

  # The GMMs used for the calculation
  gmms = request['gmms']
  
  # The GMM input parameters used for the calculation
  input = request['input']

  # Get the means
  for means in response['means']['data']:
    data = means['data']
    xMeans = data['xs']
    yMeans = data['ys']

  # Get the sigmas
  for sigmas in response['sigmas']['data']:
    data = sigmas['data']
    xSigmas = data['xs']
    ySigmas = data['ys']
