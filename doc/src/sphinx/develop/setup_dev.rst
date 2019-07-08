.. index:: Development Environment

Development Environment
=============================

The Clowder web application is written in `Scala <http://www.scala-lang.org/>`_ and `Play <www.playframework.org>`_. Many
developers currently use `IntelliJ IDEA <https://www.jetbrains.com/idea/>`_ for Scala development.

Minimum Requirements
--------------------

First install required software described here :ref:`requirements`.


Check out the source code
-------------------------

You will need a `Git <https://git-scm.com/>`_ client to checkout the source code. The code is available at the following url as a collection of git repositories:

- https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS

The repository `clowder` containes the web frontend and is required. It should be cloned using the following command:

::

  > git clone https://opensource.ncsa.illinois.edu/bitbucket/scm/cats/clowder.git


Most of the other repositories include specific extractors. Basic extractors are available in `extractors-core`:

::

  > git clone https://opensource.ncsa.illinois.edu/bitbucket/scm/cats/extractors-core.git


Execute
-------

With the required software in place and the code checked out, first start up MongoDB, then the web frontend.

::

  mongod

The web frontend is setup using the `sbt <http://www.scala-sbt.org/>`_ build system. Change directory into the
Clowder main directory and run the code using the following command

::

  ./sbt run


To have access to other commands enter the sbt shell first and the use one of the many commands available
(you can get a list by typing `help` in the shell). For example, to build the application for deployment type the following:

::

  ./sbt
  > dist


The default configuration is fine for simple testing, but if you would like to modify any of the settings, you can find
the all the configuration files under the ``/conf`` directory. The following files are of particular importance:

- ``/conf/application.conf`` includes all the basic configuration entries. For example the MongoDB credentials for
  deployments where MongoDB has non default configuration.
- ``/conf/play.plugins`` is used to turn on and off specific functionality in the system. Plugins specific to Clowder are
  available under ``/app/services``.
- ``/conf/securesocial.conf`` includes configuration settings for email functionality when signup as well as ways to
  configure the different identity providers (for example Twitter or Facebook). More information can be found on the
  `securesocial <http://securesocial.ws/>`_ website.

The files above can be overriden following the instructions in :ref:`customization`.