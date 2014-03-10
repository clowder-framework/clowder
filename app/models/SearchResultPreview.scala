package models

case class SearchResultPreview (
  
    id: String, 
    url: String,
    distance: Double,
    previewName: String,
    datasetIdList: List[String]= List.empty,
    fileId: String="",
    fileTitle:String="",
    shotStartTime:Int
         
)