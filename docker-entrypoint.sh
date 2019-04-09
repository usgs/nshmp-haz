#!/bin/bash

####
# Docker entrypoint to run nshmp-haz.
#
# Usage:
#   docker run \
#       -e PROGRAM=<deagg | deagg-epsilon | hazard | rate> \\
#       -e MODEL=<WUS-20[08|14|18] | CEUS-20[08|14|18] | COUS-20[08|14|18] | AK-2007> \\
#       -v /absolute/path/to/sites/file:/app/sites.<geojson | csv> \\
#       -v /absolute/path/to/config/file:/app/config.json \\
#       -v /absolute/path/to/output:/app/output \\
#       usgsnshmp/nshmp-haz
####

set -o errexit
set -o errtrace

# check_config_file global return variable
CHECK_CONFIG_FILE_RETURN=""

# check_site_file global return variable
CHECK_SITE_FILE_RETURN=""

# get_cous_model global return variable
GET_COUS_MODEL_RETURN=""

# get_model global return variable
GET_MODEL_RETURN=""

# get_model_path global return variable
GET_MODEL_PATH_RETURN=""

# get_nshmp_program global return variable
GET_NSHMP_PROGRAM_RETURN=""

# Log file
LOG_FILE="docker-entrypoint.log"

####
# Run nshmp-haz.
# Globals:
#   (string) GET_MODEL_PATH_RETURN - The return of get_model_path
#   (string) GET_NSHMP_PROGRAM_RETURN - The return of get_nshmp_program
#   (string) CHECK_CONFIG_FILE_RETURN - The return of check_config_file
#   (string) CHECK_SITE_FILE_RETURN - The return of check_site_file
#   (string) JAVA_XMS - Java initial memory
#   (string) JAVA_XMX - Java max memory
#   (string) MODEL - The nshm
#   (string) PROGRAM - The program to run
#   (number) RETURN_PERIOD - The return period for deagg
# Arguments:
#   None
# Returns:
#   None
####
main() {
  # Set trap for uncaught errors
  trap 'error_exit "${BASH_COMMAND}" "$(< ${LOG_FILE})"' ERR

  # Get Java class to run
  get_nshmp_program 2> ${LOG_FILE} 
  local nshmp_program="${GET_NSHMP_PROGRAM_RETURN}"

  # Get model path
  get_model_path 2> ${LOG_FILE} 
  local nshmp_model_path="${GET_MODEL_PATH_RETURN}"

  # Check site file
  check_sites_file 2> ${LOG_FILE} 
  local site_file="${CHECK_SITE_FILE_RETURN}"

  # Check config file
  check_config_file 2> ${LOG_FILE} 
  local config_file="${CHECK_CONFIG_FILE_RETURN}"

  # Monitor log file
  tail -f ${LOG_FILE} & 

  # Run nshmp-haz
  java -Xms${JAVA_XMS} -Xmx${JAVA_XMX} -cp nshmp-haz.jar \
      gov.usgs.earthquake.nshmp.${nshmp_program} \
      "${nshmp_model_path}" \
      "${site_file}" \
      ${RETURN_PERIOD:+ "${RETURN_PERIOD}"} \
      "${config_file}" 2> ${LOG_FILE} || \
      error_exit "Failed running nshmp-haz" "$(tail -n 25 ${LOG_FILE})" 

  # Move artifacts to mounted volume
  move_to_output_volume 2> ${LOG_FILE} 
}

####
# Check that the config file is valid json. 
# Globals:
#   (string) CONFIG_FILE - The config file name
#   (string) CHECK_CONFIG_FILE_RETURN - The return for the function
# Arguments:
#   None
# Returns:
#   (string) CHECK_CONFIG_FILE_RETURN - The config file name
####
check_config_file() {
   # Check if file is valid JSON
  cat ${CONFIG_FILE} | jq empty 2> ${LOG_FILE} || \
      error_exit "Config file is not valid JSON" "$(< ${LOG_FILE})"

  # Return 
  CHECK_CONFIG_FILE_RETURN=${CONFIG_FILE}
}

####
# Check that the sites file is valid.
# Globals:
#   (string) CHECK_SITE_FILE_RETURN - The return for the function
# Arguments:
#   None
# Returns:
#   (string) CHECK_SITE_FILE_RETURN - The site file name
####
check_sites_file() {
  local site_file=$(ls sites*) 2> ${LOG_FILE} || \
      error_exit "Site file does not exist." "$(< ${LOG_FILE})"

  # Check if valid JSON or ASCII file
  case ${site_file} in
    *.geojson)
      cat ${site_file} | jq empty 2> ${LOG_FILE} || \
          error_exit "Site file [${site_file}] is not valid JSON" "$(< ${LOG_FILE})"
      ;;
    *.csv)
      if [ "$(file ${site_file} -b)" != "ASCII text" ]; then
        error_exit \
            "Site file [${site_file}] is not valid ASCII" \
            "Site file is not valid ASCII"
      fi
      ;;
    *)
      error_exit "Bad site file [${site_file}]." "Bad site file."
      ;;
  esac

  # Return
  CHECK_SITE_FILE_RETURN=${site_file}
}

