package models

case class SearchResultPreview (
  
    id: UUID, 
    url: String,
    distance: Double,
    previewName: String,
    datasetIdList: List[String]= List.empty,
    fileId: String="",
    fileTitle:String="",
    shotStartTime:Int
         
)