package services

abstract class RdfSPARQLService {

  def addFileToGraph(fileId: String, selectedGraph:String = "rdfXMLGraphName"): Null  
  def addDatasetToGraph(datasetId: String, selectedGraph:String = "rdfXMLGraphName"): Null
  
  def removeFileFromGraphs(fileId: String, selectedGraph:String = "rdfXMLGraphName"): Null
  def removeDatasetFromGraphs(datasetId: String): Null
  
  def linkFileToDataset(fileId: String, datasetId: String, selectedGraph:String = "rdfXMLGraphName"): Null
  
  def sparqlQuery(queryText: String): String
  
  def detachFileFromDataset(fileId: String, datasetId: String, selectedGraph:String = "rdfXMLGraphName"): Null
  
  def removeDatasetFromUserGraphs(fileId: String): Null
  
  def addFromFile(id: String, tempFile: java.io.File, fileOrDataset: String, selectedGraph:String = "rdfCommunityGraphName") : Null
  
}