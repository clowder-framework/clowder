.. _customization:

********************************
Customizing
********************************

The default configuration
==========================

.. warning::
  Do **not** make changes to the original files in ``/conf``. Instead, create a ``/custom`` folder shown below.

The default configuration is fine for simple testing, but if you would like to modify any of the settings, you can find
all the configuration files under the ``/conf`` directory. The following files are of particular importance:

- ``/conf/application.conf`` includes all the basic configuration entries. For example the MongoDB credentials for
  deployments where MongoDB has non default configuration.
- ``/conf/play.plugins`` is used to turn on and off specific functionality in the system. Plugins specific to Clowder are
  available under ``/app/services``.
- ``/conf/securesocial.conf`` includes configuration settings for email functionality when signup as well as ways to
  configure the different identity providers (for example Twitter or Facebook). More information can be found on the
  `securesocial <http://securesocial.ws/>`_ website.


How to customize Clowder
============================

To customize Clowder, create a folder called ``custom`` inside the Clowder folder (``clowder/custom``).
Add the following. Modifications included in these files will overwrite defaults in ``/conf/application.conf`` and ``/conf/play.plugins``.

.. code:: bash
  
      cd clowder
      mkdir custom
      touch custom/application.conf custom/play.plugins


If you are working on the source code this folder is excluded from git so you can use that also to customize your development environment, and not accidentally commit changes to either ``play.plugins`` or ``application.conf``. If you make any changes to the files in the custom folder you will need to `restart the application` (both in production and development).


play.plugins
--------------

The ``/custom/play.plugins`` file describes all the `additional` plugins that should be enabled. **This file can only add additional plugins,
and is not capable of turning off any of the default ones enabled in** ``/conf/play.plugins``.
For example the following ``play.plugins`` file will enable some additional plugins:

.. code-block:: properties
  :caption: play.plugins

  9992:services.RabbitmqPlugin
  10002:securesocial.core.providers.GoogleProvider
  11002:services.ElasticsearchPlugin


custom.conf
--------------

``/custom/custom.conf`` is used to override any of the defaults in the ``application.conf`` or any included conf files (such as ``securesocial.conf``). Common changes we do is to modify Clowder to use a directory on disk to store all blobs instead of storing them in mongo. Following is an example that we use for some of the instances we have at NCSA.

- **One change every instance of Clowder should do is to modify the commKey and application.secret.** 



.. code-block:: properties
  :caption: custom.conf

  # security options -- should be changed!
  application.secret="some magic string"
  commKey=magickey

  # email when new user tries to sign up
  smtp.from="no-reply@example.com"
  smtp.fromName="NO REPLY"

  # URL to mongo
  mongodbURI = "mongodb://mongo1:27017,mongo2:27017,mongo3:27017/server1?replicaSet=CLOWDER"

  # where to store the blobs (highly recommended)
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
  # set this to true if using https
  securesocial.ssl=true
  # this will make the default timeout be 8 hours
  securesocial.cookie.idleTimeoutInMinutes=480

  # twitter setup
  securesocial.twitter.requestTokenUrl="https://api.twitter.com/oauth/request_token"
  securesocial.twitter.accessTokenUrl="https://api.twitter.com/oauth/access_token"
  securesocial.twitter.authorizationUrl="https://api.twitter.com/oauth/authorize"
  securesocial.twitter.consumerKey="key"
  securesocial.twitter.consumerSecret="secret"

  # google setup
  securesocial.google.authorizationUrl="https://accounts.google.com/o/oauth2/auth"
  securesocial.google.accessTokenUrl="https://accounts.google.com/o/oauth2/token"
  securesocial.google.clientId="magic"
  securesocial.google.clientSecret="magic"
  securesocial.google.scope="https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email"

  # enable cache
  ehcacheplugin = enabled


messages.XY
---------------------

This allows to translate or customize certain aspects of Clowder. All messages in Clowder are in english and are as messages.default. Unfortunately it is not possible to use messages.default to use for translations since it falls back to those embedded in the Clowder jar files. To update the messages in english, you can use messages.en. The default is for Clowder to only know about english, this can be changed in your custom.conf with ``application.langs="nl"``.

Customizing Web UI
---------------------

The ``public`` folder is place where you can place customizations for previews, as well as new stylesheets. To add a new stylesheet you should place it in the public/stylesheets/themes/ folder. The name should be <something>.min.css or <something>.css. The user will at this point see in their customization settings the option to select <something> as their new theme to be used.

To add new previews you can put them in the public/javascripts/previewers/. To create a previewer you will create a folder in there and in there have the files needed for the previewer as well as a package.json file. This package.json file will describe the previewer, which as the name, the main file to load, and the content types (Preview files) that the previewer can handle.

.. code-block:: json

  {
     "name" : "Video",
     "main" : "video.js",
     "contentType" : ["video/webm", "video/mp4", "video/videoalternativeslist"]
  }
