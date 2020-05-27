#!/bin/bash

# exit on error, with error code
set -e

# can use the following to push to isda-registry for testing:
# BRANCH="master" SERVER=isda-registry.ncsa.illinois.edu/ ./release.sh

# use DEBUG=echo ./release.sh to print all commands
DEBUG=${DEBUG:-""}

# use SERVER=XYZ/ to push to a different server
SERVER=${SERVER:-""}

# what branch are we on
BRANCH=${BRANCH:-"$(git rev-parse --abbrev-ref HEAD)"}

# make sure docker is build
BRANCH=${BRANCH} VERSION=${TMPVERSION} DEBUG=${DEBUG} $(dirname $0)/docker.sh

# find out the version
if [ "${BRANCH}" = "master" ]; then
    VERSION=${VERSION:-""}
    if [ "${VERSION}" = "" ]; then
        TMPVERSION=$(awk '/version = / { print $4 }' $(dirname $0)/project/Build.scala | sed 's/"//g')
        echo "Detected version ${TMPVERSION}"
        VERSION="latest"
        OLDVERSION=""
        while [ "$OLDVERSION" != "$TMPVERSION" ]; do
            VERSION="${VERSION} ${TMPVERSION}"
            OLDVERSION="${TMPVERSION}"
            TMPVERSION=$(echo ${OLDVERSION} | sed 's/\.[0-9]*$//')
        done
    fi
elif [ "${BRANCH}" = "develop" ]; then
    VERSION="develop"
else
    exit 0
fi

# tag all images and push if needed
for i in clowder toolserver mongo-init monitor check; do
    for v in ${VERSION}; do
        if [ "$v" != "latest" -o "$SERVER" != "" ]; then
            ${DEBUG} docker tag clowder/${i}:latest ${SERVER}clowder/${i}:${v}
        fi
        ${DEBUG} docker push ${SERVER}clowder/${i}:${v}
    done
done
