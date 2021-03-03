OFFLINE VERSION OF UPDATES:

Following are scripts that can be run to update the database. Some of these updates can
take a long time, these scripts can be run beforehand as well as afterwards to apply
the updaates. These scripts will first set the flag to indicate the update has been
applied, preventing clowder from running the update during a migration. These scripts
are named with the actual name of the update .js

- update-avatar-url-to-https.js


MISCELLANEOUS SCRIPTS:

- fix-counts.js: script to redo the counts in clowder

- UpdateUserId.js: Adds user_id to documents in extractions collection in clowder mongo db. Uses author id in uploads.files if exists, else it takes the author id from datasets collection. Usage: mongo clowder UpdateUserId.js
