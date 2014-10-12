Setup, Interface, and Helper Scripts for Medici
===============================================

extract.py
----------

Pass a file to the Medici extraction bus for metadata extraction.  Prints returned JSON to the screen.

Usage:

		> ./extract.py [file|URL]

geostream.py
------------

mongo-exec.sh
--------------

test-tags.sh
------------

update-medici.py
----------------

Pull most recent version of Medici from repository and deploy.  Sets up upstart scripts.  In most cases setup as a cron
job running every 15 minutes or so.

Usage:

		> ./update-medici.py

upload.py
---------
