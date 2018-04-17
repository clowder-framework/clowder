#!/bin/sh

# exit on error, with error code
set -e

# use DEBUG=echo ./release.sh to print all commands
export DEBUG=${DEBUG:-""}

${DEBUG} docker build --tag clowder/clowder:latest .
${DEBUG} docker build --tag clowder/toolserver:latest scripts/toollaunchservice
${DEBUG} docker build --tag clowder/mongo-init:latest scripts/mongo-init
