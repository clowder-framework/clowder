##################
Installing
##################

Clowder can be deployed in two ways. Users can either use `Docker <http://docker.com>`_  or manually install the core and
required dependencies. The :ref:`docker_install` method is the quickest method to run Clowder. It can be used for testing
features and development. It can be used in production but most of the current production but it hasn't been thoroughly
tested. Production instances usually use the second method of installation :ref:`manualinstall`. The only exception is that
most extractors are deployed using `Docker swarm <https://docs.docker.com/engine/swarm/>`_.

.. _docker_install:

******
Docker
******

To start using Clowder using docker you can use the provided
`docker compose file <https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS/repos/clowder/browse/docker-compose.yml>`_.

* Install `Docker <http://docker.com>`_.
* Start up all required services with ``docker-compose up -d`` (detached mode).
* To see the logs run ``docker-compose log -f``.
* To stop it execute ``docker-compose down`` in the same directory.

All commands need to be executed in the same directory as the Clowder docker compose file.

.. _manualinstall:

******
Manual Installation
******

Before installing Clowder make sure you have installed all the :ref:`requirements` below.

Download and unzip a specific version: `Latest stable version <https://opensource.ncsa.illinois.edu/projects/artifacts.php?key=CATS>`_.

Customizations your deployment by creating a custom folder in the root folder and add a ``/custom/custom.conf`` and a
``/custom/play.plugins`` files within. Modifications included in these files will overwrite defaults in
``/conf/application.conf`` and ``/conf/play.plugins``.

Do **not** make changes to the original files in ``/conf``.

The ``/custom/play.plugins`` file describes all the additional plugins that should be enabled. This file can only add additional plugins,
and is not capable of turning off any of the default ones enabled in ``/conf/play.plugins``
For example the following play.plugins file will enable some additional plugins:

.. code-block:: properties
  :caption: play.plugins

  9992:services.RabbitmqPlugin
  10002:securesocial.core.providers.GoogleProvider
  11002:services.ElasticsearchPlugin

``/custom/custom.conf`` is used to overwrite any of the defaults configurations. Some common examples that are modified are:

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


.. _requirements:

******
Requirements
******

Following is a list of requirements for the Clowder software. Besides Java you can have all other services/software
installed on other machines and can configure Clowder to communicate with these services. Items marked as always are
hard requirements (Java and mongo), the others are only required if you want to enable certain features in Clowder.

* Java 8 - required

  * The Clowder software is written in Scala and javascript and requires Java to execute.
  * Clowder has been tested with the OpenJDK.
  * Versions beyond 8 have not been tested.

* MongoDB v3.4 - required

  * By default Clowder uses MongoDB to store most of the information within the system.
  * Versions above 3.4 have not been tested.

* RabbitMQ (latest version) - optional

  * RabbitMQ is used to communicate between Clowder and the extractors. When deploying extractors it is required to deploy RabbitMQ as well.

* ElasticSearch 2.x - optional

  * ElasticSearch is used for text based search by Clowder.
  * Versions above 2.x have not been tested.

