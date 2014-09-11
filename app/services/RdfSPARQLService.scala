package services

import models.UUID

trait RdfSPARQLService {

  def addFileToGraph(fileId: UUID, selectedGraph: String = "rdfXMLGraphName"): Null

  def addDatasetToGraph(datasetId: UUID, selectedGraph:String = "rdfXMLGraphName"): Null
  
  def removeFileFromGraphs(fileId: UUID, selectedGraph:String = "rdfXMLGraphName"): Null

  def removeDatasetFromGraphs(datasetId: UUID): Null
  
  def linkFileToDataset(fileId: UUID, datasetId: UUID, selectedGraph:String = "rdfXMLGraphName"): Null
  
  def sparqlQuery(queryText: String): String
  
  def detachFileFromDataset(fileId: UUID, datasetId: UUID, selectedGraph:String = "rdfXMLGraphName"): Null
  
  def removeDatasetFromUserGraphs(fileId: UUID): Null
  
  def addFromFile(id: UUID, tempFile: java.io.File, fileOrDataset: String, selectedGraph:String = "rdfCommunityGraphName") : Null
  
}