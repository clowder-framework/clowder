/**
 * File Search Resutls.
 * Used by multimedia search to pass information
 * between controller and viewer.
 * 
 * Specifically, used by VersusPlugin, Search, ContentBasedSearchResultsVideo3
 * 
 * Searching through files that can be of various types (image, pdf, etc) 
 * 
 * @author Inna Zharnitsky
 *
 */

package models

case class SearchResultFile (     
    id: UUID, 
    url: String,
    distance: Double,
    title: String,
    datasetIdList: List[String],   
    thumbnailIdString: String    
)


