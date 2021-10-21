##################
Getting Started
##################

Clowder can be deployed in two ways. Users can either use `Docker <http://docker.com>`_  or manually install the core and
required dependencies. The :ref:`docker_install` method is the quickest method to run Clowder. It can be used for testing
features and development. It can be used in production but most of the current production but it hasn't been thoroughly
tested. Production instances usually use the second method of installation :ref:`manualinstall`. The only exception is that
most extractors are deployed using `Docker swarm <https://docs.docker.com/engine/swarm/>`_.

What type of user are you?
===============


For **users of Clowder**: Clowder is easily accessed via Docker. :ref:`Quickstart here<usersOfClowder>`.

For **developers of Clowder**, a hybrid is recommended. :ref:`Dev quickstart here<clowderDevelopers>`.

-  Run the Clowder instance manually via IntelliJ’s Play-2 run
   configuration.
-  Run the required services via Docker, and expose each of their ports
   to Clowder.

For **production instances of Clowder**, manual installation is
recommended.

-  Manual clowder install
-  Manual install of required services (at a minimum: MongoDB,
   ElasticSearch, RabbitMQ). See
   `Requirements <https://clowder-framework.readthedocs.io/en/latest/admin/installing.html#requirements>`__
   for details.

.. _usersOfClowder:

Users of Clowder: Getting Started via Docker
===================================

1. Install `Docker <http://docker.com/>`__ (if you haven’t already)
2. Download and unzip the `latest stable version here <Download and unzip a specific version: Latest stable version.>`_
3. Navigate to Clowder’s root directory in your bash command line (``cd clowder-<version_number>``)
4. Download the docker script to Clowder's root directory

.. code:: bash

   wget -O docker-compose.yml https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS/repos/clowder/raw/docker-compose.yml\?at\=refs%2Fheads%2Fdevelop

5. Start clowder on your local machine's command line (bash)

.. code:: bash

   docker-compose up -d

6. Open your web browser to ``localhost:8000``

-  If you see Error 404, allow a minute for it to appear.
-  Note: use port 8000 for docker run. However, port 9000 for manual builds.

⭐ If you experience any trouble, come ask us on `Slack here <https://join.slack.com/t/clowder-software/shared_invite/enQtMzQzOTg0Nzk3OTUzLTYwZDlkZDI0NGI4YmI0ZjE5MTZiYmZhZTIyNWE1YzM0NWMwMzIxODNhZTA1Y2E3MTQzOTg1YThiNzkwOWQwYWE>`_! ⭐

Helpful Docker commands:
---------------------------------

-  ``docker-compose up -d`` - start up all required services
-  ``docker-compose down`` - stop all docker containers
-  ``docker-compose logs -f`` - see the logs
-  ``docker info`` - check how many services are running

   -  Server → containers → Running shows currently active containers.
      You should expect to see 3 (MongoDB, Elasticsearch, RabbitMQ).

-  Check on Docker services via the Docker Desktop GUI (on Windows and
   Mac)

Done! Now simply `create a new user (below) <easyUserCreation>`_

.. _easyUserCreation:
Easy new user creation via Docker
---------------------------------

Run the (edited) command below from the base Clowder directory.

First, edit these properties to your liking:

-  FIRST_NAME
-  LAST_NAME
-  EMAIL_ADDRESS
-  PASSWORD

.. code:: bash

   docker run --rm -ti --network clowder_clowder -e FIRST_NAME=Admin -e LAST_NAME=User -e EMAIL_ADDRESS=admin@example.com -e PASSWORD=catsarecute -e ADMIN=true clowder/mongo-init

- If you get error: ``Error response from daemon: network clowder_clowder not found.`` 
  - Try changing the network parameter to ``--network clowder-1_clowder``. It's possible you have multiple Clowder docker containers.

Now you can login to Clowder in your browser via ``localhost:8000`` (or if you built from source in IntelliJ, use ``localhost:9000``).

