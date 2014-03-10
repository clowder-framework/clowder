package models

case class SearchResultFile (     
    id: String, 
    url: String,
    distance: Double,
    title: String,
    datasetIdList: List[String],   
    thumbnail_id:String    
)


