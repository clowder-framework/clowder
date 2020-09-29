#!/bin/bash

# what version ("" == latest release)
CLOWDER_BRANCH=${CLOWDER_BRANCH:-}
#CLOWDER_BRANCH="develop"

# compute some other variables
if [ "${CLOWDER_BRANCH}" = "" ]; then
  CLOWDER_VERSION=$(curl -s  https://opensource.ncsa.illinois.edu/projects/artifacts.php?key=CATS | grep '<option selected>' | sed 's#.*<option.*>\([0-9\.]*\)</option>#\1#')
  CLOWDER_ZIPFILE=clowder-${CLOWDER_VERSION}.zip
else
  CLOWDER_VERSION="${CLOWDER_BRANCH}"
  CLOWDER_ZIPFILE=clowder-${CLOWDER_VERSION}-${CLOWDER_BRANCH}.zip
fi

# Should text go to stdout, empty is not
STDOUT="YES"

# HipChat token for notifications
HIPCHAT_TOKEN=""
HIPCHAT_ROOM=""

# Slack token for notifications
SLACK_TOKEN=""
SLACK_CHANNEL="#simpl-ops"

# MSTeams Webhook
MSTEAMS_URL=""

# INFLUXDB
INFLUXDB_URL=""
INFLUXDB_DATABASE=""
INFLUXDB_USERNAME=""
INFLUXDB_PASSWORD=""

# change to folder where script is installed
cd /home/clowder

# fetch software
if [ -e ]; then
  curl -f -s -z "${CLOWDER_ZIPFILE}" -o "${CLOWDER_ZIPFILE}" https://opensource.ncsa.illinois.edu/projects/artifacts/CATS/${CLOWDER_VERSION}/files/${CLOWDER_ZIPFILE} || exit 1
else
  curl -f -s -o "${CLOWDER_ZIPFILE}" https://opensource.ncsa.illinois.edu/projects/artifacts/CATS/${CLOWDER_VERSION}/files/${CLOWDER_ZIPFILE} || exit 1
fi

if [ -s ${CLOWDER_ZIPFILE} ]; then
  if [ "$1" == "--force" -o ${CLOWDER_ZIPFILE} -nt clowder ]; then
    exec 3>&1
    exec &> "/tmp/$$.txt"

    echo "UPDATING CLOWDER ON ${HOSTNAME}"
    echo " bamboo  branch  = ${CLOWDER_BRANCH}"

    # stop clowder
    /bin/systemctl stop clowder

    # save local modifications
    if [ -d clowder/custom ]; then
      mv clowder/custom clowder.custom
    fi
    if [ -d clowder/logs ]; then
      mv clowder/logs clowder.logs
    fi

    # install new version
    /bin/rm -rf clowder $( basename ${CLOWDER_ZIPFILE} .zip )
    /usr/bin/unzip -q ${CLOWDER_ZIPFILE}
    /bin/mv $( basename ${CLOWDER_ZIPFILE} .zip ) clowder
    /usr/bin/touch clowder

    # get some nice values from the build
    echo " github  build   = $( grep '\-Dbuild.bamboo' clowder/bin/clowder | sed 's/.*=\(.*\)"$/\1/' )"
    echo " clowder version = $( grep '\-Dbuild.version' clowder/bin/clowder | sed 's/.*=\(.*\)"$/\1/' )"
    echo " clowder branch  = $( grep '\-Dbuild.branch' clowder/bin/clowder | sed 's/.*=\(.*\)"$/\1/' )"
    echo " clowder gitsha1 = $( grep '\-Dbuild.gitsha1' clowder/bin/clowder | sed 's/.*=\(.*\)"$/\1/' )"

    # restore local modifications
    if [ -d clowder.logs ]; then
      mv clowder.logs clowder/logs
    fi
    if [ -d clowder.custom ]; then
      mv clowder.custom clowder/custom
    fi

    # change permissions
    /bin/chown -R clowder clowder

    # run custom code pieces
    if [ -e update-clowder-custom.sh ]; then
      ./update-clowder-custom.sh
    fi

    # start clowder again
    /bin/systemctl start clowder

    # send message by hipchat
    if [ "${HIPCHAT_TOKEN}" != "" -a "${HIPCHAT_ROOM}" != "" ]; then
      url="https://hipchat.ncsa.illinois.edu/v1/rooms/message?format=json&auth_token=${HIPCHAT_TOKEN}"
      txt=$(cat /tmp/$$.txt | sed 's/ /%20/g;s/!/%21/g;s/"/%22/g;s/#/%23/g;s/\$/%24/g;s/\&/%26/g;s/'\''/%27/g;s/(/%28/g;s/)/%29/g;s/:/%3A/g;s/$/<br\/>/g')
      room=$(echo ${HIPCHAT_ROOM} | sed 's/ /%20/g;s/!/%21/g;s/"/%22/g;s/#/%23/g;s/\$/%24/g;s/\&/%26/g;s/'\''/%27/g;s/(/%28/g;s/)/%29/g;s/:/%3A/g')
      body="room_id=${room}&from=clowder&message=${txt}"
      result=$(curl -s -X POST -d "${body}" $url)
    fi
    if [ "${SLACK_TOKEN}" != "" -a "${SLACK_CHANNEL}" != "" ]; then
      url="https://hooks.slack.com/services/${SLACK_TOKEN}"
      txt=$(cat /tmp/$$.txt | sed 's/"/\\"/g;s/$/\\/g' | tr '\n' 'n' )
      payload="payload={\"channel\": \"${SLACK_CHANNEL}\", \"username\": \"clowder\", \"text\": \"${txt}\", \"icon_url\": \"https://opensource.ncsa.illinois.edu/projects/artifacts/CATS/logo.png\"}"
      result=$(curl -s -X POST --data-urlencode "${payload}" $url)
    fi
    if [ "${MSTEAMS_URL}" != "" ]; then
      txt=$(cat /tmp/$$.txt | sed 's/"/\\"/g;s/$/\\/g' | tr '\n' 'n' )
      payload="{\"title\": \"UPDATE CLOWDER\", \"text\": \"${txt}\" }"
      result=$(curl -s -X POST -H "Content-Type: application/json" -d "${payload}" $MSTEAMS_URL)
    fi
    if [ "${INFLUXDB_URL}" != "" -a "${INFLUXDB_DATABASE}" != "" -a "${INFLUXDB_USERNAME}" != "" -a "${INFLUXDB_PASSWORD}" != "" ]; then
      url="${INFLUXDB_URL}/api/v2/write?bucket=${INFLUXDB_DATABASE}&precision=ns"
      tags="host=${HOSTNAME}"
      timestamp="$(date -u +"%s%N")"
      values="version=\"$( grep '\-Dbuild.version' clowder/bin/clowder | sed 's/.*=\(.*\)"$/\1/' )\""
      values="${values},branch=\"$( grep '\-Dbuild.branch' clowder/bin/clowder | sed 's/.*=\(.*\)"$/\1/' )\""
      values="${values},build=\"$( grep '\-Dbuild.bamboo' clowder/bin/clowder | sed 's/.*=\(.*\)"$/\1/' )\""
      values="${values},gitsha1=\"$( grep '\-Dbuild.gitsha1' clowder/bin/clowder | sed 's/.*=\(.*\)"$/\1/' )\""
      auth="Authorization: Token ${INFLUXDB_USERNAME}:${INFLUXDB_PASSWORD}"
      result=$(curl -s -i -XPOST "$url" --header "$auth" --data "clowder_update,${tags} ${values} ${timestamp}")
    fi
    if [ "${STDOUT}" != "" ]; then
      cat /tmp/$$.txt >&3
    fi
    rm /tmp/$$.txt
  fi
fi
