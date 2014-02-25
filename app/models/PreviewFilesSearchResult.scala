/**
 * Previewers and Files Search Resutls.
 * Used by multimedia search to pass information
 * between controller and viewer.
 * 
 * Specifically, used by VersusPlugin, Search, ContentBasedSearchResultsVideo3
 * 
 * Searching through files (born as still images) and video previews
 * (first shot of each frame of the video)
 * 
 * @author Inna Zharnitsky
 *
 */

package models

case class PreviewFilesSearchResult(
    fileOrPreview:String,
    id: String, 
    url: String,
    distance: Double,
    title: String,
    previewsList: Map[File, 
      Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]],
    datasetIdList: List[String],
    
    //for files, since files have thumbnails extracted by ncsa.image extractor
    thumbnail_id:String
    
)

