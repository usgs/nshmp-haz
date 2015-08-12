#!/bin/bash

# TODO not sure this is needed in light of ant based jar builder

# Run this script from the root of a project

# Assuming Java (is available (javac specifically), this script will
# create a temporary list of files to compile, compile the files, and
# remove the list

if [ -d "classes" ]; then
    rm -R classes;
    mkdir classes
fi

find src -name "*.java" > sources.txt
javac -d classes -cp "lib/*" @sources.txt
rm sources.txt

# move required resources to 'classes' directory
cp -r src/org/opensha/gmm/coeffs classes/org/opensha/gmm/coeffs
cp -r src/org/opensha/gmm/etc classes/org/opensha/gmm/etc
cp -r src/org/opensha/gmm/tables classes/org/opensha/gmm/tables

