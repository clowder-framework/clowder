/**
 * Previewers Search Results
 * 
 * Previews are created from video files, as first shot of each frame of the video.
 *  
 * Used by multimedia search to pass information
 * between controller and viewer.
 * Specifically, used by VersusPlugin, Search, ContentBasedSearchResultsVideo3
 * 
 * @author Inna Zharnitsky
 *
 */
package models

case class SearchResultPreview (
  
    id: UUID, 
    url: String,
    distance: Double,
    previewName: String,
    datasetIdList: List[String]= List.empty,
    fileIdString: String="",
    fileTitle:String="",
    shotStartTime:Int
         
)