Check out the `next steps below <nextSteps>`_ !

.. _clowderDevelopers:

Clowder Developers: Getting Started
===================================

For **Clowder developers**, a hybrid is recommended:

-  Part 1: Run the required services via Docker, and expose each of
   their ports to Clowder.
-  Part 2: Run the Clowder instance manually via IntelliJ’s Play-2 run
   configuration.

Part 1: Setup Docker
--------------------

1. Install `Docker <http://docker.com/>`__ (if you haven’t already)
2. Clone Clowder’s ``develop`` branch (the default)

.. code:: bash

   git clone https://github.com/clowder-framework/clowder.git

3. Navigate to Clowder’s root directory (``cd clowder``)
4. Expose ports for Docker services to Clowder (below)

Expose Docker services’ ports to Clowder
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In order for Clowder to access the required services (at a minimum:
MongoDB, ElasticSearch, RabbitMQ. See
`Requirements <https://clowder-framework.readthedocs.io/en/latest/admin/installing.html#requirements>`__
for details.), we must tell Clowder which ports the services are using.

1. Create an override file, where we will store the port information

.. code:: bash

   # navigate to Clowder base directory
   cd clowder 
   # create new file docker-compose.override.yml 
   touch docker-compose.override.yml 

2. Copy and paste the lines below into that file we just created
   (``docker-compose.override.yml``)

.. code:: bash

   # this lets Clowder communicate with the necessary services
   # These services (Mongo, RabbitMQ, ElsticSearch) will be run 
   # via docker containers using the command line

   services:
     mongo:
       image: mongo:3.4
       ports:
         - 27017:27017
     rabbitmq:
       image: rabbitmq:management-alpine
       ports:
         - 5672:5672
         - 15672:15672
     elasticsearch:
       image: elasticsearch:2
       ports:
         - 9200:9200
         - 9300:9300

3. Test that our services work! First start them:

.. code:: bash

   # make sure to pass the override file!
   docker-compose -f docker-compose.override.yml up -d

4. Go to each Mongo and ElasticSearch to see it running

.. code:: bash

   If you get a response, it's working!

   localhost:27017 -- "It looks like you're trying to access MongoDB" Success!
   localhost:15672 -- should see RabbitMQ login screen (no need to login tho!)
   localhost:9200 -- Should see a json file with "name" : "Machine Teen" 

Done! Now keep that running, and next let’s build Clowder from source.

Part 2: Run Clowder via IntelliJ
--------------------------------

1. Install IntelliJ Community Edition

   - This guide will assume developers use IntelliJ

2. Open the base Clowder directory

   - This should prompt you to install the Scala plugin! Install it.

3. Install the Scala Plugin for IntelliJ
4. Install Java 8 on your computer (version 8 required, not compatible
   with other versions)

   - From here: https://adoptopenjdk.net/. Java 8, HotSpot.

5. Connect IntelliJ to that Java instance.

   - In IntelliJ: ``file`` → ``Project Structure`` → ``SDKs``
   - Add a new SDK, and specify the root folder of the SDK you just downloaded. For example: JDK Home path on Windows might look like ``C:\Program Files\Eclipse Foundation\jdk-8.0.302.8-hotspot``

6. Add a new Run Configuration

   - In the top right, click the dropdown and click “Edit Configurations…”

.. figure:: ../_static/GettingStarted_addConfig.png
   :alt: Add new configuration

7. Create a new ``Play 2 App`` configuration

   - Note: if you **don’t see Play 2 App in the list**, you may need to use IntelliJ *Ultimate* version (instead of Community). I experienced this bug, feel free to ask in the Clowder Slack.

      .. figure:: ../_static/GettingStarted_Play2Config.png
         :alt: Create play2 configuration.

8. The default configuration should be okay, see image below.

   - NOTE: Later, **if Clowder feels slow** (multiple seconds per page load) then you will need to add JNotify to your JVM Options here. :ref:`Instructions at bottom of this page<slowClowder>`.

