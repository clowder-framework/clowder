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
    searchResultFile: SearchResultFile,
    searchResultPreview: SearchResultPreview    
)

