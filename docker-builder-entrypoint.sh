#!/bin/bash

####
# Docker entrypoint for nshmp-haz builder image.
#
# Download all required USGS repositories to run 
# nshmp-haz in a Docker container.
####

####
# Download a USGS repository from Github.
# Arguments:
#   (string) repo - The project to download
#   (string) version - The version to download
# Returns:
#   None
####
download_repo() {
  local repo=${1}
  local version=${2}
  local url="https://github.com/usgs/${repo}/archive/${version}.tar.gz"

  printf "\n Downloading [${url}] \n"
  curl -L ${url} | tar -xz
  mv ${repo}-${version#v*} ${repo}
}

# Download nshm-ak-2007
download_repo "nshm-ak-2007" ${NSHM_AK_2007_VERSION}

# Download nshm-cous-2008
download_repo "nshm-cous-2008" ${NSHM_COUS_2008_VERSION}

# Download nshm-cous-2014
download_repo "nshm-cous-2014" ${NSHM_COUS_2014_VERSION}

# Download nshm-cous-2018
download_repo "nshm-cous-2018" ${NSHM_COUS_2018_VERSION}
