Setup, Interface, and Helper Scripts for Medici
===============================================

exinfo.py
----------

Connect to the RabbitMQ and get a list of all queues associated with a specific exchange. Next it will list for each queue the number of messages waiting, the number of messages currently being processed, as well as a list of all hosts that are connected to that specific queue.

Usage:

    ./exinfo.py

Output:

  wordCount :
    messages waiting    : 0
    messages processing : 0
    hosts :
      127.0.0.1


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

update-medici.py
----------------

Pull most recent version of Medici from repository and deploy.  Sets up upstart scripts.  In most cases setup as a cron
job running every 15 minutes or so.

Usage:

		> ./update-medici.py

upload.py
---------

Upload a directory of files to Medici.

