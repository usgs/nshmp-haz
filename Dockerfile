####
# Dockerfile for nshmp-haz.
#
# Usage:
#   docker run \
#       -e PROGRAM=<deagg | deagg-epsilon | deagg-iml | hazard | hazard-2018 | rate> \
#       -e MODEL=<WUS-20[08|14|18] | CEUS-20[08|14|18] | COUS-20[08|14|18] | AK-2007> \
#       -v /absolute/path/to/sites/file:/app/sites.<geojson | csv> \
#       -v /absolute/path/to/config/file:/app/config.json \
#       -v /absolute/path/to/output:/app/output \
#       usgs/nshmp-haz
#
# Usage with custom model:
#   docker run \
#       -e PROGRAM=<deagg | deagg-epsilon | deagg-iml | hazard | hazard-2018 | rate> \
#       -e MOUNT_MODEL=true \
#       -v /absolute/path/to/model:/app/model \
#       -v /absolute/path/to/sites/file:/app/sites.<geojson | csv> \
#       -v /absolute/path/to/config/file:/app/config.json \
#       -v /absolute/path/to/output:/app/output \
#       usgs/nshmp-haz
#
# Note: Models load as requested. While all supported models are
# available, requesting them all will eventually result in an
# OutOfMemoryError. Increase -Xmx to -Xmx16g or -Xmx24g, if available.
####

ARG BUILD_IMAGE=usgs/amazoncorretto:8
ARG FROM_IMAGE=usgs/amazoncorretto:8

# Project
ARG project=nshmp-haz

# Builder image working directory
ARG builder_workdir=/app/${project}

# Path to JAR file in builder image
ARG jar_path=${builder_workdir}/build/libs/${project}.jar

####
# Builder Image: Java 11
#   - Install git
#   - Build nshmp-haz
####
FROM ${BUILD_IMAGE} as builder

# Get builder workdir
ARG builder_workdir

# Set working directory
WORKDIR ${builder_workdir}

# Copy project over to container
COPY . ${builder_workdir}/.

# Build nshmp-haz
RUN ./gradlew assemble

####
# Application Image: Java 11
#   - Install jq
#   - Copy JAR file from builder image
#   - Download model
#   - Run nshmp-haz (docker-entrypoint.sh)
####
FROM ${FROM_IMAGE}

LABEL maintainer="Peter Powers <pmpowers@usgs.gov>"

WORKDIR /app

RUN yum update -y
RUN yum install -y file epel-release
RUN yum install -y jq

ARG jar_path
ARG builder_workdir

COPY --from=builder ${jar_path} .
COPY scripts scripts

ENV CONFIG_FILE ""
ENV DEBUG false
ENV IML ""
ENV JAVA_XMX "8g"
ENV LANG "en_US.UTF-8"
ENV MODEL ""
ENV MOUNT_MODEL false
ENV NSHM_VERSION master
ENV PROGRAM hazard
ENV PROJECT ${project}
ENV RETURN_PERIOD ""

VOLUME [ "/app/output" ]

# Run nshmp-haz
ENTRYPOINT [ "bash", "scripts/docker-entrypoint.sh" ]
