# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

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
- Ability for CiLogon provider to filter by LDAP groups.
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
