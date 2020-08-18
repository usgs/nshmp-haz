####
# Dockerfile for nshmp-haz.
#
# Usage:
#   docker run \
#       -e PROGRAM=<deagg | deagg-epsilon | deagg-iml | hazard | hazard-2018 | rate> \
#       -e MODEL=<WUS-20[08|14|18] | CEUS-20[08|14|18] | COUS-20[08|14|18] | AK-2007> \
#       -e ACCESS_VISUALVM=<true | false> \
#       -e VISUALVM_PORT=<port> \
#       -e VISUALVM_HOSTNAME=<hostname> \
#       -v /absolute/path/to/sites/file:/app/sites.<geojson | csv> \
#       -v /absolute/path/to/config/file:/app/config.json \
#       -v /absolute/path/to/output:/app/output \
#       usgs/nshmp-haz
#
# Usage with custom model:
#   docker run \
#       -e PROGRAM=<deagg | deagg-epsilon | deagg-iml | hazard | hazard-2018 | rate> \
#       -e ACCESS_VISUALVM=<true | false> \
#       -e VISUALVM_PORT=<port> \
#       -e VISUALVM_HOSTNAME=<hostname> \
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
FROM usgs/java:11 as builder

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
FROM usgs/java:11

# Set author
LABEL maintainer="Peter Powers <pmpowers@usgs.gov>"

# Set working directory
WORKDIR /app

# Install file and jq
RUN yum update -y
RUN yum install -y file epel-release
RUN yum install -y jq

# Get JAR path
ARG jar_path

# Get builder working directory
ARG builder_workdir

# Copy JAR file from builder image
COPY --from=builder ${jar_path} .

# Copy entrypoint script
COPY docker-entrypoint.sh .

# NSHM repository version
ENV NSHM_VERSION=master

# Set Java memory
ENV JAVA_XMS 2g
ENV JAVA_XMX 8g

# NSHM
ENV MODEL ""

# Whether to mount the model instead of selecting a model
ENV MOUNT_MODEL false

# Program to run: deagg | deagg-epsilon | hazard | rate
ENV PROGRAM hazard

# Return period for deagg
ENV RETURN_PERIOD ""

# Intensity measure level (in units of g) for deagg-iml
ENV IML ""

# Optional config file
ENV CONFIG_FILE "config.json"

# Whether to have access to Java VisualVM
ENV ACCESS_VISUALVM false

# Port for Java VisualVM
ENV VISUALVM_PORT 9090

# Java VisualVM hostname
ENV VISUALVM_HOSTNAME localhost

# Set volume for output
VOLUME [ "/app/output" ]

# Create empty config file
RUN echo "{}" > ${CONFIG_FILE}

# Run nshmp-haz
ENTRYPOINT [ "bash", "docker-entrypoint.sh" ]

# Expose Java VisualVM port
EXPOSE ${VISUALVM_PORT}
