.. _installing_clowder:

##################
Installing Clowder
##################

What type of user are you?
===================================


For **most users of Clowder**: easily access Clowder via Docker. :ref:`Get started here <usersOfClowder>` 👇

For **developers of Clowder itself**, a hybrid is recommended. :ref:`Dev quickstart here<clowderDevelopers>`.

-  Build Clowder from source via IntelliJ’s Play-2 run
   configuration.
-  Run the required services via Docker.

For **production instances of Clowder**, a Kubernetes deployment is recommended and manual installations are being phased out.

-  See `Clowder's Kubernetes install instructions here <https://github.com/clowder-framework/clowder-helm>`__.
-  Otherwise, manually install Clowder, and each of it's required services (at a minimum: MongoDB,
   ElasticSearch, RabbitMQ). See :ref:`requirements below<requirements>` for details.

.. _usersOfClowder:

Users of Clowder: Getting Started via Docker
==============================================

1. Install `Docker Desktop <http://docker.com/>`__ (if you haven’t already)
2. Clone or download `Clowder on Github <https://github.com/clowder-framework/clowder>`_ (use the default ``develop`` branch)

.. code:: bash

   git clone https://github.com/clowder-framework/clowder.git

3. Navigate to Clowder’s root directory in your bash command line (``cd clowder``)
4. Start Clowder using the Docker Compose configuration, via your command line

.. code:: bash

   docker-compose up -d
   
5. If you experience any issue with file uploads and see the below error message in the console:

.. code:: text

   [ERROR  ] - application - Could not create folder on disk /home/clowder/data/uploads/xx/xx/xx
   [ERROR  ] - application - Could not save bytes, deleting file xxx

you can try this command:

.. code:: bash 
   
   docker-compose exec -u 0 clowder chmod 777 /home/clowder/data

6. Open your web browser to ``localhost:8000``

-  If you see Error 404, allow a minute for it to appear.
-  Note: use port 8000 for docker run. However, port 9000 for manual builds.

⭐ If you experience *any* trouble, come ask us on `Slack here <https://join.slack.com/t/clowder-software/shared_invite/enQtMzQzOTg0Nzk3OTUzLTYwZDlkZDI0NGI4YmI0ZjE5MTZiYmZhZTIyNWE1YzM0NWMwMzIxODNhZTA1Y2E3MTQzOTg1YThiNzkwOWQwYWE>`_! ⭐

Helpful Docker commands:
---------------------------------

-  ``docker-compose up -d`` - start up all required services
-  ``docker-compose down`` - stop all docker containers
-  ``docker-compose logs -f`` - see the logs
-  ``docker ps`` - check how many services are running
-  ``docker info`` - details about your docker version

   -  After starting your services, run ``docker ps`` and check that 3 containers are running. The "Image" column should show ``rabbitmq``, ``elasticsearch`` and ``mongo``.

   -  You could also check on Docker services via the Docker Desktop GUI (on Windows and
   Mac)

Done! Now simply `create a new user 👇 <easyUserCreation>`_

.. _easyUserCreation:
Create your first user
-------------------------

Run the (edited) command below from the base Clowder directory.

First, **edit these properties** to your liking in the ``docker run`` command below:

-  FIRST_NAME (default: ``Admin``)
-  LAST_NAME  (``User``)
-  EMAIL_ADDRESS  (``admin@example.com``)
-  PASSWORD   (``catsarecute``)
-  ADMIN  (``true``)

.. code:: bash

   docker run --rm -ti --network clowder_clowder -e FIRST_NAME=Admin -e LAST_NAME=User -e EMAIL_ADDRESS=admin@example.com -e PASSWORD=catsarecute -e ADMIN=true clowder/mongo-init

Now you can login to Clowder in your browser via ``localhost:8000`` (or if you built from source in IntelliJ, use ``localhost:9000``).

- If you get error: ``Error response from daemon: network clowder_clowder not found.`` 
  - Try changing the network parameter to ``--network clowder-1_clowder``. It's possible you have multiple Clowder docker containers.

Done! You should be able to login to your new account, create new Spaces & Datasets and upload many different types of data. 

