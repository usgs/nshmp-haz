#!/bin/bash

####
# Check current exit status.
#
# @param $1 exit_status {Integer}
#     Current exit status
####
check_exit_status() {
  local exit_status=${1};
  [ "${exit_status}" -eq 0 ] || exit "${exit_status}";
}

####
# Check that the sites file is valid.
#
# @return String
#     The site file name
# @status Integer
#     The exit status
####
check_sites_file() {
  local site_file;
  local exit_status;
  site_file=$(ls sites*) || error_exit "Site file does not exist." 1;

  # Check if valid JSON or ASCII file
  case ${site_file} in
    *.geojson)
      jq empty < "${site_file}";
      exit_status=${?};
      ;;
    *.csv)
      if [[ "$(file "${site_file}" -b)" != "ASCII text"* ]]; then
        error_exit "Site file [${site_file}] is not valid ASCII" 1;
      fi
      ;;
    *)
      error_exit "Bad site file [${site_file}]." 1;
      ;;
  esac

  echo "${site_file}";
  return "${exit_status}";
}

####
# Download a repository from Github.
#
# @param $1 url {String}
#     The url to download
# @param $2 branch {String}
#     The branch or tag to checkout
#
# @status Integer
#     The status of the curl call
####
download_repo() {
  local url=${1};
  local branch=${2};
  local exit_status;

  git clone --depth 1 -b "${branch}" "${url}";
  exit_status=${?};

  if [ ${exit_status} -ne 0 ]; then
    error_exit "Could not download [${url}]" ${exit_status};
  fi

  return ${exit_status};
}

####
# Exit with an error message.
#
# @param $1 msg {String}
#     The message for exit
# @param $2 exit_status {Integer}
#     The exit status
####
error_exit() {
  local msg=${1};
  local exit_status=${2}
  echo "Error: ${msg}" >> /dev/stderr;
  exit "${exit_status}";
}

####
# Returns the model path for deagg-epsilon and hazard-2018.
#
# @param $1 nshm {String}
#     The NSHM to download.
# @param $1 nshm_version {String}
#     The version to download from GitHub.
#
# @return String
#     The model path
# @status Integer
#     The result of downloading the repository.
####
get_model() {
  local nshm=${1};
  local nshm_version=${2};
  local model;
  local model_path;
  local url;
  local exit_status;

  if [ "${nshm_version}" == "null" ]; then
    return 0;
  fi

  case ${nshm} in
    "AK_2007")
      model="nshm-ak-2007";
      model_path="${model}";
      url="https://github.com/usgs/${model}.git";
      ;;
    "CEUS_2008")
      model="nshm-cous-2008";
      model_path="${model}/${CEUS}/";
      url="https://github.com/usgs/${model}.git";
      ;;
    "CEUS_2014")
      model="nshm-cous-2014";
      model_path="${model}/${CEUS}/";
      url="https://github.com/usgs/${model}.git";
      ;;
    "CEUS_2014B")
      model="nshm-cous-2014";
      model_path="${model}/${CEUS}/";
      nshm_version="${VERSION_2014B}";
      url="https://github.com/usgs/${model}.git";
      ;;
    "CEUS_2018")
      model="nshm-cous-2018";
      model_path="${model}/${CEUS}/";
      url="https://github.com/usgs/${model}.git";
      ;;
    "CONUS_2008")
      model="nshm-cous-2008";
      model_path="${model}";
      url="https://github.com/usgs/${model}.git";
      ;;
    "CONUS_2014")
      model="nshm-cous-2014";
      model_path="${model}";
      url="https://github.com/usgs/${model}.git";
      ;;
    "CONUS_2014B")
      model="nshm-cous-2014";
      model_path="${model}";
      nshm_version="${VERSION_2014B}";
      url="https://github.com/usgs/${model}.git";
      ;;
    "CONUS_2018")
      model="nshm-cous-2018";
      model_path="${model}";
      url="https://github.com/usgs/${model}.git";
      # model="nshm-conus-2018";
      # url="git@code.usgs.gov:ghsc/nshmp/nshm-conus-2018.git";
      ;;
    # "CONUS_2023")
    #   model="nshm-conus-2023";
    #   url="git@code.usgs.gov:ghsc/nshmp/nshm-conus-2023.git";
    #   ;;
    "HI_2020")
      model="nshm-hi-2020";
      model_path="${model}";
      url="https://github.com/usgs/${model}.git";
      ;;
    "WUS_2008")
      model="nshm-cous-2008";
      model_path="${model}/${WUS}/";
      url="https://github.com/usgs/${model}.git";
      ;;
    "WUS_2014")
      model="nshm-cous-2014";
      model_path="${model}/${WUS}/";
      url="https://github.com/usgs/${model}.git";
      ;;
    "WUS_2014B")
      model="nshm-cous-2014";
      model_path="${model}/${WUS}/";
      nshm_version="${VERSION_2014B}";
      url="https://github.com/usgs/${model}.git";
      ;;
    "WUS_2018")
      model="nshm-cous-2018";
      model_path="${model}/${WUS}/";
      url="https://github.com/usgs/${model}.git";
      ;;

    *)
      error_exit "Model [${nshm}] not supported" 1;
      ;;
  esac

  download_repo "${url}" "${nshm_version}";
  rm -rf "${model:?}/.git";
  exit_status=${?};

  echo "${model_path}";
  return ${exit_status}
}

####
# Returns the path to the model.
#
# @param $1 nshm {String}
#     The NSHM to download.
# @param $1 nshm_version {String}
#     The version to download from GitHub.
#
# @return String
#     The path to the model
# @status Integer
#     Status of get_model call
####
get_model_path() {
  local nshm=${1};
  local nshm_version=${2};
  local nshmp_model_path;
  local exit_status;
  nshmp_model_path=$(get_model "${nshm}" "${nshm_version}");
  exit_status=${?};

  echo "${nshmp_model_path}";
  return ${exit_status};
}

####
# Returns to nshmp-haz Java class to call.
#
# @param $1 program {String}
#     The program to run
#
# @return String
#     The program to call in nshmp-haz
####
get_nshmp_program() {
  local program=${1};
  local nshmp_program;

  case ${program} in
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
      error_exit "Program [${program}] not supported" 1;
      ;;
  esac

  echo "${nshmp_program}";
}

####
# Move artifacts to mounted volume.
#
# @param $1 config_file {String}
#     The config file
#
# @status Integer
#     The status of moving the files.
####
move_to_output_volume() {
  local config_file;
  local hazout;
  hazout=$(jq -r ".output.directory" "${config_file}");

  if [ "${hazout}" == null ]; then
    hazout="hazout";
  fi

  mv ${hazout}/* output/.;
  return ${?};
}
