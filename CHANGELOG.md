# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/) 
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

### Breaking
- Validation of JSON-LD when uploaded [CATS-438](https://opensource.ncsa.illinois.edu/jira/browse/CATS-438)

### Added
- Breadcrumbs at the top of the page
- Ability to submit dataset to specific extractor [CATS-697](https://opensource.ncsa.illinois.edu/jira/browse/CATS-697)
- Ability to ask for just number of datapoints in query [GEOD-783](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-783)
- Filter metadata on extractor ID
- Additional customization texts in messages

### Changed
- Only show quicktime preview for quicktime-VR videos
- Show public/published datasets on project page [SEAD-1036](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1036)

### Fixed
- Files are no longer called blob when downloaded
- Corrected associated of json-ld metadata and user when added through API
- Ability to add specific metadata to a space [SEAD-1133](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1133), [SEAD-1134](https://opensource.ncsa.illinois.edu/jira/browse/SEAD-1134)

## [1.0.0] - 2016-12-07

First official release of clowder