.. note::
   Before you go, check out useful information like the `Clowder 'All Paws' YouTube playist <https://www.youtube.com/playlist?list=PLVhslX3lYajMZD9KA-RJK-ulmXys8d13i>`__.
   
   -  `Check out the Overview of Clowder <https://www.youtube.com/watch?v=B5hD8ehENck&list=PLVhslX3lYajMZD9KA-RJK-ulmXys8d13i&index=5&ab_channel=ClowderFramework>`__
   -  `Check out How to use Clowder <https://www.youtube.com/watch?v=wHmDJAD5GbE&list=PLVhslX3lYajMZD9KA-RJK-ulmXys8d13i&index=5&t=605s&ab_channel=ClowderFramework>`__
   -  `Check out How to Create a New Extractor <https://www.youtube.com/watch?v=0uthTzrZCt8&list=PLVhslX3lYajMZD9KA-RJK-ulmXys8d13i&index=17&ab_channel=ClowderFramework>`__
   -  and 28 total videos covering specific Clowder topics and uses!

   Try the :ref:`default extractors<defaultExtractors>` for simple quality of life improvements in Clowder.

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
4. Expose ports for Docker services to Clowder 👇

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
   ``docker-compose.override.yml``

.. code:: yml

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

   docker-compose up -d


.. note::
   By default, running ``docker-compose up -d`` uses the ``docker-compose.yml`` configuration and will apply overrides found in ``docker-compose.override.yml``. Neither file need to be specified on the command line.


4. Go to each Mongo and ElasticSearch to see it running

.. code:: text

   If you get a response, it's working!

   localhost:27017 -- "It looks like you're trying to access MongoDB" Success!
   localhost:15672 -- should see RabbitMQ login screen (no need to login tho!)
   localhost:9200 -- Should see a json file with "name" : "Machine Teen" 

Done! Now keep that running, and next let’s build Clowder from source 👇

Part 2: Run Clowder via IntelliJ
--------------------------------

1. Install IntelliJ Community Edition

   - This guide will assume developers use IntelliJ

2. Open the base Clowder directory

   - This should prompt you to install the Scala plugin! Install it.

3. Install the Scala Plugin for IntelliJ ``File`` --> ``Settings`` --> ``Plugins`` --> ``Download Scala``.
4. Install Java 8 on your computer (version 8 required, not compatible
   with other versions)

   - I find this easiest to do via IntelliJ’s Plugin Manager. ``File`` --> ``Project Structure`` --> ``SDKs`` --> ``+`` icon --> ``Download JDK``
   - Select ``Version 1.8`` (Clowder is only compatible with Java 8 (1.8), nothing higher) --> Vendor: ``Eclipse Temurin (AdoptOpenJDK Hotspot)`` --> ``Download``.

   - Alternatively, download the JDK online at `AdoptOpenJDK <https://adoptopenjdk.net/>`__. Java 8, HotSpot.
   - Then point IntelliJ to the JDK folder under ``Project Structure`` --> ``SDKs`` and specify the root folder of the JDK you just downloaded.

.. figure:: ../_static/IntelliJ_JDK_Download.png
   :alt: Download JDK from IntelliJ.

5. Add a new Run Configuration

   - In the top right, click the dropdown and click “Edit Configurations…”

.. figure:: ../_static/GettingStarted_addConfig.png
   :alt: Add new configuration

6. Create a new ``Play 2 App`` configuration

.. note::

   If you **don’t see Play 2 App in the list**, ensure you have the Scala plugin installed. If Play2 still isn't there, you may need to use IntelliJ *Ultimate* version (instead of Community). I experienced this bug, feel free to ask in the `Clowder Slack here <https://join.slack.com/t/clowder-software/shared_invite/enQtMzQzOTg0Nzk3OTUzLTYwZDlkZDI0NGI4YmI0ZjE5MTZiYmZhZTIyNWE1YzM0NWMwMzIxODNhZTA1Y2E3MTQzOTg1YThiNzkwOWQwYWE>`_.

.. figure:: ../_static/GettingStarted_Play2Config.png
   :alt: Create play2 configuration.

7. The default configuration should be okay, see image below.

.. note::

   Later, **if Clowder feels slow** (multiple seconds per page load) then you will need to add JNotify to your JVM Options on this page. :ref:`Instructions at bottom of this page<slowClowder>`.

.. figure:: ../_static/GettingStarted_AddJDK.png
   :alt: Specify the JDK path

Done!

Now simply ensure your docker services are still running from the
previous step.

-  You can check by running ``docker ps`` and check that 3 services
   are running.
-  If not, start them with
   ``docker-compose up -d``

Now in IntelliJ, click the green play button (top right) to build Clowder from source! Give it a minute to finish. Access Clowder via ``localhost:9000`` in the browser.

Also note, a handy debugging mode 🐞 is enabled by default. You can run the debug mode by clicking the green "bug" button right beside the play button.

.. note::

   Use ``localhost:9000`` when **building from source** (clicking the green play button ▶️ in IntelliJ).``

   Use ``localhost:8000`` when **running from Docker only** (via ``docker-compose up -d`` without building from source)

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

