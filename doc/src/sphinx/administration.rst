.. index:: Administration
==============
Administration
==============

There are two options for installation. You can either use `Docker <http://docker.com>`_ for installation or install all services on either a single machine or multiple machines yourself. The quickest method is :ref:`docker_install` installation. If you choose to install Clowder by yourself see :ref:`requirements`

.. _docker_install:

******
Docker
******

To start using Clowder using docker you can use the `docker compose file <https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS/repos/clowder/browse/docker-compose.yml>`_. This will start docker

.. _requirements:

************
Requirements
************

Following is a list of requirements for the Clowder software. Besides Java you can have all other services/software installed on other machines and can configure Clowder to communicate with these services. Items marked as always are hard requirements (Java and mongo), the others are only required if you want to enable certain features in Clowder.

============== ========================= ==================== =====
Software       Version                   Required for         Notes
============== ========================= ==================== =====
Java           1.8+                      always               The Clowder software is written in Scala and javascript and requires Java to execute. Clowder has been tested with OpenJDK.
MongoDB        3.2+ (latest preferred)   always               Clowder uses MongoDB to store the information about the files and if configured the files as well.
RabbitMQ       3.5+                      extractions          RabbitMQ is used to communicate between Clowder and the extractors. When deploying extractors it is required to deploy RabbitMQ as well.
ElasticSearch  2.x+ (5.x not yet tested) search               ElasticSearch is used to search Clowder. We have not tested Clowder with version 2.0 or larger of ElasticSearch.
============== ========================= ==================== =====

************
Installation
************

Before installing Clowder make sure you have looked at the :ref:`requirements` and have setup all the software that is needed.

The first step is for you to figure out which one to use, there are two major versions to choose from:

* `Latest stable version <https://opensource.ncsa.illinois.edu/projects/artifacts.php?key=CATS>`_ - This version is more tested, but is not as up to date as the development version.
* `Latest development version <https://opensource.ncsa.illinois.edu/projects/artifacts.php?key=CATS>`_ - This version contains the latest code and has been lightly tested.

After downloading the version you have selected you will unzip that version.

The final step in the installation is to customize your installation of Clowder. The customizations of the Clowder software are stored in the custom folder inside the Clowder installation. The main two files you will need to configure are custom.conf and play.plugins. Adding changes made to the files in the conf folder will NOT be used by Clowder.

The play.plugins describes all the additional plugins that should be enabled. This file can only add additional plugins, and is not capable of turning off any of the `default plugins <https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS/repos/clowder/browse/conf/play.plugins>`_. For example the following play.plugins file will add some additional plugins:

.. code-block:: properties
  :caption: play.plugins

  9992:services.RabbitmqPlugin
  10002:securesocial.core.providers.GoogleProvider
  11002:services.ElasticsearchPlugin

The file custom.conf file is used to overwrite any of the defaults values for Clowder. Some common examples that are modified are:

.. code-block:: properties
  :caption: custom.conf

  # mongodb
  mongodb.default="mongodb://mongoserver:27017/mongodatabase"
   
  # where to store the blobs (highly recommended)
  service.byteStorage=services.filesystem.DiskByteStorageService
  medici2.diskStorage.path="/home/clowder/data"
   
  # rabbitmq
  clowder.rabbitmq.uri="amqp://guest:guest@server/virtualhost"
  clowder.rabbitmq.exchange=exchange
   
  initialAdmins="youremail@address"
   
  # elasticsearch
  elasticsearchSettings.clusterName="name"
  elasticsearchSettings.serverAddress="server"
  elasticsearchSettings.serverPort=9300
   
  # securesocial customization
  # set this to true if using https
  securesocial.ssl=true
  # this will make the default timeout be 8 hours
  securesocial.cookie.idleTimeoutInMinutes=480
   
  # google setup
  securesocial.google.authorizationUrl="https://accounts.google.com/o/oauth2/auth"
  securesocial.google.accessTokenUrl="https://accounts.google.com/o/oauth2/token"
  securesocial.google.clientId="magic"
  securesocial.google.clientSecret="magic"
  securesocial.google.scope="https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email"
   
  # security options
  application.secret="some magic string"
  commKey=magickey


