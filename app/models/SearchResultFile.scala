package models

case class SearchResultFile (     
    id: UUID, 
    url: String,
    distance: Double,
    title: String,
    datasetIdList: List[String],   
    thumbnail_id_str: String    
)


