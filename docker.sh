#!/bin/sh

# exit on error, with error code
set -e

# use newer docker build options
export DOCKER_BUILDKIT=1

# set some defaults
DEBUG=${DEBUG:-""}
BRANCH=${BRANCH:-"$(git rev-parse --abbrev-ref HEAD)"}
VERSION=${VERSION:-"$(awk '/version = / { print $4 }' $(dirname $0)/project/Build.scala | sed 's/\"//g')"}
BUILDNUMBER=${bamboo_buildNumber:-local}
GITSHA1=${GITSHA1:-"$(git rev-parse --short HEAD)"}

${DEBUG} docker build --tag clowder/clowder:latest \
  --build-arg BRANCH=${BRANCH} \
  --build-arg VERSION=${VERSION} \
  --build-arg BUILDNUMBER=${BUILDNUMBER} \
  --build-arg GITSHA1=${GITSHA1} \
  .
${DEBUG} docker build --tag clowder/toolserver:latest \
  --build-arg BRANCH=${BRANCH} \
  --build-arg VERSION=${VERSION} \
  --build-arg BUILDNUMBER=${BUILDNUMBER} \
  --build-arg GITSHA1=${GITSHA1} \
  scripts/toollaunchservice
${DEBUG} docker build --tag clowder/mongo-init:latest \
  --build-arg BRANCH=${BRANCH} \
  --build-arg VERSION=${VERSION} \
  --build-arg BUILDNUMBER=${BUILDNUMBER} \
  --build-arg GITSHA1=${GITSHA1} \
  scripts/mongo-init
${DEBUG} docker build --tag clowder/monitor:latest \
  --build-arg BRANCH=${BRANCH} \
  --build-arg VERSION=${VERSION} \
  --build-arg BUILDNUMBER=${BUILDNUMBER} \
  --build-arg GITSHA1=${GITSHA1} \
  scripts/monitor