.. figure:: ../_static/GettingStarted_AddJDK.png
   :alt: Specify the JDK path

Done!

Now simply ensure your docker services are still running from the
previous step.

-  You can check by running ``docker info`` and check that 3 services
   are running.
-  If not, start them with
   ``docker-compose -f docker-compose.override.yml up -d``

Now in IntelliJ, click the green play button (top right) to build Clowder from source! Give it a minute to finish.

Access Clowder via ``localhost:9000`` in the
browser.

-  Note: building from source defaults to port 9000. Running from Docker
   (without building from source) defaults to port 8000.

.. _creatingLocalAccount
Creating a local Clowder account
--------------------------------

After creating your Clowder instance, you still need to Sign Up for a
user account. All accounts require:

1. Email verification (need to spoof this locally)
2. To be Activated by an administrator (you are an administrator of the
   local instance)

3 ways to create a local Clowder account:

1. Easiest: Use the docker command

   - Skip the email verification and activation.

2. Creating many users: set the default to auto-activate new users.
3. Already created a user, but you didn’t get a confirmation email, or
   you’re “not activated”: edit permissions in MongoDB.

Method 1: Easiest new user creation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Run the (edited) command below from the base Clowder directory.

First, edit these properties to your liking:

-  FIRST_NAME
-  LAST_NAME
-  EMAIL_ADDRESS
-  PASSWORD

.. code:: bash

   docker run --rm -ti --network clowder_clowder -e FIRST_NAME=Admin -e LAST_NAME=User -e EMAIL_ADDRESS=admin@example.com -e PASSWORD=catsarecute -e ADMIN=true clowder/mongo-init

Now you can login to Clowder (``localhost:9000`` or ``localhost:8000`` if not building from source Docker) in your browser

User creation method 2 and 3
----------------------------

For methods 2 and 3, enable local email verification (*or you will never
get an email verification*).

Enable local email verification
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

For local instances of Clowder, the email verification step will have to
be done manually, via a mock SMTP email server.

Add the following line to the bottom of ``application.conf``

.. code:: bash

   # application.conf
   # Add the content below to end of file

   # ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
   # Local email verification -- see Intellij console to complete registration
   # ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
   smtp.mock=true

Now the below methods will work.

Method 2: Creating many users? Change default Activation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

All accounts must also be **activated by an administrator. To activate
your account by default, edit ``application.conf``**

.. code:: bash

   # application.conf
   # Search for this line, and EDIT it (not adding a new line)
   # SET TO FALSE

   # Whether emails for new users registrations go through admins first
   registerThroughAdmins=false

Done! Create new users via the Clowder GUI in your browser.

Method 3: Edit permissions in MongoDB
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To edit the permissions on *existing accounts*, **edit their properties
in MongoDB**. You can skip this step if haven’t created a local Clowder
account yet.

1. Download RoboMongo (a GUI for MongoDB)
2. Ensure all services are running!

.. code:: bash

   cd clowder # base directory

   # start all required services 
   docker-compose up -d

1. Connect RoboMongo to the docker instance (the defaults should be
   fine)

   1. Point it towards port ``27017``

2. In the file tree on the left, navigate to clowder → Collections →
   social.users

   1. Then click the dropdown to expand that user
   2. Find ``status`` field, and right click to edit.
   3. If it is ``Inactive``, change it by typing ``Active``
      (capitalized)

3. Done. Refresh your browser (on ``localhost:9000``) to access Clowder.

Create a local Clowder account
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Start Clowder:

1. Start required services (via
   ``docker-compose -f docker-compose.override.yml up -d`` from the root
   Clowder directory).

   1. You can check if your services are already running using
      ``docker info`` and check that 3 containers are active (MongoDB,
      ElasticSearch, and RabbitMQ) by looking at
      ``Server → Containser: 3``. Or check via the Docker Desktop GUI.

