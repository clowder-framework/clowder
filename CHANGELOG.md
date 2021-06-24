# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## Unreleased

### Fixed
- Added index for comments, will speed up index creation

## 1.17.0 - 2021-04-29

### Fixed
- Close channel after submitting events to RabbitMQMessageService.

### Added
- Endpoint `/api/datasets/createfrombag` to ingest datasets in BagIt format. Includes basic dataset metadata, files,
  folders and technical metadata. Downloading datasets now includes extra Datacite and Clowder metadata.
- Endpoint `/api/files/bulkRemove` to delete multiple files in one call. [#12](https://github.com/clowder-framework/clowder/issues/12)
- Log an event each time that a user archives or unarchives a file.
- Persist name of message bus response queue, preventing status messages from getting lost after a reboot.

### Changed
- Updated Sphinx dependencies due to security and changes in required packages.

## 1.16.0 - 2021-03-31

### Fixed
- Remove the RabbitMQ plugin from the docker version of clowder

### Added
- Added a `sort` and `order` parameter to `/api/search` endpoint that supports date and numeric field sorting. 
  If only order is specified, created date is used. String fields are not currently supported.
- Added a new `/api/deleteindex` admin endpoint that will queue an action to delete an Elasticsearch index (usually prior to a reindex).
- JMeter testing suite.

### Changed
- Consolidated field names sent by the EventSinkService to maximize reuse.
- Add status column to files report to indicate if files are ARCHIVED, etc.
- Reworked auto-archival configuration options to make their meanings more clear.

## 1.15.1 - 2021-03-12

### Fixed
- Several views were throwing errors trying to access a None value in `EventSinkService` when a user was not logged in. 
  Replaced `get()` with `getOrElse()`.
- Consolidated field names sent by the EventSinkService to maximize reuse.
- Changed `EventSinkService` logging to debug to minimize chatter.
- Don't automatically create eventsink queue and bind it to eventsink exchange. Let clients do that so that we don't 
  have a queue for the eventsink filling up if there are no consumers.

## 1.15.0 - 2021-03-03

### Added
- CSV/JSON previewer using [Vega](https://vega.github.io/).
- Previewer for FBX files.
- `created` search option for filtering by upload/creation date of resource.
- `EventSinkService` to track user activity. All events are published to the message queue. Multiple consumers are 
  available in [event-sink-consumers](https://github.com/clowder-framework/event-sink-consumers).

### Fixed
- Clowder will no longer offer a Download button for a file until it has been PROCESSED.
- When space created through api the creator was not added to space as admin [#179](https://github.com/clowder-framework/clowder/issues/179).

### Changed
- `/api/me` will now return some of the same information as response headers. Can be used by other services to single 
  sign on when running on same host.
- `RabbitMQPlugin` has been split into `ExtractorRoutingService` and `MessageService` to isolate the rabbitmq code from 
  the extraction code.

### Removed
- the toolserver is no longer build as part of clowder since it is no longer maintained. We are working on a
  newer version that will be included in future versions of clowder.

## 1.14.1 - 2021-02-02

- Google will no longer work as login provider, we are working on this issue [#157](https://github.com/clowder-framework/clowder/issues/157).
- If non local accounts are used the count can be wrong. Use the [fixcounts](https://github.com/clowder-framework/clowder/blob/develop/scripts/updates/fix-counts.js)
script to fix this.

### Fixed
- Error logging in with Orcid due to changed URL. [#91](https://github.com/clowder-framework/clowder/issues/91)
- Fixed error in url for Twitter login.
- Users count was not correct if using anything else but local accounts. [#136](https://github.com/clowder-framework/clowder/issues/136)
- Files were not properly reindexed when the Move button was used to move a file into or out of a folder in a dataset. 
- When adding a file to a dataset by URL, prioritize the URL `content-type` header over the file content type established
  by looking at the file name extension. [#139](https://github.com/clowder-framework/clowder/issues/139)
- Wrap words across lines to stay within interface elements. [#160](https://github.com/clowder-framework/clowder/issues/160)

## 1.14.0 - 2021-01-07

### Added
- Added a new `/api/reports/metrics/extractors` report for summarizing extractor usage by user. Database administrators
  can use `scripts/updates/UpdateUserId.js` to assign user IDs to older extraction event records based on resource ownership
  in order to improve the accuracy of the report for older data.

### Changed
- `api/reports/storage/spaces` endpoint now accepts a space parameter for ID rather than requiring a space filter.
- Datasets and collections in the trash are no longer indexed for discovery in search services.
- Switched to loading the 3DHOP libraries used by `viewer_hop.js` from http://vcg.isti.cnr.it/3dhop/distribution to https://3dhop.net/distribution. The new server is a safer https server.

## 1.13.0 - 2020-12-02

### Added
- Ability to submit multiple selected files within a dataset to an extractor.
- Support for Amplitude clickstream tracking. See Admin -> Customize to configure Amplitude apikey.
- UpdateUserId.js to scripts/updates. This code adds user_id to each document in extractions collection in mongodb. 
  user_id is taken from author id in uploads.files if exists, else it taken from author id in datasets collection.

### Fixed
- An extractor with file matching set to `*/*` (all file types) would incorrectly send out dataset events.
- Space Editors can now delete tags on files, datasets and sections.
- GeospatialViewer previewer no longer shows if file does not contain geospatial layers.

## 1.12.2 - 2020-11-19

### Changed
- /api/reindex admin endpoint no longer deletes and swaps a temporary index, but reindexes in-place.

## 1.12.1 - 2020-11-05

### Fixed
- Error uploading to spaces that did not have extractors enabled/disabled (personel spaces).
- If extractor does not have any parameters, there would be an error message in the console of the browser.
- If the extractor did not have a user_id it would create an error and not record the event.

### Changed
- Docker Images are now pushed to [github container registry](https://github.com/orgs/clowder-framework/packages)

## 1.12.0 - 2020-10-19
**_Warning:_**
- This update modifies the MongoDB schema. Make sure to start the application with `-DMONGOUPDATE=1`.
- This update modifies information stored in Elasticsearch used for text based searching. Make sure to initiate a reindex 
  of Elasticsearch from the Admin menu or by `POST /api/reindex`.

### Added
- Global extractors page now shows more information, including submission metrics, logs (using Graylog), job history and
  extractors maturity. Extractors can be grouped using labels. User can filter list of extractors by labels, space, trigger
  and metadata key.
- Users have more refined options to set extractors triggers at the space level. They can now follow global settings, 
  disable and enable triggers.
- Ability to set chunksize when downloading files. Set defult to 1MB from 8KB. This will result in faster downloads and 
  less CPU usage at the cost of slightly more memory use.
- Support for parsing of Date and Numeric data in new metadata fields. New search operators <, >, <=, >= have been 
  added to search API now that they can be compared properly.
- Track user_id with every extraction event. [#94](https://github.com/clowder-framework/clowder/issues/94)
- Added a new storage report at `GET api/reports/storage/spaces/:id` for auditing user storage usage on a space basis.
- The file and dataset metrics reports also have support for since and until ISO8601 date parameters.
- Added `viewer_hop` a 3D models previewer for `*.ply` and `*.nxz` files. Added `mimetype.nxz=model/nxz` and `mimetype.NXZ=model/nxz` as new mimetypes in `conf/mimetypes.conf`

### Fixed
- Ignore the `update` field when posting to `/api/extractors`. [#89](https://github.com/clowder-framework/clowder/issues/89)
- Search results were hardcoded to be in batches of 2.
- Fixed permissions checks on search results for search interfaces that would cause misleading counts. [#60](https://github.com/clowder-framework/clowder/issues/60)

## 1.11.2 - 2020-10-13

### Fixed
- Clowder healthcheck was not correct, resulting in docker-compose never thinking it was healthy. This could also result 
  in traefik not setting up the routes. 

## 1.11.1 - 2020-09-29

### Added
- Added healtz endpoint that is cheap and quick to return, useful for kubernetes live/ready checks.

### Fixed
- Fixed health check script when using custom path prefix.
- Proxy will no correctly handle paths that end with a / at the end.
- Submitting an extraction will always return a 500 error, see [#84](https://github.com/clowder-framework/clowder/issues/84)
- Added MongoDB index for `folders.files`.

### Changed
- Updated update-clowder script to work with migration to github. Has the ability now to push a message to MSTEAMS as well as influxdb.

## 1.11.0 - 2020-08-31

### Added
- Downloaded datasets now include DataCite v4 XML files in the output /metadata folder.
- Script to clean extractors' tmp files `scripts/clean-extractors-tmpfiles/`.
- Script for RabbitMQ error queue cleanup `scripts/rmq-error-shovel/`.
- Ability to use HTML formatting in the welcome message on the home page. [#51](https://github.com/clowder-framework/clowder/issues/51)
- Expose a read-only list of extractors to all users.

### Changed
- Improved test script `scripts/tester/tester.sh` to report successes once a day.

### Fixed
- Escape colon characters on search values for search box and advanced search to allow those values in a search.
- Typesafe now only offers https access. [#49](https://github.com/clowder-framework/clowder/issues/49)
- If uploading files by url > 2147483647 it would fail. [#54](https://github.com/clowder-framework/clowder/issues/54)


## 1.10.1 - 2020-07-16

### Fixed
- Queue threads (e.g. Elasticsearch indexer) will no longer crash permanently if the queue connection to Mongo is lost temporarily.
- Docker images would not build correctly on GitHub.
- If monitor HTTP server would crash, it would not restart correctly.
- Don't call server side twice when rendering list of files on dataset page.
  [#7](https://github.com/clowder-framework/clowder/issues/7)
- Fixed Sphinx build errors and switched to using pipenv. Now building docs on [readthedocs](https://clowder-framework.readthedocs.io/en/latest/).

### Added
- GitHub artifacts can be uploaded using SCP to remote server.

## 1.10.0 - 2020-06-30

### Added
- Ability to mark multiple files in a dataset and perform bulk operations (download, tag, delete) on them at once.

### Fixed
- Return thumbnail as part of the file information.
  [#8](https://github.com/clowder-framework/clowder/issues/8)
- Datasets layout on space page would sometimes have overlapping tiles.

### Changed
- mongo-init script with users would return with exit code -1 if user exists, now returns exit code 0.

## 1.9.0 - 2020-06-01

**_Warning:_ This update modifies information stored in Elasticsearch used for text based searching. To take advantage 
of these changes a reindex of Elasticsearch is required. A reindex can be started by an admin from the Admin menu.**

### Added
- Ability to delete extractor, both from API and GUI.
  [CATS-1044](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1044)
- Add tags endpoint now returns the added tags.
  [CATS-1053](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1053)
- Ability to search by creator name and email address for all resources.
- List Spaces/Datasets/Collections created by each user on their User Profile page.
  [CATS-1056](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1056)
- Allow user to easily flip through the files in a dataset.
  [CATS-1058](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1058)
- Ability to filter files and folders in a dataset when sorting is enabled.
- Visualize existing relations between datasets on the dataset page. This can be extended other resource types.
  [CATS-1000](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1000)
- S3ByteStorageService verifies bucket existence on startup and creates it if it does not exist.
  [CATS-1057](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1057)
- Can now switch storage provider in Docker compose, for example S3 storage. See env.example for configuration options.
- Script to test extractions through the API.
  
### Fixed
- When adding tags to a section of an image, show the new tag without having to refresh the page.
  [CATS-1053](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1053)

### Changed
- Removed buttons to remove datasets from spaces and collections from certain pages. Moved Remove button for 
  subcollections to right side of page to be consistent with other pages.
  [CATS-1055](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1055)
- Upgraded swagger documentation to openapi v3.0.1.

## 1.8.4 - 2020-05-15
**_Warning:_ This update modifies how information is stored in Elasticsearch for text based searching. To take advantage 
of these changes a reindex of Elasticsearch is required. This can be started by an admin either from GUI or through the API.**

### Fixed
- Fixed a bug related to improper indexing of files in nested subfolders, which could also affect searching by parent dataset.

## 1.8.3 - 2020-04-28
**_Warning:_ This update modifies how information is stored in Elasticsearch for text based searching. To take advantage 
of these changes a reindex of Elasticsearch is required. This can be started by an admin either from GUI or through the API.**

### Changed
- Elasticsearch indexer will now store new metadata fields as strings to avoid unexpected behavior on date fields.
- When reindexing use a temporary index to reindex while the current one is in use then swap.

### Fixed
- Ability to delete tags from sections and files on the file page. 
  [CATS-1046](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1046)
  [CATS-1042](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1042)
- User-owned resources will now appear in search results regardless of space permissions. 
- Updating space ownership for datasets and collections will correctly reindex those resources for searches.
- Missing index in statistics which would slow down system when inserting download/views.

### Added
- GitHub Actions to compile and test the code base, create documentation and docker images.
- Code of Conduct as MD file (will be displayed by GitHub).
- Templates for Bug, Feature and Pull Request on GitHub.

## 1.8.2 - 2020-02-19

### Fixed
- Use the passed-in length within S3ByteStorageService.save.

## 1.8.1 - 2020-02-05

### Removed
- Removed unused RDF libraries. This was probably used by the rdf/xml functionality that was removed a while back but 
the dependencies were never removed.
- Removed Jena validation of JSON-LD metadata. It was creating a blank graph and clients couldn't upload metadata when 
Clowder runs in a location that doesn't not have access to https://clowderframework.org/contexts/metadata.jsonld. 

### Added
- Scripts to migrate files on disk AWS S3.
  [CATS-1034](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1034)

### Changed
- Include collection prefix in path when saving to S3.
- Include length of file in `FileService` when saving the bytes to any backend service. This helps optimize S3 implementation.
- Upgraded sbt from 0.13.0 to 0.13.6 to fix build failures.
  [CATS-1038](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1038)

### Fixed
- Calling api/Files.removeFile should no longer decrement related counters twice.
  [CATS-929](https://opensource.ncsa.illinois.edu/jira/browse/CATS-929)

## 1.8.0 - 2019-11-06
**_Warning:_ This update adds a new permission for archiving files and adds it to the Admin role. Please make sure
to run Clowder with the `MONGOUPDATE` flag set to update the database.**

### Changed
- `/api/search` endpoint now returns JSON objects describing each result rather than just the ID. This endpoint has three
  new parameters - from, size, and page. The result JSON objects will also return pagination data such as next and 
  previous page if Elasticsearch plugin is enabled and these parameters are used.
- S3ByteStorageService now uses AWS TransferManager for saving bytes - uploads larger than ~1GB should now save more reliably.
- `/api/search` endpoint now returns JSON objects describing each result rather than just the ID.
- Clean up docker build. Use new buildkit to speed up builds. Store version/branch/git as environment variables in 
  docker image so that they can be inspected at runtime with Docker.
- Extractors are now in their own docker-compose file. Use Traefik for proxy. Use env file for setting options.
- Utilize bulk get methods for resources widely across the application, including checking permissions for many resources
  at once. Several instances where checks for resource existence were being done multiple times (e.g. in a method and then
  in another method the first one calls) to reduce MongoDB query load. These bulk requests will also report any missing
  IDs in the requested list so developers can handle those appropriately if needed.
- Removed mini icons for resource types on tiles.
  [CATS-1031](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1031)
- Cleaned up pages to list and enable extractors. Added description of what the page does. Added links to extraction info 
  pages. Removed authors from table. 

### Added
- Ability to pass runtime parameters to an extractor, with a UI form dynamically generated UI from extractor_info.json.
  [CATS-1019](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1019)
- Infinite scrolling on search return pages.
- Trigger archival process automatically based on when a file was last viewed/downloaded and the size of the file.
- Script to check if mongodb/rabbitmq is up and running, used by Kubernetes Helm chart.
- Queuing system that allows services such as Elasticsearch and RabbitMQ to store requested actions in MongoDB
  for handling asynchronously, allowing API calls to return as soon as the action is queued rather than waiting for
  the action to complete.
- New extractors monitor docker image to monitor extraction queues. Monitor app includes UI that shows selected 
  information about the extractors
- New `/api/thumbnails/:id` endpoint to download a thumbnail image from ID found in search results.
- New utility methods in services to retrieve multiple MongoDB resources in one query instead of iterating over a list.
- Support for MongoDB 3.6 and below. This required the removal of aggregators which can result in
  operations taking a little longer. This is needed to support Clowder as a Kubernetes Helm chart.
  [CATS-806](https://opensource.ncsa.illinois.edu/jira/browse/CATS-806)
- New Tree view as a tab in home page to navigate resources as a hiearchical tree (spaces, collections, datasets, 
  folders and files). The tree is lazily loaded using a new endpoint `api/tree/getChildrenOfNode`.

### Fixed
- Downloading metrics reports would fail due to timeout on large databases. Report CSVs are now streamed
  to the client as they are generated instead of being generated on the server and sent at the end.
- Social accounts would not properly be added to a space after accepting an email invite to join it.
- Fixed bug where extractors monitor will not print error, but just return 0 if queue is not found.
- Pagination controls are now vertically aligned and use unescaped ampersands.
- Changing the page size on dataset, collection, space listings would not properly update elements visible on the page.
  [CATS-1030](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1030)
- Added a max of 100 status messages on the  page listing all extractions. Before this trying to list all extractions in 
  the system was causing the JVM to run out of memory.
  [CATS-1032](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1032)
- Added padding to the top of the footer so that the superadmin notification does not cover buttons and the buttons
  at the end of forms are not too close to the footer and difficult to see.
  
## 1.7.4 - 2019-10-21

### Fixed
- Extractors that don't specify EXTRACT as categories don't show up in the manual submission page.
  [CATS-1023](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1023)

## 1.7.3 - 2019-08-19

### Fixed
- Fixed bug where metadata field names in the search box were being forced to lowercase, omitting search results due to
  case sensitivity.

## 1.7.2 - 2019-08-01

### Fixed
- RabbitMQ plugin would throw an exception if rabbitmq server restarted. 
  [CATS-1012](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1012)

### Changed
- Changed internal regex wrapping syntax on search box queries to better handle complex terms.
- Updated core documentation, both the content and version of Sphinx.
  [CATS-865](https://opensource.ncsa.illinois.edu/jira/browse/CATS-865)

## 1.7.1 - 2019-07-09

### Fixed
- Logging was accidently set to DEBUG, reverted it back to INFO

## 1.7.0 - 2019-07-08

**This update will require a reindex of Elasticsearch. After deploying the update either call `POST to /api/reindex`
or navigate to the `Admin > Indexes` menu and click on the `Reindex` button.**

### Fixed
- HTTP 500 error when posting new metadata.

### Added
- Add archive button on file page which can trigger archive extractor to archive this file.
- Added S3ByteStorageService for storing uploaded bytes in S3-compatible buckets.
  [CATS-992](https://opensource.ncsa.illinois.edu/jira/browse/CATS-992)
- Added support for archiving files in Clowder and preparing an admin email if user attempts to download archived file.
  [CATS-981](https://opensource.ncsa.illinois.edu/jira/browse/CATS-981)
- Listen for heartbeat messages from extractors and update list of registered extractors
  based on extractor info received. For extractors using this method they will not need
  to manually register themselves through API to be listed.
  [CATS-1004](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1004)
- Added support for extractor categories that can be used for retrieving filtered lists of extrators by category.

### Changed
- Improved Advanced Search UI to retain search results between navigations.
  [CATS-1001](https://opensource.ncsa.illinois.edu/jira/browse/CATS-1001)
- Display more info on the manual submission page, link to ExtractorDetails view.
  [CATS-959](https://opensource.ncsa.illinois.edu/jira/browse/CATS-959)
- Clean up of Search pages. Renamed Advanced Search to Metadata Search. Added search form and Metadata Search link to 
  main Search page. Consistent and improved search results on both search pages.
  [CATS-994](https://opensource.ncsa.illinois.edu/jira/browse/CATS-994)
- Updated the mongo-init docker image to aks for inputs if not specified as
  an environment variable.
  `docker run -ti --rm --network clowder_clowder clowder/mongo-init`
- Rework of the Elasticsearch index to include improved syntax and better documentation on the basic search page.

## 1.6.2 - 2019-05-23

### Fixed
- Mimetype of RabbitMQ message body should be `application/json` instead of `application\json`.
  [GH-12](https://github.com/ncsa/clowder/issues/12)

## 1.6.1 - 2019-05-07

### Fixed
- A double quote character in a metadata description disallowing edit of metadata definition.
  [CATS-991](https://opensource.ncsa.illinois.edu/jira/browse/CATS-991)
- About page should no longer show "0 Bytes", counts should be more accurate.
  [CATS-779](https://opensource.ncsa.illinois.edu/jira/browse/CATS-779)
- Fixed creation of standard vocabularies within a space.
- Slow load times in dataset page by removing queries for comments and tags on files within a dataset.
  [CATS-999](https://opensource.ncsa.illinois.edu/jira/browse/CATS-999)
- Send file delete events over RabbitMQ when a folder is deleted that contains files.
  [CATS-995](https://opensource.ncsa.illinois.edu/jira/browse/CATS-995)

### Changed
- Improved the HTTP return codes for the generic error handlers in Clowder.
- Adjusted display of Advanced Search matching options to include (AND) / (OR).
  [CATS-998](https://opensource.ncsa.illinois.edu/jira/browse/CATS-998)
- Dataset page does not show comments on files within the dataset anymore.
- dataset-image previewer turned off by default since it is expensive for datasets with many files but does not much
  information to the dataset page.
- Removed unused queries for comments throughout the application.

### Added
- Script to cleanup/migrate userpass account data to cilogon accounts.

## 1.6.0 - 2019-04-01

### Added
- User API Keys are now sent over to extractors (instead of the global key). If user doesn't provide a user key with the
  request, one is gets created with name `_extraction_key`. If no user is available, the global key is used.
  [CATS-901](https://opensource.ncsa.illinois.edu/jira/browse/CATS-901)
- Ability to cancel a submission to the extraction bus. A cancel button is available in the list of extraction events.
  [CATS-970](https://opensource.ncsa.illinois.edu/jira/browse/CATS-970)
- Allow user to create and manage controlled vocabularies within Clowder.
- Cascade creation and deletion of global metadata definitions to all spaces.
  [CATS-967](https://opensource.ncsa.illinois.edu/jira/browse/CATS-967)
- New view for files and datasets offering a table view of the attached metadata.
- Add SUBMITTED event on the GUI of extractions and pass this submitted event id in extraction message.
  [CATS-969](https://opensource.ncsa.illinois.edu/jira/browse/CATS-969)
- Send email address of user who initiated an extraction so that extractors can notify user by email when job is done.
  [CATS-963](https://opensource.ncsa.illinois.edu/jira/browse/CATS-963)
- Extraction history for dataset extractors is now displayed on dataset page.
  [CATS-796](https://opensource.ncsa.illinois.edu/jira/browse/CATS-796)
- Script to verify / fix mongo uploads collection if file bytes are missing.
- Additional columns added to reporting API endpoint including date, parent resources, file location, size and ownership.
- Previewer for displaying internal contents of Zip Files.
  [CATS-936](https://opensource.ncsa.illinois.edu/jira/browse/CATS-936)
- Additional API endpoints for adding and retrieving file metadata in bulk.
  [CATS-941](https://opensource.ncsa.illinois.edu/jira/browse/CATS-941)
- Optional form to adding multiple metadata fields at once via UI under "Advanced."
  [CATS-940](https://opensource.ncsa.illinois.edu/jira/browse/CATS-940)
- CONTAINS operator added to Advanced Search interface and wildcards (e.g. ".*") now supported in search box.
  [CATS-962](https://opensource.ncsa.illinois.edu/jira/browse/CATS-962)
- New widget to add standard name mappings.
  [BD-2321](https://opensource.ncsa.illinois.edu/jira/browse/BD-2321)
- Add a new event for extractors "dataset.files.added" that is triggered when a user uploads multiple files at once via UI.
  [CATS-973](https://opensource.ncsa.illinois.edu/jira/browse/CATS-973)
- `/api/search` endpoint now supports additional flags including tag, field, datasetid, and others detailed in SwaggerAPI.
  [CATS-968](https://opensource.ncsa.illinois.edu/jira/browse/CATS-968)
- Add a dropdown to Advanced Search UI for filtering by space.
  [CATS-985](https://opensource.ncsa.illinois.edu/jira/browse/CATS-985)
  
### Fixed
- Enhancements to reporting date and string formatting. Space listing on spaces report and on New Collections page now 
  correctly return space list depending on user permissions even if instance is set to private.
- GeospatialViewer previewer added content to incorrect tab.
  [CATS-946](https://opensource.ncsa.illinois.edu/jira/browse/CATS-946)
- Handle 403 errors appropriately from the ZipFile Previewer.
  [CATS-948](https://opensource.ncsa.illinois.edu/jira/browse/CATS-948)
- Error when showing ordered list of tags and Elasticsearch included an empty tag. Also removed the ability to add empty 
  tags both from the UI as well as the API.
  [CATS-952](https://opensource.ncsa.illinois.edu/jira/browse/CATS-952)
- In SuperAdmin mode, the Spaces page will correctly show all spaces.
  [CATS-958](https://opensource.ncsa.illinois.edu/jira/browse/CATS-958)
- In FileMetrics report, space and collection IDs are only added to the report once to avoid repeating.
- Apply 'max' restriction when fetching dataset file lists earlier, to avoid long load times for certain previewers.
  [CATS-899](https://opensource.ncsa.illinois.edu/jira/browse/CATS-899)
- Unable to edit metadata definition when description included newlines characters.
- Fixed user events not being created. Migrated to EventType enum class for tracking event types.
  [CATS-961](https://opensource.ncsa.illinois.edu/jira/browse/CATS-961)
- Loading indicator should now show on datasets page while files and folders are loading.
  
### Changed 
- Extraction events on File and Dataset pages are now grouped by extractor. The events view has been moved to a tab for both,
  and the File pages now have metadata and comments under tabs as well.
  [CATS-942](https://opensource.ncsa.illinois.edu/jira/browse/CATS-942)
- Cleaned up clowder init code docker image see README.
- Updated Sphinx dependencies in `doc/src/sphinx/requirements.txt` for building documentation.

## 1.5.2 - 2018-12-14

### Fixed
- Filtering using Rabbitmq does not take into account exchanges. [CATS-954](https://opensource.ncsa.illinois.edu/jira/browse/CATS-954)
- The icon used for spaces in a few places was the wrong icon. [CATS-955](https://opensource.ncsa.illinois.edu/jira/browse/CATS-955)
- GeoJSON dataset previewer loads all files in a dataset. [CATS-956](https://opensource.ncsa.illinois.edu/jira/browse/CATS-956)

## 1.5.1 - 2018-11-07

### Fixed
- Previewer tabs on the file page were showing default title "Preview" instead of the one defined in the previewer manifest.
  [CATS-939](https://opensource.ncsa.illinois.edu/jira/browse/CATS-939)
- Remove signup button if signup is disabled using `securesocial.registrationEnabled=false`.
  [CATS-943](https://opensource.ncsa.illinois.edu/jira/browse/CATS-943)
- Add flag smtp.mimicuser=false that will force emails to always come from the user defined in the configuration file
  instead of the Clowder user.
  [CATS-944](https://opensource.ncsa.illinois.edu/jira/browse/CATS-944)

## 1.5.0 - 2018-10-23
**_Warning:_ This update will reset all permissions assigned to roles. 
Please review the defintion of roles in your instance before and after the upgrade to make sure that they match 
your needs.**

### Added
- Ability to specify whether a previewer fires on a preview object in the database (`preview: true`) or the 
  raw file/metadata (`file:true`) in the previewer `package.json` file.
  [CATS-934](https://opensource.ncsa.illinois.edu/jira/browse/CATS-934)
- Support for adding multiple comma-separated tags on dataset and file pages.
- Ability to send events to extractors only if they are enabled in a space. Refactored some of the extraction code.
  Added more explicit fields to the extraction message regarding event type, source and target. Tried to keep backward
  compatibility.
  [CATS-799](https://opensource.ncsa.illinois.edu/jira/browse/CATS-799)
- Update Docker image's `custom.conf` to allow for override of Mongo and RabbitMQ URIs.
  [BD-2181](https://opensource.ncsa.illinois.edu/jira/browse/BD-2128)
- Script to add a service account directly into Mongo `scripts/create-account.sh`.
- Added a new view to display Extractor Details.
  [CATS-892](https://opensource.ncsa.illinois.edu/jira/browse/CATS-892)
- New API endpoints for proxying GET, POST, PUT, and DELETE requests through Clowder.
  There are still some issues with POST depending on the backend service (for example Geoserver).
  [CATS-793](https://opensource.ncsa.illinois.edu/jira/browse/CATS-793)
  [CATS-889](https://opensource.ncsa.illinois.edu/jira/browse/CATS-889)
  [CATS-895](https://opensource.ncsa.illinois.edu/jira/browse/CATS-895)
- Ability to enable disable extractors at the instance level (versus space level).
  [CATS-891](https://opensource.ncsa.illinois.edu/jira/browse/CATS-891)
- Add flag to specify not to run any extraction on uploaded files to dataset. By default, we always run extraction on 
  uploaded files to dataset.
  [BD-2191](https://opensource.ncsa.illinois.edu/jira/browse/BD-2191)
- Tracking of view and download counts for Files, Datasets and Collections.
  [CATS-374](https://opensource.ncsa.illinois.edu/jira/browse/CATS-374)
  [CATS-375](https://opensource.ncsa.illinois.edu/jira/browse/CATS-375)
- Ability to downloads CSV reports of usage metrics for Files, Datasets and Collections via new API endpoints.
  [CATS-918](https://opensource.ncsa.illinois.edu/jira/browse/CATS-918)
- Ability to provide API key in HTTP X-API-Key request header.
  [CATS-919](https://opensource.ncsa.illinois.edu/jira/browse/CATS-919)
- Extraction history for dataset extractors is now displayed on dataset page.
  [CATS-796](https://opensource.ncsa.illinois.edu/jira/browse/CATS-796)
- API route for creating a new folder now returns folder information on success.
- Offline updates for mongodb added to `scripts/updates`.
  
### Changed 
- If no local password and only 1 provider, redirect to the provider login page immediately.
  [CATS-868](https://opensource.ncsa.illinois.edu/jira/browse/CATS-868)
- Changing gravatar picture to be https in the database
  [CATS-882](https://opensource.ncsa.illinois.edu/jira/browse/CATS-882)
- Modified zenodo.json file to include more Orcid IDs.
  [CATS-884](https://opensource.ncsa.illinois.edu/jira/browse/CATS-884)
- Display more extractor information in each Space's "Update Extractors" view.
  [CATS-890](https://opensource.ncsa.illinois.edu/jira/browse/CATS-890)
- Clean up of the list of previewers page `/previewers/list` and added link to it from the admin menu.
  [CATS-934](https://opensource.ncsa.illinois.edu/jira/browse/CATS-934)

### Fixed
- In a private mode, a superadmin can now see datasets in a space that he/she is not part of.
  [CATS-881](https://opensource.ncsa.illinois.edu/jira/browse/CATS-881)
- In private mode, users used to be able to see the list of spaces. Now they cannot.
  [CATS-887](https://opensource.ncsa.illinois.edu/jira/browse/CATS-887)
- In DatasetService, rename function of findByFileID to findByFileIdDirectlyContain. Add a new function 
  findByFileIdAllContain to return back datasets directly and indirectly contain the given file. 
  [CATS-897](https://opensource.ncsa.illinois.edu/jira/projects/CATS/issues/CATS-897)
- Parameters subdocument is now properly escaped in rabbitmq message.
  [CATS-905](https://opensource.ncsa.illinois.edu/jira/browse/CATS-905)
- Removed erroneous occurrences of .{format} from swagger.yml.
  [CATS-910](https://opensource.ncsa.illinois.edu/jira/browse/CATS-910)
- Previews on the file page are now shown whether they are because of a `Preview` entry on the file 
  added by an extractor or by `contentType` in `package.json` for each previewer.
  [CATS-904](https://opensource.ncsa.illinois.edu/jira/browse/CATS-904)

## 1.4.3 - 2018-09-26

### Fixed 
- File model not being deleted when it contains previews or sections.
  [CATS-928](https://opensource.ncsa.illinois.edu/jira/browse/CATS-928)
  
## 1.4.2 - 2018-08-21

### Fixed 
- Extractors printing the private key in error extraction status messages.
  [CATS-903](https://opensource.ncsa.illinois.edu/jira/browse/CATS-887)

## 1.4.1 - 2018-08-21

### Fixed
- LDAP provider now properly sets identityId.userId.
  [CATS-911](https://opensource.ncsa.illinois.edu/jira/browse/CATS-911)

## 1.4.0 - 2018-05-04

### Added
- Ability to disable username/password login provider.
  [CATS-803](https://opensource.ncsa.illinois.edu/jira/browse/CATS-803)
- Track original file name used when file was originally uploaded.
  [SEAD-1173](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1173)
- LDAP authentication.
  [CATS-54](https://opensource.ncsa.illinois.edu/jira/browse/CATS-54)
- Ability for users to create their own API keys.
  [CATS-686](https://opensource.ncsa.illinois.edu/jira/browse/CATS-686)
- Abilty for CiLogon provider to filter by LDAP groups.
- *exact* flag on collection and dataset API endpoints that accept a *title* flag. This will use
  exact matching on the title field instead of regular expression fuzzy matching.
- Having a temporary trash option. Can be set with useTrash boolean in the configuration file.
  [CATS-780](https://opensource.ncsa.illinois.edu/jira/browse/CATS-780)
- Track last time a user logged in.
- Add logic to check rabbitmq, mongo, clowder ready before creating default users with Docker compose.
  [BD-2059](https://opensource.ncsa.illinois.edu/jira/browse/BD-2059)
- Add Jupyter notebook examples of how to interacting with Clowder endpoints for file, dataset, collection and
  spaces manipulation.    
- HTML previewer for text/html files.
  [CATS-861](https://opensource.ncsa.illinois.edu/jira/browse/CATS-861)

### Changed
- File and dataset GET metadata endpoints now include their corresponding IDs and resource type information.
  [CATS-718](https://opensource.ncsa.illinois.edu/jira/browse/CATS-718)
- Cleanup of docker build process and how Clowder in launched in Docker.
  [CATS-871](https://opensource.ncsa.illinois.edu/jira/browse/CATS-871)
- Serving gravatar picture over https instead of http.
  [CATS-882](https://opensource.ncsa.illinois.edu/jira/browse/CATS-882)
- When the metadata.jsonld has a contextURL instead of a JsObject or JsArray show a popup with the link of the
  context instead of creating a link.
  [CATS-842](https://opensource.ncsa.illinois.edu/jira/browse/CATS-842)
- Changed permissions for the editor role
  [CATS-921](https://opensource.ncsa.illinois.edu/jira/browse/CATS-921)

### Fixed
- Space admins can now delete metadata definitions.
  [CATS-880](https://opensource.ncsa.illinois.edu/jira/browse/CATS-880)
- Rolling log file wrote to wrong folder, now writes to logs folder.
- Now sends email when a user signs up using an external login provider. Due to this fix admins will receive an email
  a user logs on for the first time after this version is deployed when logging in with an external login provider.
  [CATS-483](https://opensource.ncsa.illinois.edu/jira/browse/CATS-483)
- Fixed dataset geospatial layer checkbox turn on/off and opacity.
  [CATS-837](https://opensource.ncsa.illinois.edu/jira/browse/CATS-837)
- Fixed GreenIndex previewer on clowder dataset page.
  [BD-1912](https://opensource.ncsa.illinois.edu/jira/browse/BD-1912)
- Only show the sort by dropdown in the collection page when the sort in memory flag is false.
  [CATS-840](https://opensource.ncsa.illinois.edu/jira/browse/CATS-840)
- Extraction status returns "Done" instead of "Processing" when one of the extractor fails
  [CATS-719](https://opensource.ncsa.illinois.edu/jira/browse/CATS-719)
- Avoid exception avoid exception in user events when unknown events don't match expected pattern (e.g. metadata events
  from another branch).
- Fixed bug where "show more results" would fail on Search.
  [CATS-860](https://opensource.ncsa.illinois.edu/jira/browse/CATS-860)
- Fixed bug where reindex of Elasticsearch would fail if extractors tried to index simultaneously.
  [CATS-856](https://opensource.ncsa.illinois.edu/jira/browse/CATS-856)
- Fixed bug of Account not active when using mongo-init to create user account.
  [BD-2042](https://opensource.ncsa.illinois.edu/jira/browse/BD-2042)
- Setting status for users on signup.
  [CATS-864](https://opensource.ncsa.illinois.edu/jira/browse/CATS-864)
- Person tracking previewer updated after changes to the associated metadata structure.
  [CATS-730](https://opensource.ncsa.illinois.edu/jira/browse/CATS-730)
- Hide incompatible extractors on `/datasets/:id/extractions` and `/files/:id/extractions` views.
  [CATS-875](https://opensource.ncsa.illinois.edu/jira/browse/CATS-875)
- Can now accept ToS even if account is not enabled.
  [CATS-834](https://opensource.ncsa.illinois.edu/jira/browse/CATS-834)

## 1.3.5 - 2018-02-23

### Fixed
- Modifying subject for emails being sent when a user is added to a space.
  [CATS-858](https://opensource.ncsa.illinois.edu/jira/browse/CATS-858)

## 1.3.4 - 2018-02-05

### Fixed
- Downloading datasets could take a long time before actual download
  started and could result in proxy timeouts.
  [CATS-795](https://opensource.ncsa.illinois.edu/jira/browse/CATS-795)

## 1.3.3 - 2017-12-21

### Added
- Endpoint to get a list of traversing paths from datasets to the parent
  folders of the given file.
  [CATS-811](https://opensource.ncsa.illinois.edu/jira/browse/CATS-811)
- clowder.upload.previews flag to application.conf to turn on/off
  previews in upload page.
  [CATS-813](https://opensource.ncsa.illinois.edu/jira/browse/CATS-813)

### Changed
- Send email with instructions when registerThroughAdmins=true.
  [CATS-791](https://opensource.ncsa.illinois.edu/jira/browse/CATS-791)
- Default showAll to true when listing spaces.
  [CATS-815](https://opensource.ncsa.illinois.edu/jira/browse/CATS-718)
- Move submit for extraction to the top on file page and dataset page.
  Remove parameter text field on Submit for Extraction page.
  [CATS-794](https://opensource.ncsa.illinois.edu/jira/browse/CATS-794)
- Add 'cat:' as prefix for typeOfAgent in UserAgent and ExtractorAgent
  constructors. Add filter or condition to check typeOfAgent is
  cat:extractor in getTechnicalMetadataJSON endpoint.
  [CATS-798](https://opensource.ncsa.illinois.edu/jira/browse/CATS-798)

### Fixed
- Dataset geospatial previewer now has a max of 20 layers shown by default.
  The dataset page was taking too long to load for datasets with lots of
  files because of this.
  [CATS-826](https://opensource.ncsa.illinois.edu/jira/browse/CATS-826)
- Dataset descriptions of sufficient length no longer cause the page to
  freeze in tiles view.
- Tags lists now showing up to 10000 entries when using elasticsearch.
  Was defaulting to 10.
  [SEAD-1169](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1169)
- Add js route to get the JSONLD metadata of a file.
  [GitHub-PR#2](https://github.com/ncsa/clowder/pull/2)
- Geostreams POST /sensors lat and long are reversed.
  [GLGVO-382](https://opensource.ncsa.illinois.edu/jira/browse/GLGVO-382)
- Edit license breaks on names with apostrophes in them.
  [CATS-820](https://opensource.ncsa.illinois.edu/jira/browse/CATS-820)

## 1.3.2 - 2017-08-15

### Fixed
- Elasticsearch searches are broken.
  [CATS-783](https://opensource.ncsa.illinois.edu/jira/browse/CATS-783)

## 1.3.1 - 2017-07-24

### Fixed
- Upgraded Postgres driver to 42.1.1. Geostreams API was throwing an a "canceling statement due to user request" error
  for large datapoint queries with Postgresql versions 9.5+.
  [CATS-771](https://opensource.ncsa.illinois.edu/jira/browse/CATS-771)
- When doing a reindex all indices in elasticsearch were removed.
  [CATS-772](https://opensource.ncsa.illinois.edu/jira/browse/CATS-772)
- CILogin properly works by specifying bearer token in header.
- Collections id properly removed from child collections when deleting parent collection.
  [CATS-774](https://opensource.ncsa.illinois.edu/jira/browse/CATS-774)
- The modal for adding a relationship between sensors and datasets is now on top of the background and can be clicked.
  [CATS-777](https://opensource.ncsa.illinois.edu/jira/browse/CATS-777)

## 1.3.0 - 2017-06-20

### Added
- Only show spaces, collections and datasets that are shared with other users under 'explore' tab.
  In application.conf, this is set by the *showOnlySharedInExplore* whose default value is false.
- Ability to download a collection. Download collection and dataset both use BagIt by default.
  [CATS-571](https://opensource.ncsa.illinois.edu/jira/browse/CATS-571)
- Ability to mention other users using '@' in a comment on a file or dataset. Mentioned users will receive a
  notification email and a notice in their event feed.
  [SEAD-781](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-781)
- Description field to metadata definition.
  [SEAD-1101](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1101)
- Improved documentation for the user interface.

### Changed
- Ability to search datapoints, averages and trends using a start and end time.
- Ability to change how many items are displayed on the listing pages.
  [SEAD-1149](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1149)
- When downloading datasets there is no folder with the id for each file.
  [SEAD-1038](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1038)
- Datasets can be copied with *Download Files* and *View Dataset* permissions instead of just the owner.
  [SEAD-1162](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1162)
- Selections can now be downloaded, tagged or deleted directly from the top menu bar through the new action dropdown.
- Can assign any GeoJSON geometry to Geostreams entities in the PostGIS database, not just lat/long coordinates.
  [CATS-643](https://opensource.ncsa.illinois.edu/jira/browse/CATS-643)
- Attributes filter on datapoint GET endpoint can now include ':' to restrict to datapoints that match a specific value
  in their attributes.
  [CATS-762](https://opensource.ncsa.illinois.edu/jira/browse/CATS-762)

### Fixed
- Binning on geostreaming api for hour and minutes.
  [GEOD-886](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-886)
- Returning the last average when semi is not selected and there is no binning by season.
- Removing space id from collections and datasets when the space is deleted.
  [CATS-752](https://opensource.ncsa.illinois.edu/jira/browse/CATS-752)
- Copy of dataset. When a dataset is copied, the newly created dataset will have the system generated metadata, previews,
  and thumbnails for the dataset and the files.
  [CATS-729](https://opensource.ncsa.illinois.edu/jira/browse/CATS-729)
- Return *409 Conflict* when submitting file for manual extraction and file is not marked as *PROCESSED*.
  [CATS-754](https://opensource.ncsa.illinois.edu/jira/browse/CATS-754)
- Listing of files in dataset breaks when user permissions in a space are set to View.
  [CATS-767](https://opensource.ncsa.illinois.edu/jira/browse/CATS-767)
- Reenabled byte counts on index and status pages.
- Miscellaneous bug fixes.

## 1.2.0 - 2017-03-24

### Added
- Docker container to add normal/admin users for Clowder.
  [BD-1167](https://opensource.ncsa.illinois.edu/jira/browse/BD-1167)
- ORCID/other ID expansion - uses SEAD's PDT service to expand user ids entered as creator/contact metadata so they show
  as a name, link to profile, and email(if available).
  [SEAD-1126](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1126)
- Can add a list of creators to a Dataset and publication request(Staging Area plugin). This addition also supports
  type-in support for adding a creator by name, email, or ID, and adjusts the layout/labeling of the owner(was creator)
  field, and creator and descirption fields.
  [SEAD-1071](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1071),
  [SEAD-610](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-610)

### Changed
- Clowder now requires Java 8.
- Updated the POST endpoint `/api/extractors` to accept a list of extractor repositories (git, docker, svn, etc) instead
  of only one.
  [BD-1253](https://opensource.ncsa.illinois.edu/jira/browse/BD-1253)
- Changed default labels in Staging Area plugin, e.g. "Curation Objects" to "Publication Requests" and make them
  configurable.
  [SEAD-1131](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1131)
- Updated docker compose repositories from ncsa/* to clowder/*.
  [CATS-734](https://opensource.ncsa.illinois.edu/jira/browse/CATS-734])
- Improved handling of special characters and long descriptions for datasets and Staging Area publication requests
  [SEAD-1143](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1143),
  [CATS-692](https://opensource.ncsa.illinois.edu/jira/browse/CATS-692)
- Default for clowder.diskStorage.path changed from /tmp/clowder to /home/clowder/data.
  [CATS-748](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1143)

### Fixed
- Fixed email newsfeed template for new events, so that instances with malfunctioning email digest subscriptions can
  correctly generate digest emails.
  [SEAD-1108](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1108)


## 1.1.0 - 2017-01-18

### Added
- Breadcrumbs at the top of the page.
  [SEAD-1025](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1025)
- Ability to submit datasets to specific extractors.
  [CATS-697](https://opensource.ncsa.illinois.edu/jira/browse/CATS-697)
- Ability to ask for just number of datapoints in query.
  [GEOD-783](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-783)
- Filter metadata on extractor ID.
  [CATS-566](https://opensource.ncsa.illinois.edu/jira/browse/CATS-566)
- Moved additional entries to conf/messages.xxx for internationalization and customization of labels by instance.
- *(Experimental)* Support for geostreams datapoints with parameters values organized by type.
  [GLM-54](https://opensource.ncsa.illinois.edu/jira/browse/GLM-54)
- Extraction messages are now sent with the RabbitMQ persistent flag turned on.
  [CATS-714](https://opensource.ncsa.illinois.edu/jira/browse/CATS-714)
- Pagination to listing of curation objects.
- Pagination to listing of public datasets.

### Changed
- Only show Quicktime preview for Quicktime-VR videos.
- Organize public/published datasets into multiple tabs on project space page.
  [SEAD-1036](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1036)
- Changed RabbitMQ delivery mode to persistent.
  [CATS-714](https://opensource.ncsa.illinois.edu/jira/browse/CATS-714)
- Dataset and collection listing layout is not consistent with space listing layout.

### Removed
- /delete-all endpoint.

### Fixed
- Validation of JSON-LD when uploaded.
  [CATS-438](https://opensource.ncsa.illinois.edu/jira/browse/CATS-438)
- Files are no longer called blob when downloaded.
- Corrected association of JSON-LD metadata and user when added through API.
- Ability to add specific metadata to a space.
  [SEAD-1133](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1133),
  [SEAD-1134](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1134)
- Metadata context popups now always properly disappear on mouse out.
- User metadata @context properly filled to required mappings.
  [CATS-717](https://opensource.ncsa.illinois.edu/jira/browse/CATS-717)

## 1.0.0 - 2016-12-07

First official release of Clowder.
