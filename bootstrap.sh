#!/bin/bash

VERSION="3.3.1"

# options available:
# cerulean cosmo custom cyborg darkly flatly journal lumen paper
# readable sandstone simplex slate spacelab superhero united yeti
BOOTSWATCH="amelia cyborg journal paper readable sandstone simplex spacelab superhero united"

GLYPHICONS="glyphicons-halflings-regular.eot glyphicons-halflings-regular.svg glyphicons-halflings-regular.ttf glyphicons-halflings-regular.woff"

CDNJS="https://cdnjs.cloudflare.com/ajax/libs/"

# fetch bootstrap
echo -n "Fetching bootstrap.min.js ... "
curl -f -s -L -o tmpfile ${CDNJS}/twitter-bootstrap/${VERSION}/js/bootstrap.min.js
if [ $? == 0 ]; then
  mv tmpfile public/javascripts/bootstrap.min.js
  echo "OK"
else
  echo "ERROR"
fi
echo -n "Fetching bootstrap.min.css ... "
curl -f -s -L -o tmpfile ${CDNJS}/twitter-bootstrap/${VERSION}/css/bootstrap.min.css
if [ $? == 0 ]; then
  mv tmpfile public/stylesheets/themes/bootstrap.min.css
  echo "OK"
else
  echo "ERROR"
fi

# fetch glyphicons
for f in ${GLYPHICONS} ; do
  echo -n "Fetching ${f} ... "
  curl -f -s -L -o tmpfile ${CDNJS}/twitter-bootstrap/${VERSION}/fonts/$f
  if [ $? == 0 ]; then
    mv tmpfile public/stylesheets/fonts/${f}
    echo "OK"
  else
    echo "ERROR"
  fi
done

# fetch themes from bootswatch
for t in ${BOOTSWATCH}; do
  echo -n "Fetching ${t}.min.css ... "
  curl -f -s -L -o tmpfile ${CDNJS}/bootswatch/${VERSION}/${t}/bootstrap.min.css
  if [ $? == 0 ]; then
    mv tmpfile public/stylesheets/themes/${t}.min.css
    echo "OK"
  else
    echo "ERROR"
  fi
done
