#!/bin/sh

# variables that can be set
# DEBUG   : set to echo to print command and not execute

PROJECT=${PROJECT:-"browndog"}

mkdir -p docker/files
${DEBUG} cp entrypoint.sh passwd supervisord.conf docker/files

${DEBUG} docker build -t $PROJECT/create-useraccount docker
docker push $PROJECT/create-useraccount:latest

#Cleanup
rm -rf docker/files
