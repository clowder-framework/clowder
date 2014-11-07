# Installing Medici 2

This assumes you are using Ubuntu (debian might work), and are logged in as root. If not you can use `sudo -s` to become root.

Make sure machine is up to date.

```bash
apt-get -y update
apt-get -y dist-upgrade
```

## Medici2 Requirements

First we need to install the requirements for Medici, java, rabbitmq and mongodb. The latter 2 are not required to be installed on the same machine, you can modify the medici 2 configuration to point to those servers.

```bash
# setup repository for rabbitMQ
echo "deb http://www.rabbitmq.com/debian/ testing main" > /etc/apt/sources.list.d/rabbitmq.list
wget http://www.rabbitmq.com/rabbitmq-signing-key-public.asc
apt-key add rabbitmq-signing-key-public.asc
rm rabbitmq-signing-key-public.asc
apt-get -y update
apt-get -y install rabbitmq-server
mkdir /etc/rabbitmq/rabbitmq.conf.d
rabbitmq-plugins enable rabbitmq_management

# setup repository for MongoDB
echo "deb http://downloads-distro.mongodb.org/repo/ubuntu-upstart dist 10gen" > /etc/apt/sources.list.d/mongo.list
apt-key adv --keyserver keyserver.ubuntu.com --recv 7F0CEB10
apt-get -y update
apt-get -y install mongodb-org

# install software
# easiest for postfix is to select internet site
apt-get -y update
apt-get -y install unzip openjdk-7-jre-headless postfix
```

## Medici2 Scripts

First create a user that will run the medici software

```
# create a user for medici
adduser --system --group users --disabled-login medici
```

Create the scripts to automatically start/stop medici

```bash
cat << EOF > /etc/init/medici.conf
# Medici Server
# this runs a medici as user medici
 
description "Medici Server"
 
start on runlevel [2345]
stop on runlevel [!2345]
 
kill timeout 30
respawn

pre-start script
  if [ -f /home/medici/medici-play-1.0-SNAPSHOT/RUNNING_PID ] ; then
  	if ps -p `cat /home/medici/medici-play-1.0-SNAPSHOT/RUNNING_PID` > /dev/null ; then
      echo "Found running version, killing old version"
      kill `cat /home/medici/medici-play-1.0-SNAPSHOT/RUNNING_PID`
    fi
    rm /home/medici/medici-play-1.0-SNAPSHOT/RUNNING_PID
  fi
end script

script
  exec start-stop-daemon --start --chuid medici --exec /home/medici/medici-play-1.0-SNAPSHOT/bin/medici-play --name Medici -- -Dhttp.port=9000
end script
EOF

ln -s /lib/init/upstart-job /etc/init.d/medici
update-rc.d medici defaults
```

Next we install the script that will install or update medici.

```bash
cat > /home/browndog/update-medici.sh << EOF
#!/bin/bash

# MMDB-WWW (master) is the main branch for this server
# MMDB-WWW1 (develop) if you want the latest version
MEDICI2_BRANCH=${MEDICI2_BRANCH:-"MMDB-WWW1"}

# change to folder where script is installed
cd /home/medici

# fetch software
/usr/bin/wget -N -q -O medici-play-1.0-SNAPSHOT.zip https://opensource.ncsa.illinois.edu/bamboo/browse/${MEDICI2_BRANCH}/latest/artifact/JOB1/medici-dist/medici-play-1.0-SNAPSHOT.zip

if [ -s medici-play-1.0-SNAPSHOT.zip ]; then
  if [ medici-play-1.0-SNAPSHOT.zip -nt medici-play-1.0-SNAPSHOT ]; then
    echo "UPDATING MEDICI2 TO NEWER VERSION"

    /sbin/stop medici2

    /bin/rm -rf medici-play-1.0-SNAPSHOT
    /usr/bin/unzip -q medici-play-1.0-SNAPSHOT.zip
    /usr/bin/touch medici-play-1.0-SNAPSHOT

    # local modifications
    /bin/sed -i 's/app_classpath="/app_classpath="$conf_dir:/' medici-play-1.0-SNAPSHOT/bin/medici-play
    /usr/bin/patch -R medici-play-1.0-SNAPSHOT/bin/medici-play medici2.diff
    if [ -e application.conf ]; then
      /bin/cp application.conf medici-play-1.0-SNAPSHOT/conf
    fi
    if [ -e securesocial.conf ]; then
      /bin/cp securesocial.conf medici-play-1.0-SNAPSHOT/conf
    fi
    if [ -e play.plugins ]; then
      /bin/cp play.plugins medici-play-1.0-SNAPSHOT/conf
    else
      /bin/rm medici-play-1.0-SNAPSHOT/conf/play.plugins
    fi

    # change permissions
    /bin/chown -R medici medici-play-1.0-SNAPSHOT

    /sbin/start medici2
  fi
fi
EOF
chmod 755 /home/medici/update-medici.sh
```

A small patch to the medici install, to allow for local changes

```
cat > /home/medici/medici.diff << EOF
290d289
< declare -r conf_dir="\$(realpath "\${app_home}/../conf")"
EOF
```

Get the latest version of the application.conf so you can modify it. This is not required and it will use the defaults, best is to change modify them.

```
wget -O /home/medici/application.conf "https://opensource.ncsa.illinois.edu/stash/projects/MMDB/repos/medici-play/browse/conf/application.conf?at=develop&raw"
wget -O /home/medici/securesocial.conf "https://opensource.ncsa.illinois.edu/stash/projects/MMDB/repos/medici-play/browse/conf/securesocial.conf?at=develop&raw"
```

Common things you might want to modify in application.con are:

- hostIp : set this to the fully qualified hostname.
- permissions : public or private server.
- initialAdmins and registerThroughAdmins :  if you want to prevent anybody from signing up.
- application.context : if you have a web server running in front of medici.
- commKey and application.secret : change these since the defaults are not secure.

Things to change in securesocial.conf are:

- smtp.from : set this to the user all email should come from.
- onLoginGoTo and onLogoutGoTo : prefix the path with the same path as used for application.context, default assumes root context.
- ssl : set this to true if you run on https (which you really should).

Finally install medici using the update script. You can from now on, just call this script to install the latest version of medici.

```
/home/medici/update-medici.sh
```

You can add a cron entry that runs to call this script. It will only update this instance of medici if a newer version is available. This will make it so you are always running the latest version.

# Medic2 Extractors

To get people started to create their own extractors we will add the example extractors, as well as the required software to compile these.

## Medici2 Extractors Requirements

First we will install all the required software to compile these plugins.

```bash
sudo -s

# install python requirements
apt-get -y install python-pip
pip install pika requests

# install java requirements
apt-get -y install openjdk-7-jdk
wget http://mirror.metrocast.net/apache/maven/maven-3/3.2.2/binaries/apache-maven-3.2.2-bin.tar.gz
tar zxvf apache-maven-3.2.2-bin.tar.gz -C /usr/local
rm apache-maven-3.2.2-bin.tar.gz
ln -s /usr/local/apache-maven-3.2.2/bin/mvn /usr/local/bin

# install git to get extractors
apt-get -y install git

# done as root
exit
```

## Medici2 Example Extractors

This will install and compile the example extractors

```bash
# clone extractors
cd
git clone https://opensource.ncsa.illinois.edu/stash/scm/mmdb/extractors-examples.git

# compile java code
cd extractors-examples/java
mvn package
```
