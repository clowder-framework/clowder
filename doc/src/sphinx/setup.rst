Setup
=====

With the required software in place and the code checked out, it is just a matter of starting web frontend.
Please make sure all services are running before doing this.

The web frontend is setup using the `sbt <http://www.scala-sbt.org/>`_ build system. Change directory into the
clowder main directory and run the code using the following command

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
- ``/conf/play.plugins`` is usesd to turn on and off specific functionality in the system. Plugins specific to Clowder are
  available under ``/app/services``.
- ``/conf/securesocial.conf`` includes configuration settings for email functionality when signup as well as ways to
  configure the different identity providers (for example Twitter or Facebook). More information can be found on the
  `securesocial <http://securesocial.ws/>`_ website.
