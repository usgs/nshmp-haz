#!/bin/bash
# shellcheck disable=SC1090
# shellcheck disable=SC2034

# Prevent configuration from being included multiple times
[ -z "${CONFIGURATION_COMPLETE}" ] || return;
source "$(dirname "$0")/docker-functions.inc.sh";

readonly DEBUG="${DEBUG:-false}";

# Turn on debugging if desired. Do this first so each value is echo'd
if [[ "${DEBUG}" == "true" ]]; then
  set -x;
fi

readonly CEUS="Central & Eastern US";
readonly CONFIG_FILE="${CONFIG_FILE:-config.json}";
readonly JAVA_XMX="${JAVA_XMX:-8g}";
readonly MODEL=$(echo "${MODEL:-CONUS_2008}"  | awk \{'print toupper($0)'\});
readonly NSHM_VERSION="${NSHM_VERSION:-main}";
readonly PROJECT="${PROJECT:-nshmp-haz}";
readonly PROGRAM=$(echo "${PROGRAM:-hazard}" | awk \{'print tolower($0)'\});
readonly WUS="Western US";
readonly VERSION_2014B="v4.1.1";

# Include guard to prevent accidental re-configuration
CONFIGURATION_COMPLETE="true";