####
# Exit script with error.
# Globals:
#   None
# Arguments:
#   (string) message - The error message
#   (string) logs - The log for the error
# Returns:
#   None
####
error_exit() {
  local usage="
    docker run \\
        -e PROGRAM=<deagg | deagg-epsilon | hazard | rate> \\
        -e MODEL=<WUS-20[08|14|18] | CEUS-20[08|14|18] | COUS-20[08|14|18] | AK-2007> \\
        -v /absolute/path/to/sites/file:/app/sites.<geojson | csv> \\
        -v /absolute/path/to/config/file:/app/config.json \\
        -v /absolute/path/to/output:/app/output \\
        usgsnshmp/nshmp-haz
  "

  local message="
    nshmp-haz Docker error:
    ${1}

    ----------
    Logs:

    ${2}

    ----------
    Usage:

    ${usage}

  "

  printf "${message}"

  exit -1
}

####
# Returns the model path for deagg-epsilon. 
# Globals:
#   (string) MODEL - The nshm
#   (string) PROGRAM - The program to run
#   (string) GET_COUS_MODEL_RETURN - The return for the function
# Arguments:
#   None
# Returns:
#   (string) GET_COUS_MODEL_RETURN - The cous model path
####
get_cous_model() {
  local nshmp_model_path=""

  case ${MODEL} in
    "COUS-2008")
      nshmp_model_path="nshm-cous-2008/"
      ;;
    "COUS-2014")
      nshmp_model_path="nshm-cous-2014/"
      ;;
    "COUS-2018")
      nshmp_model_path="nshm-cous-2018/"
      ;;
    *)
      error_exit "Model [${MODEL}] not supported for program ${PROGRAM}" "Model not supported"
      ;;
  esac

  # Return 
  GET_COUS_MODEL_RETURN=${nshmp_model_path}
}

####
# Returns the model path for all programs except deagg-epsilon.
# Globals:
#   (string) MODEL - The nshm
#   (string) PROGRAM - The program to run
#   (string) GET_MODEL_RETURN - The return for the function
# Arguments:
#   None
# Returns:
#   (string) GET_MODEL_RETURN - The model path
####
get_model() {
  local nshmp_model_path=""

  case ${MODEL} in
    "AK-2007")
      nshmp_model_path="nshm-ak-2007"
      ;;
    "CEUS-2008")
      nshmp_model_path="nshm-cous-2008/Central & Eastern US/"
      ;;
    "CEUS-2014")
      nshmp_model_path="nshm-cous-2014/Central & Eastern US/"
      ;;
    "CEUS-2018")
      nshmp_model_path="nshm-cous-2018/Central & Eastern US/"
      ;;
    "WUS-2008")
      nshmp_model_path="nshm-cous-2008/Western US/"
      ;;
    "WUS-2014")
      nshmp_model_path="nshm-cous-2014/Western US/"
      ;;
    "WUS-2018")
      nshmp_model_path="nshm-cous-2018/Western US/"
      ;;
    *)
      error_exit "Model [${MODEL}] not supported for program ${PROGRAM}" "Model not supported"
      ;;
  esac
  
  # Return
  GET_MODEL_RETURN=${nshmp_model_path}
}

####
# Returns the path to the model.
# Globals:
#   (string) PROGRAM - The program to run
#   (string) GET_MODEL_PATH_RETURN - The return value for the funciton
#   (string) GET_COUS_MODEL_RETURN -  The return for get_cous_model
#   (string) GET_MODEL_RETURN - The return for get_model
# Arguments:
#   None
# Returns:
#   (string) GET_MODEL_PATH_RETURN - The model path
####
get_model_path() {
  local nshmp_model_path=""

  if [ ${PROGRAM} == 'deagg-epsilon' ]; then
    get_cous_model 2> ${LOG_FILE} 
    nshmp_model_path="${GET_COUS_MODEL_RETURN}"
  else
    get_model 2> ${LOG_FILE} 
    nshmp_model_path="${GET_MODEL_RETURN}"
  fi

  # Return
  GET_MODEL_PATH_RETURN=${nshmp_model_path}
}

####
# Returns to nshmp-haz Java class to call.
# Globals:
#   (string) PROGRAM - The program to run: deagg | deagg-epsilon | hazard | rate
#   (string) GET_NSHMP_PROGRAM_RETURN - The return value for the function
# Arguments:
#   None
# Returns:
#   (string) GET_NSHMP_PROGRAM_RETURN - The Java class to call
####
get_nshmp_program() {
  local nshmp_program=""

  case ${PROGRAM} in
    "deagg")
      nshmp_program="DeaggCalc"
      ;;
    "deag-epsilon")
      nshmp_program="DeaggEpsilon"
      ;;
    "hazard")
      nshmp_program="HazardCalc"
      ;;
    "rate")
      nshmp_program="RateCalc"
      ;;
    *)
      error_exit "Program [${PROGRAM}] not supported" "Program not supported"
      ;;
  esac
  
  # Return
  GET_NSHMP_PROGRAM_RETURN=${nshmp_program}
}

####
# Move artifacts to mounted volume.
# Globals:
#   (string) CONFIG_FILE - The config file name
# Arguments:
#   None
# Returns:
#   None
####
move_to_output_volume() {
  # Get output directory
  local hazout=$(cat ${CONFIG_FILE} | jq -r '.output.directory')

  if [ ${hazout} == null ]; then
    hazout="hazout"
  fi

  # Copy output to volume output
  cp -r ${hazout}/* output/. 2> ${LOG_FILE} 
}

####
# Run main
####
main "$@" 2> ${LOG_FILE} 
