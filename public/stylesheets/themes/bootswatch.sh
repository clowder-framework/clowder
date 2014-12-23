#!/bin/bash

THEMES="amelia cyborg journal paper readable sandstone simplex spacelab superhero united"

VERSION="3.3.1"
REPO="https://cdnjs.cloudflare.com/ajax/libs/bootswatch/"

for t in ${THEMES}; do
  echo -n "Fetching ${t} ... "
  curl -f -s -L -o tmp.min.css ${REPO}/${VERSION}/${t}/bootstrap.min.css
  if [ $? == 0 ]; then
    mv tmp.min.css ${t}.min.css
    echo "OK"
  else
    echo "ERROR"
  fi
done
