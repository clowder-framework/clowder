Setup, Interface, and Helper Scripts for Medici
===============================================

extract.py
----------

Pass a file to the Medici extraction bus for metadata extraction.  Prints returned JSON to the screen.

Usage:

		> ./extract.py <file|URL>

geostream.py
------------

A simple example script showing how to query the Medici geostreaming API.

mongo-exec.sh
--------------

Execute given argument as a Mongo shell command.

Usage:

		./mongo-exec.sh 'db.uploads.files.count()'

test-tags.sh
------------

Find a file/dataset/section that does not have any existing tags or use one with a given ID and test the various REST
endpoints that manipulate the tags.

Usage:

		./test-tag-generic.sh <file|section|dataset> [id]
		./test-tag-generic.sh file
		./test-tag-generic.sh dataset 52697b5ae4b008632f496995

upload.py
---------

Upload a directory of files to Medici.

