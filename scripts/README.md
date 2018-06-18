Setup, Interface, and Helper Scripts for Clowder
===============================================

create-account.sh
-----------------

A script that takes the name of the server to be added as a  single argument. The script will generate a unique password, encrypt the password and print the mongo command to insert this entry into the list of users. This script will not actually execute the command! To insert the account simple copy and paste the code and use the mongo shell.

For this to work you will need to have passlib and bcrypt installed as python modules. This can be done using `pip install passlib brcypt`. It is highly recommended to use a virtual environment for this.

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

Pass a file to the Clowder extraction bus for metadata extraction.  Prints returned JSON to the screen.

Usage:

		> ./extract.py <file|URL>

geostream.py
------------

A simple example script showing how to query the Clowder geostreaming API.

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

Upload a directory of files to Clowder.

