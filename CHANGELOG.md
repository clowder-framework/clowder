# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/) 
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]
### Added
### Changed
### Fixed

## 1.2.0

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
- Improved handling of special characters and long descriptions for datasets and Staging Area publication requests [SEAD-1143](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1143), [CAT-692](https://opensource.ncsa.illinois.edu/jira/browse/CATS-692)
- Default for clowder.diskStorage.path changed from /tmp/clowder to /home/clowder/data [CATS-748](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1143)

### Fixed
- Fixed email newsfeed template for new events, so that instances with malfunctioning email digest subscriptions can correctly generate digest emails. [SEAD-1108](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1108)


## 1.1.0

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
- Organize public/published datasets into multiple tabs on project space page. [SEAD-1036](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1036)
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

## [1.0.0] - 2016-12-07

First official release of Clowder.
