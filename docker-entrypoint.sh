#!/bin/bash

####
# Docker entrypoint to run nshmp-haz.
####

set -o errexit;
set -o errtrace;

# check_config_file global return variable
CHECK_CONFIG_FILE_RETURN="";

# check_site_file global return variable
CHECK_SITE_FILE_RETURN="";

# get_cous_model global return variable
GET_COUS_MODEL_RETURN="";

# get_model global return variable
GET_MODEL_RETURN="";

# get_model_path global return variable
GET_MODEL_PATH_RETURN="";

# get_nshmp_program global return variable
GET_NSHMP_PROGRAM_RETURN="";

# Log file
readonly LOG_FILE="docker-entrypoint.log";

####
# Run nshmp-haz.
# Globals:
#   (string) GET_MODEL_PATH_RETURN - The return of get_model_path
#   (string) GET_NSHMP_PROGRAM_RETURN - The return of get_nshmp_program
#   (string) CHECK_CONFIG_FILE_RETURN - The return of check_config_file
#   (string) CHECK_SITE_FILE_RETURN - The return of check_site_file
#   (numnber) IML - The intensity measure level for deagg-iml
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
  trap 'error_exit "${BASH_COMMAND}" "$(< ${LOG_FILE})"' ERR;

  # Get Java class to run
  get_nshmp_program 2> ${LOG_FILE};
  local nshmp_program="${GET_NSHMP_PROGRAM_RETURN}";

  # Get model path
  get_model_path 2> ${LOG_FILE};
  local nshmp_model_path="${GET_MODEL_PATH_RETURN}";

  # Check site file
  check_sites_file 2> ${LOG_FILE};
  local site_file="${CHECK_SITE_FILE_RETURN}";

  # Check config file
  check_config_file 2> ${LOG_FILE};
  local config_file="${CHECK_CONFIG_FILE_RETURN}";

  # Monitor log file
  tail -f ${LOG_FILE} & 

  # Set Java VisualVM
  local visualvm_opts="";
  if [ ${ACCESS_VISUALVM} = true ]; then
    visualvm_opts="-Dcom.sun.management.jmxremote
      -Dcom.sun.management.jmxremote.rmi.port=${VISUALVM_PORT}
      -Dcom.sun.management.jmxremote.port=${VISUALVM_PORT}
      -Dcom.sun.management.jmxremote.ssl=false
      -Dcom.sun.management.jmxremote.authenticate=false
      -Dcom.sun.management.jmxremote.local.only=false
      -Djava.rmi.server.hostname=${VISUALVM_HOSTNAME}
    ";
  fi

  # Run nshmp-haz
  java -Xms${JAVA_XMS} -Xmx${JAVA_XMX} \
      ${visualvm_opts} \
      -cp nshmp-haz.jar \
      gov.usgs.earthquake.nshmp.${nshmp_program} \
      "${nshmp_model_path}" \
      "${site_file}" \
      ${RETURN_PERIOD:+ "${RETURN_PERIOD}"} \
      ${IML:+ "${IML}"} \
      "${config_file}" 2> ${LOG_FILE} || \
      error_exit "Failed running nshmp-haz" "$(tail -n 55 ${LOG_FILE})";

  # Move artifacts to mounted volume
  move_to_output_volume 2> ${LOG_FILE};
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
  jq empty < ${CONFIG_FILE} 2> ${LOG_FILE} || \
      error_exit "Config file is not valid JSON" "$(< ${LOG_FILE})";

  # Return 
  CHECK_CONFIG_FILE_RETURN=${CONFIG_FILE};
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
      error_exit "Site file does not exist." "$(< ${LOG_FILE})";

  # Check if valid JSON or ASCII file
  case ${site_file} in
    *.geojson)
      jq empty < ${site_file} 2> ${LOG_FILE} || \
          error_exit "Site file [${site_file}] is not valid JSON" "$(< ${LOG_FILE})";
      ;;
    *.csv)
      if [[ "$(file ${site_file} -b)" != "ASCII text"* ]]; then
        error_exit \
            "Site file [${site_file}] is not valid ASCII" \
            "Site file is not valid ASCII";
      fi
      ;;
    *)
      error_exit "Bad site file [${site_file}]." "Bad site file.";
      ;;
  esac

  # Return
  CHECK_SITE_FILE_RETURN=${site_file};
}

