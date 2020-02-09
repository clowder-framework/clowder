package models

import play.api.libs.json._


case class SearchResult (results: List[JsValue],  // List of resources as JSON
                         from: Int,               // Starting index of results
                         count: Int,              // Actual returned result count
                         size: Int,               // Requested page size of query
                         scanned_size: Int,       // Number of records scanned to fill 'size' results after permission check
                         total_size: Long,        // Number of records across all pages
                         first: Option[String],   // URL of first page of results
                         last: Option[String],    // URL of last page of results
                         prev: Option[String],    // URL of previous page of results
                         next: Option[String])    // URL of next page of results

object SearchResult {
  /**
   * Serializer for SearchResult
   */
  implicit object SearchResultWrites extends Writes[SearchResult] {
    def writes(sr: SearchResult): JsValue = {
      var result = Json.obj(
        "results" -> Json.toJson(sr.results),
        "from" -> JsNumber(sr.from),
        "count" -> JsNumber(sr.count),
        "size" -> JsNumber(sr.size),
        "scanned_size" -> JsNumber(sr.scanned_size),
        "total_size" -> JsNumber(sr.total_size)
      )

      // Add optional fields only if they exist
      sr.first match {
        case Some(f) => result += ("first" -> JsString(f))
        case None =>
      }
      sr.last match {
        case Some(f) => result += ("last" -> JsString(f))
        case None =>
      }
      sr.prev match {
        case Some(f) => result += ("prev" -> JsString(f))
        case None =>
      }
      sr.next match {
        case Some(f) => result += ("next" -> JsString(f))
        case None =>
      }

      result
    }
  }

  /**
   * Deserializer for SearchResult
   */
  implicit object SearchResultReads extends Reads[SearchResult] {
    def reads(json: JsValue): JsResult[SearchResult] = JsSuccess(new SearchResult(
      (json \ "results").as[List[JsValue]],
      (json \ "from").as[Int],
      (json \ "count").as[Int],
      (json \ "size").as[Int],
      (json \ "scanned_size").as[Int],
      (json \ "total_size").as[Long],
      (json \ "first").as[Option[String]],
      (json \ "last").as[Option[String]],
      (json \ "prev").as[Option[String]],
      (json \ "next").as[Option[String]]
    ))
  }
}

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
