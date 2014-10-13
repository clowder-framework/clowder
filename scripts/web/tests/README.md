tests.php
--------

Web application to test the functionality of the extraction service behind a specified Medici instance. Note, test.php
is a helper script for this script.
	
Usage:

		Go to http://localhost/tests.php

tests.py
--------

Command line version of the above for use as a cron job.  Places output in the same folder so that web application can 
view the previous run.

Usage:

		> ./tests.py