*********
Upgrading
*********

This page describes how to upgrade the Clowder software. The steps described will do an in-place upgrade of Clowder. The biggest advantage of this upgrade is that it is fast and requires the least amount of changes to the current system.

Before you start
================

Read about the new version - Review the release notes for the version of Clowder. If you skip a few versions, we strongly recommend that you read the release notes of the versions you have skipped.

Check for known issues - Use the JIRA to search for any issues in the new version that will affect you.

Check for compatibility:

Confirm that your operating system, database, and other software installed still comply with the requirements for Clowder.

If you have installed Clowder extractors, verify that they will be compatible with the version of Clowder you are upgrading to. If not you will need to update the extractors as well.

Prestaging and testing your new version of Clowder:

We strongly recommend performing your upgrade in a test environment first. Do not upgrade your production Clowder server until you are satisfied that your test environment upgrade has been successful.

If you have any problems with your test environment upgrade which you cannot resolve, create an issue at JIRA so that we can assist you.


Backing up your database
========================

Before you begin the upgrade process, make you have upgraded your database. During the upgrade process your database will be updated to match with the new version of the software. If you ever want to rollback to a previous version of the software you will have to rollback the database as well. Following are commands to backup your database, as well as the commands needed to restore the specific database

Backing up MongoDB
------------------

This will describe how to backup the mongo database. If you have the files stored in the mongo database (default) this can take a long time and take up a significant amount of space since it will also dump the actual files. This assumes you are using the default database name (clowder) on the local host. If your database is stored somewhere else or has a different name you will need to modify the commands below. To backup the mongo database use:

.. code-block:: bash
  :caption: Backing up MongoDB

  mongodump  --db clowder --out clowder-upgrade
 
Restoring MongoDB
-----------------

This describes how to restore the mongo database. If you have the files stored in the mongo database (default) this can take a long time and take up a significant amount of space since it will also restore the actual files. There are two ways to restore the mongo database, the first one will drop the database first, and thus will also remove any additional collections you added. The second way will only drop those collections that are imported, this can leave some additional collections that could create trouble in future updates.

.. code-block:: bash
  :caption: Restoring MongoDB 1

  echo "db.dropDatabase();" | mongo --db clowder
  mongorestore --db clowder clowder-upgrade/clowder
 
.. code-block:: bash
  :caption: Restoring MongoDB 2

  mongorestore --drop --db clowder clowder-upgrade/clowder
 
Backing up PostgreSQL
---------------------

If you leverage of the geostreams capabilities in Clowder you will be using a PostgreSQL database. Again this assumes you will be using the default database (geostream) on localhost. This will dump the database in a directory. Each of the tables will be a separate file that is compressed.

.. code-block:: bash
  :caption: Backing up PostgreSQL

  pg_dump -F d -Z 9 -d bety -f geostream


Restoring PostgreSQL
--------------------

To restore the database geostream database you can use the following command. 

.. code-block:: bash
  :caption: Restoring PostgreSQL

  pg_restore -d geostream geostream

Performing the upgrade
======================

The actual update consists of a few steps. After these steps are completed you will have an updated version of Clowder.

Make sure you have backed up your database. 

Download the version you want to install, some common versions are:

* `Latest stable version <https://opensource.ncsa.illinois.edu/projects/artifacts.php?key=CATS>`_ - This version is more tested, but is not as up to date as the development version.
* `Latest development version <https://opensource.ncsa.illinois.edu/projects/artifacts.php?key=CATS>`_ - This version contains the latest code and has been lightly tested.

Stop the current version of Clowder you have running

Move the folder of the current version

Unzip the downloaded version of Clowder

Move the custom folder of the original Clowder to the custom folder of the new Clowder

Start Clowder. Make sure your startup script uses the flag `-DMONGOUPDATE=1` and `-DPOSTGRESUPDATE=1` to update the databases. If the database is not updated the application might not run correctly and/or you might not be able to login.

To make this process easier we have a script "update-clowder.sh" that will perform all these tasks for you (except for the backup, your are still responsible for the backup). The script does assume you have in the startup script that will have the UPDATE flags enabled.

To upgrade to the latest development version, as root, do: 
 
