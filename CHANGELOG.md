# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/) 
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

### Added

### Changed
- Created email to be sent when registerThroughAdmins=true
  [CATS-791](https://opensource.ncsa.illinois.edu/jira/browse/CATS-791)
- Default value for showAll in list spaces
  [CATS-815](https://opensource.ncsa.illinois.edu/jira/browse/CATS-718)

### Fixed
- Dataset descriptions of sufficient length no longer cause the page to freeze in tiles view.

## 1.3.2 - 2017-08-15

### Fixed
- Elasticsearch searches are broken. [CATS-783](https://opensource.ncsa.illinois.edu/jira/browse/CATS-783)

## 1.3.1 - 2017-07-24

### Fixed
- Upgraded Postgres driver to 42.1.1. Geostreams API was throwing an a "canceling statement due to user request" error 
  for large datapoint queries with Postgresql versions 9.5+. 
  [CATS-771](https://opensource.ncsa.illinois.edu/jira/browse/CATS-771)
- When doing a reindex all indices in elasticsearch were removed.
  [CATS-772](https://opensource.ncsa.illinois.edu/jira/browse/CATS-772)
- CILogin properly works by specifying berer token in header.
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
  notification email and a notice in their event feed. [SEAD-781](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-781)
- Description field to metadata definition. [SEAD-1101](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1101)

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
  in their attributes. [CATS-762](https://opensource.ncsa.illinois.edu/jira/browse/CATS-762)

### Fixed
- Binning on geostreaming api for hour and minutes. [GEOD-886](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-886)
- Returning the last average when semi is not selected and there is no binning by season.
- Removing space id from collections and datasets when the space is deleted. 
  [CATS-752](https://opensource.ncsa.illinois.edu/jira/browse/CATS-752)
- Copy of dataset. When a dataset is copied, the newly created dataset will have the system generated metadata, previews, 
  and thumbnails for the dataset and the files.[CATS-729](https://opensource.ncsa.illinois.edu/jira/browse/CATS-729) 
- Return *409 Conflict* when submitting file for manual extraction and file is not marked as *PROCESSED*. 
  [CATS-754](https://opensource.ncsa.illinois.edu/jira/browse/CATS-754)
- Listing of files in dataset breaks when user permissions in a space are set to View. 
  [CATS-767](https://opensource.ncsa.illinois.edu/jira/browse/CATS-767)
- Reenabled byte counts on index and status pages.
- Miscellaneous bug fixes.

## 1.2.0 - 2017-03-24 

### Added
- Docker container to add normal/admin users for Clowder. [BD-1167](https://opensource.ncsa.illinois.edu/jira/browse/BD-1167) 
- ORCID/other ID expansion - uses SEAD's PDT service to expand user ids entered as creator/contact metadata so they show 
  as a name, link to profile, and email(if available)[SEAD-1126](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1126) 
- Can add a list of creators to a Dataset and publication request(Staging Area plugin). This addition also supports 
  type-in support for adding a creator by name, email, or ID, and adjusts the layout/labeling of the owner(was creator) 
  field, and creator and descirption fields. [SEAD-1071](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1071), 
  [SEAD-610](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-610) 

### Changed
- Clowder now requires Java 8.
- Updated the POST endpoint `/api/extractors` to accept a list of extractor repositories (git, docker, svn, etc) instead 
  of only one. [BD-1253](https://opensource.ncsa.illinois.edu/jira/browse/BD-1253)
- Changed default labels in Staging Area plugin, e.g. "Curation Objects" to "Publication Requests" and make them configurable. 
  [SEAD-1131](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1131)
- Updated docker compose repositories from ncsa/* to clowder/*. [CATS-734](https://opensource.ncsa.illinois.edu/jira/browse/CATS-734])
- Improved handling of special characters and long descriptions for datasets and Staging Area publication requests 
  [SEAD-1143](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1143), 
  [CAT-692](https://opensource.ncsa.illinois.edu/jira/browse/CATS-692)
- Default for clowder.diskStorage.path changed from /tmp/clowder to /home/clowder/data. 
  [CATS-748](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1143)

### Fixed
- Fixed email newsfeed template for new events, so that instances with malfunctioning email digest subscriptions can 
  correctly generate digest emails. [SEAD-1108](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1108)


## 1.1.0 - 2017-01-18

### Added
- Breadcrumbs at the top of the page. [SEAD-1025](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1025)
- Ability to submit datasets to specific extractors. [CATS-697](https://opensource.ncsa.illinois.edu/jira/browse/CATS-697)
- Ability to ask for just number of datapoints in query. [GEOD-783](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-783)
- Filter metadata on extractor ID. [CATS-566](https://opensource.ncsa.illinois.edu/jira/browse/CATS-566)
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
- Changed Rabbitmq delivery mode to persistent. [CATS-714](https://opensource.ncsa.illinois.edu/jira/browse/CATS-714)
- Dataset and collection listing layout is not consistent with space listing layout.

### Removed
- /delete-all endpoint.

### Fixed
- Validation of JSON-LD when uploaded. [CATS-438](https://opensource.ncsa.illinois.edu/jira/browse/CATS-438)
- Files are no longer called blob when downloaded.
- Corrected association of JSON-LD metadata and user when added through API.
- Ability to add specific metadata to a space. [SEAD-1133](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1133), 
  [SEAD-1134](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1134)
- Metadata context popups now always properly disappear on mouse out.
- User metadata @context properly filled to required mappings. [CATS-717](https://opensource.ncsa.illinois.edu/jira/browse/CATS-717)

## 1.0.0 - 2016-12-07

First official release of Clowder.