####
# Download a USGS repository from Github.
# Arguments:
#   (string) repo - The project to download
#   (string) version - The version to download
# Returns:
#   None
####
download_repo() {
  local repo=${1};
  local version=${2};
  local url="https://github.com/usgs/${repo}/archive/${version}.tar.gz";

  printf "\n Downloading [${url}] \n\n";
  curl -L ${url} | tar -xz 2> ${LOG_FILE} || \
      error_exit "Could not download [${url}]" "$(< ${LOG_FILE})";
  mv ${repo}-${version#v*} ${repo};
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
        -e ACCESS_VISUALVM=<true | false> \\
        -e VISUALVM_PORT=<port> \\
        -e VISUALVM_HOSTNAME=<hostname> \\
        -v /absolute/path/to/config/file:/app/config.json \\
        -v /absolute/path/to/output:/app/output \\
        usgs/nshmp-haz
  ";

  local message="
    nshmp-haz Docker error:
    ${1}

    ----------
    Logs:

    ${2}

    ----------
    Usage:

    ${usage}

  ";

  printf "${message}";

  exit -1;
}

####
# Returns the model path for deagg-epsilon and hazard-2018. 
# Globals:
#   (string) MODEL - The nshm
#   (string) PROGRAM - The program to run
#   (string) GET_COUS_MODEL_RETURN - The return for the function
#   (string) NSHM_VERSION - The NSHM repository version
# Arguments:
#   None
# Returns:
#   (string) GET_COUS_MODEL_RETURN - The cous model path
####
get_cous_model() {
  local nshmp_model_path="";

  case ${MODEL} in
    "COUS-2008")
      nshmp_model_path="nshm-cous-2008/";
      download_repo "nshm-cous-2008" ${NSHM_VERSION};
      ;;
    "COUS-2014")
      nshmp_model_path="nshm-cous-2014/";
      download_repo "nshm-cous-2014" ${NSHM_VERSION};
      ;;
    "COUS-2018")
      nshmp_model_path="nshm-cous-2018/";
      download_repo "nshm-cous-2018" ${NSHM_VERSION};
      ;;
    *)
      error_exit "Model [${MODEL}] not supported for program [${PROGRAM}]" "Model not supported";
      ;;
  esac

  # Return 
  GET_COUS_MODEL_RETURN=${nshmp_model_path};
}

####
# Returns the model path for all programs except deagg-epsilon.
# Globals:
#   (string) MODEL - The nshm
#   (string) PROGRAM - The program to run
#   (string) GET_MODEL_RETURN - The return for the function
#   (string) NSHM_VERSION - The NSHM repository version
# Arguments:
#   None
# Returns:
#   (string) GET_MODEL_RETURN - The model path
####
get_model() {
  local nshmp_model_path="";

  case ${MODEL} in
    "AK-2007")
      nshmp_model_path="nshm-ak-2007";
      download_repo "nshm-ak-2007" ${NSHM_VERSION};
      ;;
    "CEUS-2008")
      nshmp_model_path="nshm-cous-2008/Central & Eastern US/";
      download_repo "nshm-cous-2008" ${NSHM_VERSION};
      ;;
    "CEUS-2014")
      nshmp_model_path="nshm-cous-2014/Central & Eastern US/";
      download_repo "nshm-cous-2014" ${NSHM_VERSION};
      ;;
    "CEUS-2018")
      nshmp_model_path="nshm-cous-2018/Central & Eastern US/";
      download_repo "nshm-cous-2018" ${NSHM_VERSION};
      ;;
    "WUS-2008")
      nshmp_model_path="nshm-cous-2008/Western US/";
      download_repo "nshm-cous-2008" ${NSHM_VERSION};
      ;;
    "WUS-2014")
      nshmp_model_path="nshm-cous-2014/Western US/";
      download_repo "nshm-cous-2014" ${NSHM_VERSION};
      ;;
    "WUS-2018")
      nshmp_model_path="nshm-cous-2018/Western US/";
      download_repo "nshm-cous-2018" ${NSHM_VERSION};
      ;;
    *)
      error_exit "Model [${MODEL}] not supported for program [${PROGRAM}]" "Model not supported";
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
  local nshmp_model_path="";

  if [ ${PROGRAM} == 'deagg-epsilon' ] || [ ${PROGRAM} == 'hazard-2018' ]; then
    get_cous_model 2> ${LOG_FILE};
    nshmp_model_path="${GET_COUS_MODEL_RETURN}";
  else
    get_model 2> ${LOG_FILE};
    nshmp_model_path="${GET_MODEL_RETURN}";
  fi

  # Return
  GET_MODEL_PATH_RETURN=${nshmp_model_path};
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
  local nshmp_program="";

  case ${PROGRAM} in
    "deagg")
      nshmp_program="DeaggCalc";
      ;;
    "deagg-epsilon")
      nshmp_program="DeaggEpsilon";
      ;;
    "deagg-iml")
      nshmp_program="DeaggIml";
      ;;
    "hazard-2018")
      nshmp_program="Hazard2018";
      ;;
    "hazard")
      nshmp_program="HazardCalc";
      ;;
    "rate")
      nshmp_program="RateCalc";
      ;;
    *)
      error_exit "Program [${PROGRAM}] not supported" "Program not supported";
      ;;
  esac
  
  # Return
  GET_NSHMP_PROGRAM_RETURN=${nshmp_program};
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
  local hazout=$(jq -r '.output.directory' ${CONFIG_FILE});

  if [ ${hazout} == null ]; then
    hazout="hazout";
  fi

  # Copy output to volume output
  cp -r ${hazout}/* output/. 2> ${LOG_FILE};
}

####
# Run main
####
main "$@";
