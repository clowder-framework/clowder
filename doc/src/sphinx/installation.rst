Installation
============

The following instructions cover dependencies, source code and configuration.

Requirements
------------

The minimum requirements are:

- The Java Development Kit version 7. Either the `Oracle JDK <http://www.oracle.com/technetwork/java/javase/downloads/index.html>`_
  or the `Open JDK <http://openjdk.java.net/>`_ . You might already have Java installed. Open a command prompt and
  try typing ``java``.

- The `MongoDB <http://www.mongodb.org/>`_ database. The latest tested version should be **2.2.6**.

If you would like to use the extractor functionality (which is true in most cases), you will also need:

- The `RabbitMQ <http://www.rabbitmq.com/>`_ event bus. Different packages are available for specific operating systems.
  Requires Erlang. Erlang is installed with most package as a dependency.

For text-based search functionality the following is required:

- The `Elasticsearch <http://www.elasticsearch.org/>`_ distributed search service. Requires Java.

For content-based search the following is required:

- The `Versus <http://isda.ncsa.illinois.edu/documentation/versus/tutorial.html>`_ service.