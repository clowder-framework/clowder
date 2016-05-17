#!/bin/bash

# CATS-CORE is the main branch for this server
CLOWDER_BRANCH=${CLOWDER_BRANCH:-"CATS-CORE"}
CLOWDER_BUILD=${CLOWDER_BUILD:-"latestSuccessful"}

# change to folder where script is installed
cd /home/ubuntu

# fetch software
if [[ ${CLOWDER_BUILD} == latest* ]]; then
  BB="${CLOWDER_BRANCH}/${CLOWDER_BUILD}"
else
  BB="${CLOWDER_BRANCH}-${CLOWDER_BUILD}"
fi
URL="https://opensource.ncsa.illinois.edu/bamboo/browse/${BB}/artifact/JOB1/dist/"
/usr/bin/wget -q -e robots=off -A "clowder-*.zip" -nd -r -N -l1 ${URL}
LATEST=$( /bin/ls -1rt clowder-*.zip | tail -1 )

if [ -s ${LATEST} ]; then
  if [ "$1" == "--force" -o ${LATEST} -nt clowder ]; then
    echo "UPDATING CLOWDER ON ${HOSTNAME}"
    echo " bamboo  branch  = ${CLOWDER_BRANCH}"

    # stop clowder
    /sbin/stop clowder

    # save local modifications
    if [ -d clowder/custom ]; then
      mv clowder/custom clowder.custom
    fi

    # install new version
    /bin/rm -rf clowder $( basename ${LATEST} .zip )
    /usr/bin/unzip -q ${LATEST}
    /bin/mv $( basename ${LATEST} .zip ) clowder
    /usr/bin/touch clowder

    # get some nice values from the build
    echo " bamboo  build   = $( grep '\-Dbuild.bamboo' clowder/bin/clowder | sed 's/.*=\(.*\)"$/\1/' )"
    echo " clowder version = $( grep '\-Dbuild.version' clowder/bin/clowder | sed 's/.*=\(.*\)"$/\1/' )"
    echo " clowder branch  = $( grep '\-Dbuild.branch' clowder/bin/clowder | sed 's/.*=\(.*\)"$/\1/' )"
    echo " clowder gitsha1 = $( grep '\-Dbuild.gitsha1' clowder/bin/clowder | sed 's/.*=\(.*\)"$/\1/' )"

    # restore local modifications
    if [ -d clowder.custom ]; then
      mv clowder.custom clowder/custom
    fi

    # change permissions
    /bin/chown -R ubuntu clowder

    # run custom code pieces
    if [ -e update-clowder-custom.sh ]; then
      ./update-clowder-custom.sh
    fi

    # start clowder again
    /sbin/start clowder
  fi
fi
