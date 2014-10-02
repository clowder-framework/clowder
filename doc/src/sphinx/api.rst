Using the API
=============

The RESTFul application programming interface is the best way to interact with Medici programmatically. Much of the web
frontend and the extractors use this same API to interact with the system.

Following is a brief description of all the available endpoints. For more information take a look at the source under
``app/api``. All routes are defined in ``conf/routes``. For methods that write to the system a key is required. The default
key is available in ``conf/application.conf`` under ``commKey``. Please change this to a very long string of characters
when deploying the system. The key is passed as a query parameter to the url. For example
``?key=sdjof902j39f09joahsduh0932jnujv09erjfosind``.

=============== =========================================================== ============================================
HTTP Method     Endpoint                                                    Description
=============== =========================================================== ============================================
GET             /api/files                                                  List files
POST            /api/files                                                  Upload file
POST            /api/files                                                  Upload file
POST            /api/files                                                  Upload file
POST 	        /api/files/searchusermetadata                               Search user specified metadata
POST 	        /api/files/searchmetadata                                   Search system generated metadata
POST            /api/uploadToDataset/:id                                    Upload file to dataset
POST 	        /api/files/:fileId/remove                                   Delete file
GET             /api/files/:id/metadata                                     Get file information
POST 	        /api/files/:file_id/thumbnails/:thumbnail_id                Attach thumbnail to file
POST            /api/files/:id/metadata                                     Add system-specified metadata
POST            /api/files/:id/usermetadata                                 Add user-specified metadata
GET             /api/files/:id/technicalmetadatajson                        Get system-specified metadata
POST            /api/files/:id/comment                                      Add comment to file
GET             /api/files/:id/tags                                         List file tags
POST            /api/files/:id/tags                                         Add file tags
POST            /api/files/:id/tags/remove                                  Remove file tags
POST            /api/files/:id/tags/remove_all                              Remove all tags from file
POST            /api/files/:id/previews/:p_id                               Attach preview to file
GET             /api/files/:id/listpreviews                                 List all previews associated with file
GET             /api/files/:id/getPreviews                                  List previews associated with file filter by available previewers
GET             /api/files/:id/isBeingProcessed                             Whether a file is being processed by an extractor
GET             /api/files/:three_d_file_id/:filename	                    Get texture associated with file
POST 	        /api/files/:three_d_file_id/geometries/:geometry_id         Attach geometry
POST 	        /api/files/:three_d_file_id/3dTextures/:texture_id          Attach texture
GET             /api/files/:id                                              Download original file
POST 	        /api/files/uploadIntermediate/:idAndFlags                   *
POST 	        /api/files/sendJob/:fileId/:fileType	                    *
GET             /api/files/getRDFURLsForFile/:id                            *
GET             /api/files/rdfUserMetadata/:id                              *
GET             /api/queries/:id                                            Versus download query
POST 	        /api/collections/:coll_id/datasets/:ds_id                   Associate dataset with collection
POST 	        /api/collections/:coll_id/datasetsRemove/:ds_id/:ignore     Remove dataset from collection
POST            /api/collections/:coll_id/remove                            Delete collection
GET             /api/collections/:coll_id/getDatasets	                    List datasets in collection
GET             /api/datasets                                               List datasets
POST            /api/datasets                                               Create new dataset
POST 	        /api/datasets/searchusermetadata                            Search datasets by user-specified metadata
POST            /api/datasets/searchmetadata                                Search datasets system-specifed metadata
GET             /api/datasets/listOutsideCollection/:coll_id                List all datasets not in a collection
POST 	        /api/datasets/:ds_id/filesRemove/:file_id/:ignoreNotFound   Remove file from dataset
GET             /api/datasets/getRDFURLsForDataset/:id	                    Get dataset information as RDF
GET             /api/datasets/rdfUserMetadata/:id                           Get user-specified metadata as RDF
POST 	        /api/datasets/:datasetId/remove                             Delete dataset
POST            /api/datasets/:id/metadata                                  Add system-specified metadata to dataset
POST            /api/datasets/:id/usermetadata                              Add user-specified metadata to dataset
GET             /api/datasets/:id/technicalmetadatajson                     Get system-specified for dataset
GET  	        /api/datasets/:id/listFiles                                 List files in dataset
POST            /api/datasets/:id/comment                                   Add comment to dataset
POST 	        /api/datasets/:id/removeTag                                 Remove tag from dataset
GET             /api/datasets/:id/tags                                      List tags in dataset
POST            /api/datasets/:id/tags                                      Add tag to dataset
POST            /api/datasets/:id/tags/remove                               Remove tags from dataset
POST            /api/datasets/:id/tags/remove_all                           Remove all tags from dataset
GET             /api/datasets/:id/isBeingProcessed                          If dataset is being processed by extractors
GET             /api/datasets/:id/getPreviews                               Get previews associated with dataset
POST 	        /api/datasets/:ds_id/files/:file_id                         Associate file with dataset
GET             /api/previews/:preview_id/textures/dataset/:d_id/json       *
GET             /api/previews/:preview_id/textures/dataset/:d_id/:file      *
GET             /api/previews/:dzi_id_dir/:level/:filename                  *
POST            /api/previews/:dzi_id/tiles/:tile_id/:level                 *
POST            /api/previews/:id/metadata                                  Add metadata to preview
GET             /api/previews/:id/metadata                                  Get metadata from preview
POST            /api/previews/:id/annotationAdd                             Add annotation to 3D model
POST            /api/previews/:id/annotationEdit                            Edit annotation of 3D model
GET             /api/previews/:id/annotationsList                           List annotations associated with preview
GET             /api/previews/:id                                           Get preview bytes
POST            /api/previews                                               Create new preview
POST            /api/indexes                                                Create a new content-based index
POST            /api/indexes/features                                       Add feauture vector to content-based index
POST            /api/sections                                               Create new section
GET             /api/sections/:id                                           Get section metadata
POST            /api/sections/:id/comments                                  Add comment to section
GET             /api/sections/:id/tags                                      Get tags associated with section
POST            /api/sections/:id/tags                                      Associate tags with section
POST            /api/sections/:id/tags/remove                               Remove tags from section
POST            /api/sections/:id/tags/remove_all                           Remove all tags from section
POST            /api/geostreams/sensors                                     Create new sensor
GET             /api/geostreams/sensors/:id/streams                         List streams associated with sensor
GET             /api/geostreams/sensors/:id                                 Get sensor information
GET             /api/geostreams/sensors                                     Search sensors by space
POST            /api/geostreams/streams                                     Create new stream
GET             /api/geostreams/streams/:id                                 Get stream information
GET             /api/geostreams/streams                                     Search streams by space
DELETE          /api/geostreams/streams/:id                                 Delete stream
POST            /api/geostreams/datapoints                                  Create new geotemporal datapoint
GET             /api/geostreams/datapoints/:id                              Get datapoint
GET             /api/geostreams/datapoints                                  Search datapoints by space and time
GET             /api/geostreams/counts                                      Return counts for sensors, streams and datapoints
POST 	        /api/tiles                                                  Upload tile to image pyramid
POST 	        /api/geometries                                             Upload geometry
POST 	        /api/3dTextures                                             Upload 3D texture
POST            /api/fileThumbnail                                          Upload file thumbnail
POST 	        /api/comment/:id                                            Create new comment
GET             /api/search                                                 Text based search
=============== =========================================================== ============================================



You can use ``curl`` to test the service. If you are on Linux or MacOSX you should have it already. Try typing ``curl``
on the command prompt. If you are on windows, you can download a build at http://curl.haxx.se/.
If you want a more rich GUI experience most web browsers have extensions that can be used instead.
For example for Chrome you can try
`cREST client <https://chrome.google.com/webstore/detail/dev-http-client/aejoelaoggembcahagimdiliamlcdmfm>`_.