2. Ensure your local clowder instance is running (on ``localhost:9000``)

Finally, **attempt to signup for an account via the Clowder GUI** on
``localhost:9000``

-  Click the Sign Up button in the top right.

Upon clicking Signup, **the IntelliJ console will show the text of the
user signup verification emails**, where you can click the confirmation
link.

Look for this in the console:

-  Don’t see it? Make sure you enabled ``smtp.mock=true`` above!!

.. code:: python

   <p>Please follow this
       <a href="http://localhost:9000/signup/baf28c54-80fe-480c-b1e4-9200668cb92e">link</a> to complete your registration
       at <a href="http://localhost:9000/">Clowder</a>.
   </p>

Now fill in your account details, and you should be good to go using
Clowder!

.. _slowClowder: 

If Clowder feels slow, add the faster JVM option
------------------------------------------------

`Instructions are
here <https://opensource.ncsa.illinois.edu/confluence/display/CATS/JVM+Configuration+Options>`__.

-  Simply download JNotify and tell IntelliJ where it is.


.. _nextSteps: 

Next Steps
==========

Watch the `Clowder Conference playlist on
Youtube <https://www.youtube.com/playlist?list=PLVhslX3lYajMZD9KA-RJK-ulmXys8d13i>`__!

-  28 videos covering specific Clowder topics and uses
-  `Check out the Overview of
   Clowder <https://www.youtube.com/watch?v=B5hD8ehENck&list=PLVhslX3lYajMZD9KA-RJK-ulmXys8d13i&index=5&ab_channel=ClowderFramework>`__
-  `Check out How to use
   Clowder <https://www.youtube.com/watch?v=wHmDJAD5GbE&list=PLVhslX3lYajMZD9KA-RJK-ulmXys8d13i&index=5&t=605s&ab_channel=ClowderFramework>`__
-  `Check out How to Create a New
   Extractor <https://www.youtube.com/watch?v=0uthTzrZCt8&list=PLVhslX3lYajMZD9KA-RJK-ulmXys8d13i&index=17&ab_channel=ClowderFramework>`__
   and many more!

Try the `core data
extractors <https://github.com/clowder-framework/extractors-core>`__.

Resources for Developers
========================

`Installing Clowder and creating custom plugins and Configurations
(legacy
instructions) <https://opensource.ncsa.illinois.edu/confluence/display/CATS/Installing+Clowder>`__

`Clowder REST API
examples <https://opensource.ncsa.illinois.edu/confluence/display/CATS/Clowder+API+Examples>`__

`Extractors: Running samples and writing your
own <https://opensource.ncsa.illinois.edu/confluence/display/CATS/Extractors>`__

-  Look at the `Core Extractors for
   examples <https://github.com/clowder-framework/extractors-core>`__
   for image, video, Audio, PDF, etc.
-  `Extractor for CSV
   files <https://github.com/clowder-framework/extractors-csv>`__
-  `Extractor for ZIP
   files <https://github.com/clowder-framework/extractors-zip>`__
-  `Virus checker
   extractor <https://github.com/clowder-framework/extractors-clamav>`__
   (ensure dataset doesn’t have viruses)



Customize Deployment
=================


Customize your deployment by creating a custom folder in Clowder's root directory and add a ``/custom/custom.conf`` and a
``/custom/play.plugins`` files within. Modifications included in these files will overwrite defaults in
``/conf/application.conf`` and ``/conf/play.plugins``.

Do **not** make changes to the original files in ``/conf``.

The ``/custom/play.plugins`` file describes all the additional plugins that should be enabled. This file can only add additional plugins,
and is not capable of turning off any of the default ones enabled in ``/conf/play.plugins``
For example the following ``play.plugins`` file will enable some additional plugins:

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

Requirements
=================

Following is a list of requirements for the Clowder software. Besides Java, all other services/software
can be installed on other machines with Clowder configured to communicate with them.

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