Run the (edited) command below from the base Clowder directory. If the folder name is not clowder, the network will need to be changed, e.g. the folder you are in is called `kitten` in this case the network will be `kitten_clowder`.

First, edit these properties to your liking:

-  FIRST_NAME
-  LAST_NAME
-  EMAIL_ADDRESS
-  PASSWORD

.. code:: bash

   docker run --rm -ti --network clowder_clowder -e FIRST_NAME=Admin -e LAST_NAME=User -e EMAIL_ADDRESS=admin@example.com -e PASSWORD=catsarecute -e ADMIN=true clowder/mongo-init

✅ Configuration complete! Now you can login to Clowder via ``localhost:9000`` in your browser.

:ref:`Skip to using default extractors and developer resources <defaultExtractors>` 👇

User creation method 2 and 3
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

For methods 2 and 3, enable local email verification (*or you will never
get an email verification*).

Enable local email verification
+++++++++++++++++++++++++++++++++

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

**All accounts must also be activated by an administrator. To activate
your account by default, edit** ``application.conf``.

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
in MongoDB**. You can skip this step if you haven’t created a local Clowder
account yet.

1. Download a GUI for MongoDB: MongoDB Compass or a 3rd party tool like RoboMongo.
2. Ensure all services are running!

.. code:: bash

   cd clowder # base directory

   # start all required services 
   docker-compose up -d

3. Connect RoboMongo to the docker instance (the defaults should be
   fine)

   1. Point it towards port ``27017``

4. In the file tree on the left, navigate to clowder → Collections →
   social.users

   1. Then click the dropdown to expand that user
   2. Find ``status`` field, and right click to edit.
   3. If it is ``Inactive``, change it by typing ``Active``
      (capitalized)

5. Done. Refresh your browser (on ``localhost:9000``) to access Clowder.

Create a local Clowder account
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Start Clowder:

1. Start required services (via
   ``docker-compose up -d`` from the root
   Clowder directory).

   1. You can check if your services are already running using
      ``docker ps`` and check that 3 containers are active (MongoDB,
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

-  Don’t see it? Make sure you enabled ``smtp.mock=true`` above.

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

- Follow the `instructions here to add JNotify <https://opensource.ncsa.illinois.edu/confluence/display/CATS/JVM+Configuration+Options>`__.
- Simply download JNotify and tell IntelliJ where it is in the ``Run Configurations`` -> ``JVM Options``.

.. _defaultExtractors: 

Use the default extractors
============================


The default extractors offer simple quality of life improvements for image, video, pdf, and audio file previews while browsing Clowder.

Enable them by starting Clowder with the extractors file ``docker-compose.extractors.yml``:

.. code:: bash

   docker-compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.extractors.yml up -d

Or run NCSA GeoServer for viewing and editing geospacial data via ``docker-compose.geoserver.yml``: 

* geoserver
* ncsa_geo_shp
* extractor-geotiff-preview
* extractor-geotiff-metadata

Learn more about `GeoServer <https://wiki.ncsa.illinois.edu/display/NCSASoftware/GeoServer+Focus+Group+Final+Report>`__ and `read the documentation <https://wiki.ncsa.illinois.edu/display/MM/Documentation>`__.

Troubleshooting extractors
---------------------------
Networking issues: Error "connection refused."
This is caused by the docker containers not being able to connect to each other.

1. In ``conf/application.conf`` edit the rabbitmq (message queue) URL to: ``clowder.rabbitmq.clowderurl="http://host.docker.internal:9000"``

Then restart Clowder in IntelliJ and via Docker, and everything should work. Done!

On Windows, I've had trouble getting ``localhost`` to resolve to the Docker host. You could try the following:

- Access Clowder **NOT** via localhost, but via your local IP address. For example, ``55.251.130.193:9000``. 

- You can find your local IP address:
   - Windows: ``Settings`` -> ``Network & internet`` -> ``IPv4 address``.
   - Mac: ``System Preferences`` --> ``Netowrk``--> ``Advanced``--> ``TCP/IP``--> ``IPv4 Address``. (Note: don't use the 'Public IP' from iStat Menus).
   - Linux ``$ ifconfig``

That should resolve extractor issues.

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

Try the :ref:`default extractors<defaultExtractors>` for simple quality of life improvements in Clowder.

Write your own extractors using the `PyClowder Python package <https://github.com/clowder-framework/pyclowder>`__.

🤔❓ Please ask any questions on our `Clowder Slack <clowder-software.slack.com>`__.

.. _clowder-python:

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
   (to ensure datasets don't have viruses)


Customize Deployment
======================


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
=============

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


