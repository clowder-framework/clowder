# Installing Clowder

This assumes you are using Ubuntu (debian might work), and are logged in as root. If not you can use `sudo -s` to become root.

Make sure machine is up to date.

```bash
apt-get -y update
apt-get -y dist-upgrade
```

## clowder Requirements

First we need to install the requirements for clowder, java, rabbitmq and mongodb. The latter 2 are not required to be installed on the same machine, you can modify the clowder configuration to point to those servers.

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

## clowder Scripts

First create a user that will run the clowder software

```
# create a user for clowder
adduser --system --group users --disabled-login clowder
```

Create the scripts to automatically start/stop clowder

```bash
cat << EOF > /etc/init/clowder.conf
# clowder Server
# this runs a clowder as user clowder
 
description "clowder Server"
 
start on runlevel [2345]
stop on runlevel [!2345]
 
kill timeout 30
respawn

pre-start script
  if [ -f /home/clowder/clowder-2.0-SNAPSHOT/RUNNING_PID ] ; then
  	if ps -p `cat /home/clowder/clowder-2.0-SNAPSHOT/RUNNING_PID` > /dev/null ; then
      echo "Found running version, killing old version"
      kill `cat /home/clowder/clowder-2.0-SNAPSHOT/RUNNING_PID`
    fi
    rm /home/clowder/clowder-2.0-SNAPSHOT/RUNNING_PID
  fi
end script

script
  exec start-stop-daemon --start --chuid clowder --exec /home/clowder/clowder-2.0-SNAPSHOT/bin/clowder-play --name clowder -- -Dhttp.port=9000
end script
EOF

ln -s /lib/init/upstart-job /etc/init.d/clowder
update-rc.d clowder defaults
```

Next we install the script that will install or update clowder.

```bash
cat > /home/browndog/update-clowder.sh << EOF
#!/bin/bash

# CATS-WWW (master) is the main branch for this server
# CATS-WWW1 (develop) if you want the latest version
clowder_BRANCH=${clowder_BRANCH:-"CATS-WWW1"}

# change to folder where script is installed
cd /home/clowder

# fetch software
/usr/bin/wget -N -q -O clowder-2.0-SNAPSHOT.zip https://opensource.ncsa.illinois.edu/bamboo/browse/${clowder_BRANCH}/latest/artifact/JOB1/clowder-dist/clowder-2.0-SNAPSHOT.zip

if [ -s clowder-2.0-SNAPSHOT.zip ]; then
  if [ clowder-2.0-SNAPSHOT.zip -nt clowder-2.0-SNAPSHOT ]; then
    echo "UPDATING clowder TO NEWER VERSION"

    /sbin/stop clowder

    /bin/rm -rf clowder-2.0-SNAPSHOT
    /usr/bin/unzip -q clowder-2.0-SNAPSHOT.zip
    /usr/bin/touch clowder-2.0-SNAPSHOT

    # local modifications
    /bin/sed -i 's/app_classpath="/app_classpath="$conf_dir:/' clowder-2.0-SNAPSHOT/bin/clowder-play
    /usr/bin/patch -R clowder-2.0-SNAPSHOT/bin/clowder-play clowder.diff
    if [ -e application.conf ]; then
      /bin/cp application.conf clowder-2.0-SNAPSHOT/conf
    fi
    if [ -e securesocial.conf ]; then
      /bin/cp securesocial.conf clowder-2.0-SNAPSHOT/conf
    fi
    if [ -e play.plugins ]; then
      /bin/cp play.plugins clowder-2.0-SNAPSHOT/conf
    else
      /bin/rm clowder-2.0-SNAPSHOT/conf/play.plugins
    fi

    # change permissions
    /bin/chown -R clowder clowder-2.0-SNAPSHOT

    /sbin/start clowder
  fi
fi
EOF
chmod 755 /home/clowder/update-clowder.sh
```

## clowder Customization

Any customization for clowder should be placed in the folder custom. The following files/folders are currently supported:

custom/custom.conf:
This file allows you to override any settings in conf/*.conf. If you make any changes to this file you will need to restart clowder. For example you can use this to change the permissions of this clowder instance from public to private, and have an admin approve new users  by adding the following to custom.conf:
```
permissions=private
initialAdmins="<your-email-address>"
registerThroughAdmins=true
```

Common things you might want to modify in custom.conf are:

- hostIp : set this to the fully qualified hostname.
- permissions : public or private server.
- initialAdmins and registerThroughAdmins :  if you want to prevent anybody from signing up.
- application.context : if you have a web server running in front of clowder.
- commKey and application.secret : change these since the defaults are not secure.
- smtp.from : set this to the user all email should come from.
- securesocial.onLoginGoTo and securesocial.onLogoutGoTo : prefix the path with the same path as used for application.context, default assumes root context.
- securesocial.ssl : set this to true if you run on https (which you really should).


custom/public/stylesheets/themes/:
This folder can contain any custom stylesheets you want to use in clowder. These themes will be applied to the whole system. The themes are based on the bootstrap them. You can place new files in this folder and the system will find them without a restart.

custom/public/javascript/previewers/:
This folder can contain any custom previewers you want to use in clowder. These previewers will be be picked up with a restart of clowder. THIS FEATURE HAS NOT BEEN TESTED YET!


Finally install clowder using the update script. You can from now on, just call this script to install the latest version of clowder.

```
/home/clowder/update-clowder.sh
```

You can add a cron entry that runs to call this script. It will only update this instance of clowder if a newer version is available. This will make it so you are always running the latest version.

# Medic2 Extractors

To get people started to create their own extractors we will add the example extractors, as well as the required software to compile these.

## clowder Extractors Requirements

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

## clowder Example Extractors

This will install and compile the example extractors

```bash
# clone extractors
cd
git clone https://opensource.ncsa.illinois.edu/stash/scm/cats/extractors-examples.git

# compile java code
cd extractors-examples/java
mvn package
```
