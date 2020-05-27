******
Upgrading
******



This page describes how to upgrade the Clowder software. The steps described will do an in-place upgrade of Clowder.
The biggest advantage of this upgrade is that it is fast and requires the least amount of changes to the current system.

Before you start
================

Before you start the upgrade thoroughly review the Change Log for all new versions since your
current deployed version.

Confirm that your operating system, database, and other software installed still comply with the requirements for Clowder.

If you have installed Clowder extractors, verify that they will be compatible with the version of Clowder you are upgrading to.
If not you will need to update the extractors as well.


We strongly recommend performing your upgrade in a test environment first. Do not upgrade your production Clowder server
until you are satisfied that your test environment upgrade has been successful.



Backing up your database
========================

Before you begin the upgrade process, make sure you have upgraded your database. During the upgrade process your
database will be updated to match with the new version of the software. If you ever want to rollback to a previous
version of the software you will have to rollback the database as well. Following are commands to backup your database,
as well as the commands needed to restore the specific database

Backing up MongoDB
------------------

This will describe how to backup the mongo database. If you have the files stored in the mongo database (default) this
can take a long time and take up a significant amount of space since it will also dump the actual files. This assumes
you are using the default database name (clowder) on the local host. If your database is stored somewhere else or has a
different name you will need to modify the commands below. To backup the mongo database use:

.. code-block:: bash
  :caption: Backing up MongoDB

  mongodump  --db clowder --out clowder-upgrade

Restoring MongoDB
------------------

This describes how to restore the mongo database. If you have the files stored in the mongo database (default) this can
take a long time and take up a significant amount of space since it will also restore the actual files. There are two
ways to restore the mongo database, the first one will drop the database first, and thus will also remove any additional
collections you added. The second way will only drop those collections that are imported, this can leave some additional
collections that could create trouble in future updates.

.. code-block:: bash
  :caption: Restoring MongoDB 1

  echo "db.dropDatabase();" | mongo --db clowder
  mongorestore --db clowder clowder-upgrade/clowder

.. code-block:: bash
  :caption: Restoring MongoDB 2

  mongorestore --drop --db clowder clowder-upgrade/clowder

Performing the upgrade
========================

The actual update consists of a few steps. After these steps are completed you will have an updated version of Clowder.

Make sure you have backed up your database.

Download the version you want to install, some common versions are:

* `Latest stable version <https://opensource.ncsa.illinois.edu/projects/artifacts.php?key=CATS>`_ - This version is
  more tested, but is not as up to date as the development version.
* `Latest development version <https://opensource.ncsa.illinois.edu/projects/artifacts.php?key=CATS>`_ - This version
  contains the latest code and has been lightly tested.

Stop the current version of Clowder you have running

Move the folder of the current version

Unzip the downloaded version of Clowder

Move the custom folder of the original Clowder to the custom folder of the new Clowder

Start Clowder. Make sure your startup script uses the flag `-DMONGOUPDATE=1` and `-DPOSTGRESUPDATE=1` to update the
databases. If the database is not updated the application might not run correctly and/or you might not be able to login.

To make this process easier we have a script "update-clowder.sh" that will perform all these tasks for you (except for
the backup, your are still responsible for the backup). The script does assume you have in the startup script that will
have the UPDATE flags enabled.

To upgrade to the latest development version, as root, do:

.. code-block:: bash

  CLOWDER_BRANCH=CATS-CORE0 ./update-clowder.sh

To upgrade to the latest stable version, as root, do:

.. code-block:: bash

  ./update-clowder.sh

For both, if this does not update it, add `--force` after `update-clowder.sh`.

Post upgrade checks and tasks
=============================

Once you have confirmed the availability of compatible versions of the extractors, you should upgrade your extractors
after successfully upgrading Clowder.

Congratulations! You have completed your Clowder upgrade.