# Clowder

A scalable research data management system you can install on your own cloud.

## Running

To work on Clowder you will need the following packages installed: Mongo and Java. You can either use the included
version of sbt and scala or you can use your own. For development of extractors you also need to have RabbitMQ
installed. Following is a list of all software is used by Clowder. All of these services can be installed either
locally or remotely.

Once you have installed all the required software and optional software you need you can start clowder. If you want
to change the underlying source code you can clone the git repository and use either a IDE to run Clowder, or you can
run it from the command line.

- If you use Intellij IDEA you can import the project as an SBT project.
- If you have eclipse  you can use the following command to create all the project files:
  `./sbt 'eclipse with-source=true execution-environment=JavaSE-1.7'`.
- To run Clowder from the command line you can use the following line: `./sbt 'run'`.
 
You can also download a precompiled version of Clowder from https://opensource.ncsa.illinois.edu/projects/CATS. Once
you have this version unzipped you can start it using `bin/clowder`.

You can then connect to Clowder using the following URL: http://localhost:9000/.
  
### JAVA (required)

To compile Clowder you will need to have at least version 1.7 of JAVA. Version 1.8 will work as well.

### Scala / SBT (required for development)

If you have sbt installed you can use it directly. The other option is to use the sbt executable included in Clowder
in the root folder. For windows there are 2 versions, one for 32 bit versions of JAVA and one for 64 bit versions of
JAVA. We highly recommend using 64 bit version of JAVA since the 32 bit version can run out of memory.

### MongoDB (required)

A recent version of MongoDB is required. Clowder is tested with versions 2.6.8 of MongoDB and up, and will work with
versions 3 and higher. The default for Clowder is to use the test database. This can be changed using the mongodbURI
in the custom.conf file. You will need to make sure mongod is running before you start Clowder.

### RabbitMQ (optional)

To enable the message passing between Clowder and the extractors you will need RabbitMQ. We currently are supporting
version 3.4.3 of RabbitMQ. We have not yet tested Clowder with version 4.0 of RabbitMQ. You can set the RabbitMQ
server using medici2.rabbitmq.uri in the custom.conf file. To enable RabbitMQ you will need to enable the plugin in
play.plugins.

The management plugin is used by the DTS extractor endpoints and will help with debugging. To enable the plugin you
will need to execute the following code inside the RabbitMQ folder: `sbin/rabbitmq-plugins enable rabbitmq_management`.
To open the management console you can open the following link in your browser: http://localhost:15672/

### ElasticSearch (optional)

To enable the searching of data in Clowder you will need the ElasticSearch plugin. Currently we support version 1.3.4
of ElasticSearch and up. You will need to tell Clowder what cluster to connect to using the following variables in
your custom.conf: elasticsearchSettings.clusterName, elasticsearchSettings.serverAddress,
elasticsearchSettings.serverPort. To enable RabbitMQ you will need to enable the plugin in play.plugins.

To allow for searching in the browser you can install the head plugin using the following command:
`elasticsearch/bin/plugin -install mobz/elasticsearch-head`. To access the plugin you can open the following link in
your browser: http://localhost:9200/_plugin/head/

### GeoServer (optional)

To enable the display of geospatial data in Clowder you will need GeoServer. Currently we support version 1.7.0
of GeoServer and up. You will need to tell Clowder in the metadata the URL of the geoserver.

### PostgreSQL (optional)

To enable streaming data in Clowder you will need PostgreSQL. Currently we support version 9.3 of PostgreSQL and up.
You will need the PostGIS extensions enabled. You will need to tell Clowder what server to connect to using the
following variables in your custom.conf: postgres.host, postgres.port, postgres.db, postgres.user and postgres.password.
To enable PostgreSQL you will need to enable the plugin in play.plugins.

### RDF Storage

In order for the RDF storage and querying features to work, 4store has to be installed from http://4store.org/.

## Configuration

Clowder can be easily configured using a file called custom.conf in your conf folder. In this file you can overwrite
any of the variables specified in application.conf. This file will not be checked in, so you can store all your local
changes to the configuration files in this one single location. If you want to enable any plugins that are not
enabled by default you can edit the play.plugins file in the same conf folder. All variables you can change are
documented in the application.conf file.

If you are using the dist version of Clowder you can place your custom.conf and play.plugins in the custom folder in
the root of the distribution. This allows you to easily save all your changes if you ever want to update your version
of Clowder.
