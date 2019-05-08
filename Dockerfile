################################
# Dockerfile for nshmp-haz.
#
# Note: Models load as requested. While all supported models are
# available, requesting them all will eventually result in an
# OutOfMemoryError. Increase -Xmx to -Xmx16g or -Xmx24g, if available.
################################

# Project
ARG project=nshmp-haz

# Builder image working directory
ARG builder_workdir=/app/${project}

# Path to JAR file in builder image
ARG jar_path=${builder_workdir}/build/libs/${project}.jar

####
# Builder Image: Java 8
#   - Install git, curl, and bash
#   - Download models (docker-builder-entrypoint.sh)
#   - Build nshmp-haz
####
FROM openjdk:8-alpine as builder

# Get builder workdir
ARG builder_workdir

# Repository versions
ARG NSHM_COUS_2018_VERSION=master
ARG NSHM_COUS_2014_VERSION=master
ARG NSHM_COUS_2008_VERSION=master
ARG NSHM_AK_2007_VERSION=master

# Set working directory
WORKDIR ${builder_workdir} 

# Copy project over to container
COPY . ${builder_workdir}/. 

# Install git, curl, and bash
RUN apk add --no-cache git curl bash

# Build nshmp-haz
RUN ./gradlew assemble

# Change working directory
WORKDIR ${builder_workdir}/models

# Download models
RUN bash ${builder_workdir}/docker-builder-entrypoint.sh

####
# Application Image: Java 8 in usgs/centos image
#   - Install jq
#   - Copy JAR file from builder image
#   - Run nshmp-haz
####
FROM usgsnshmp/nshmp-openjdk:jdk8

# Set author
LABEL maintainer="Peter Powers <pmpowers@usgs.gov>"

# Set working directory
WORKDIR /app

# Install file and jq
RUN yum install -y add file epel-release
RUN yum install -y jq

# Get JAR path
ARG jar_path

# Get builder working directory
ARG builder_workdir

# Copy JAR file from builder image
COPY --from=builder ${jar_path} .

# Copy models
COPY --from=builder ${builder_workdir}/models .

# Copy entrypoint script
COPY docker-entrypoint.sh .

# Set Java memory
ENV JAVA_XMS 1g
ENV JAVA_XMX 8g

# NSHM
ENV MODEL ""

# Program to run: deagg | deagg-epsilon | hazard | rate
ENV PROGRAM hazard

# Return period for deagg
ENV RETURN_PERIOD ""

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
