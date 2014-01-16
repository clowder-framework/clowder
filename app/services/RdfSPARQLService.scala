package services

abstract class RdfSPARQLService {

  def addFileToGraph(fileId: String, selectedGraph:String = "rdfXMLGraphName"): Null  
  def addDatasetToGraph(datasetId: String, selectedGraph:String = "rdfXMLGraphName"): Null
  
  def removeFileFromGraph(fileId: String, selectedGraph:String = "rdfXMLGraphName"): Null
  def removeDatasetFromGraph(datasetId: String, selectedGraph:String = "rdfXMLGraphName"): Null
  
  def linkFileToDataset(fileId: String, datasetId: String, selectedGraph:String = "rdfXMLGraphName"): Null
  
  def sparqlQuery(queryText: String): String
  
  def detachFileFromDataset(fileId: String, datasetId: String, selectedGraph:String = "rdfXMLGraphName"): Null
  
  def removeFileMetadata(fileId: String, selectedGraph:String = "rdfCommunityGraphName") : Null
  def removeDatasetMetadata(datasetId: String, selectedGraph:String = "rdfCommunityGraphName") : Null
  
  def addFromFile(fileId: String, tempFile: java.io.File, selectedGraph:String = "rdfCommunityGraphName") : Null
  
}