.. code-block:: bash

  CLOWDER_BRANCH=CATS-CORE0 ./update-clowder.sh

To upgrade to the latest stable version, as root, do: 
 
.. code-block:: bash
  
  ./update-clowder.sh

For both, if this does not update it, add `--force` after `update-clowder.sh`.

Post upgrade checks and tasks
=============================

Once you have confirmed the availability of compatible versions of the extractors, you should upgrade your extractors after successfully upgrading Clowder.

Congratulations! You have completed your Clowder upgrade. 



*************
Customization
*************

To customize Clowder you can put all configuration changes in a folder called custom inside the Clowder folder. If you are working on the source code this folder is excluded from git so you can use that also to customize your development environment, and not accidentally commit changes to either play.plugins or application.conf. If you make any changes to the files in the custom folder you will need to restart the application (both in production and development).

play.plugins
============

The play.plugins file is used to enable plugins. You can only enable plugins, you can not disable plugins. This is one of the reasons why we minimize the number plugins that are enabled by default. For example most instances at NCSA will have the following plugins enabled.

.. code-block:: properties
  :caption: play.plugins

  9992:services.RabbitmqPlugin
  11002:services.ElasticsearchPlugin

custom.conf
===========

The custom.conf file is used to override any of the changes in the application.conf or any included conf files (such as securesocial.conf). One change every instance of Clowder should do is to modify the commKey and application.secret. Common changes we do is to modify Clowder to use a directory on disk to store all blobs instead of storing them in mongo. Following is an example that we use for some of the instances we have at NCSA.

.. code-block:: properties
  :caption: custom.conf

  # security options
  application.secret="1234567890123456789012345678901234567890"
  commKey=notreallyit

  # email when new user tries to sign up
  smtp.from="no-reply@example.com"
  smtp.fromName="NO REPLY"

  # URL to mongo
  mongodbURI = "mongodb://mongo1:27017,mongo2:27017,mongo3:27017/server1?replicaSet=CLOWDER"

  # where to store the blobs
  service.byteStorage=services.filesystem.DiskByteStorageService
  medici2.diskStorage.path="/home/clowder/data"

  # rabbitmq
  clowder.rabbitmq.uri="amqp://user:password@rabbitmq/clowder"
  clowder.rabbitmq.exchange=server1

  initialAdmins="joe@example.com"

  # elasticsearch
  elasticsearchSettings.clusterName="clowder"
  elasticsearchSettings.serverAddress="localhost"
  elasticsearchSettings.serverPort=9300

  # securesocial customization
  securesocial.ssl=true
  securesocial.cookie.idleTimeoutInMinutes=480

  # twitter setup
  securesocial.twitter.requestTokenUrl="https://api.twitter.com/oauth/request_token"
  securesocial.twitter.accessTokenUrl="https://api.twitter.com/oauth/access_token"
  securesocial.twitter.authorizationUrl="https://api.twitter.com/oauth/authorize"
  securesocial.twitter.consumerKey="key"
  securesocial.twitter.consumerSecret="secret"

  # enable cache
  ehcacheplugin = enabled


messages.XY
===========

This allows to translate or customize certain aspects of Clowder. All messages in Clowder are in english and are as messages.default. Unfortunately it is not possible to use messages.default to use for translations since it falls back to those embedded in the Clowder jar files. To update the messages in english, you can use messages.en. The default is for Clowder to only know about english, this can be changed in your custom.conf with application.langs="nl".

public folder
=============

The public folder is place where you can place customizations for previews, as well as new stylesheets. To add a new stylesheet you should place it in the public/stylesheets/themes/ folder. The name should be <something>.min.css or <something>.css. The user will at this point see in their customization settings the option to select <something> as their new theme to be used.

To add new previews you can put them in the public/javascripts/previewers/. To create a previewer you will create a folder in there and in there have the files needed for the previewer as well as a package.json file. This package.json file will describe the previewer, which as the name, the main file to load, and the content types (Preview files) that the previewer can handle.

.. code-block:: json

  {
     "name" : "Video",
     "main" : "video.js",
     "contentType" : ["video/webm", "video/mp4", "video/videoalternativeslist"]
  }
