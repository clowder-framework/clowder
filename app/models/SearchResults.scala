package models

/**
  * Previews are created from video files, as first shot of each frame of the video.
  *
  * Used by multimedia search to pass information
  * between controller and viewer.
  * Specifically, used by VersusPlugin, Search, ContentBasedSearchResultsVideo3
  */
case class SearchResultPreview (id: UUID,
                                 url: String,
                                 distance: Double,
                                 previewName: String,
                                 datasetIdList: List[String]= List.empty,
                                 fileIdString: String="",
                                 fileTitle:String="",
                                 shotStartTime:Int)

/**
* Used by multimedia search to pass information
* between controller and viewer.
*
* Specifically, used by VersusPlugin, Search, multimediaSearchResults
*
* Searching through files that can be of various types (image, pdf, etc)
*/
case class SearchResultFile (id: UUID,
                              url: String,
                              distance: Double,
                              title: String,
                              datasetIdList: List[String],
                              thumbnailId: String)

// A.get(List[UUID]) returns the list of documents found in database, and a list of any IDs that were not found
case class DBResult[A](found: List[A], missing: List[UUID])
