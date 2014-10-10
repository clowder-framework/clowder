#!/bin/bash

# User to run servce as
USER=medici

# MMDB-WWW is the master branch
# MMDB-WWW1 is the develop branch
BRANCH=MMDB-WWW22

# change to folder where script is installed
cd $(dirname $0)

# install startup scripts
if [ ! -e /etc/init/medici.conf ]; then
  cat << EOF > /etc/init/medici.conf
# Medici Server
# this runs a medici as user ${USER}
# place this file in /etc/init
 
description "Medici Server"
 
start on runlevel [2345]
stop on runlevel [!2345]
 
kill timeout 30

respawn
 
script
    exec start-stop-daemon --start --chuid ${USER} --exec ${PWD}/medici-play-1.0-SNAPSHOT/bin/medici-play --name Medici
end script
EOF
fi

if [ ! -e /etc/init.d/medici ]; then
  ln -s /lib/init/upstart-job /etc/init.d/medici
  update-rc.d medici defaults
fi

# fetch software
/usr/bin/wget -N -q -O medici-play-1.0-SNAPSHOT.zip https://opensource.ncsa.illinois.edu/bamboo/browse/${BRANCH}/latest/artifact/JOB1/medici-dist/medici-play-1.0-SNAPSHOT.zip

if [ -s medici-play-1.0-SNAPSHOT.zip ]; then
  if [ medici-play-1.0-SNAPSHOT.zip -nt medici-play-1.0-SNAPSHOT ]; then
    echo "UPDATING MEDICI TO NEWER VERSION"

    /sbin/stop medici

    /bin/rm -rf medici-play-1.0-SNAPSHOT
    /usr/bin/unzip -q medici-play-1.0-SNAPSHOT.zip
    /usr/bin/touch medici-play-1.0-SNAPSHOT

    # change permissions
    /bin/chown -R ${USER} medici-play-1.0-SNAPSHOT

    /sbin/start medici
  fi
fi
