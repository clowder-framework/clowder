package services

abstract class RdfSPARQLService {

  def addFileToGraph(fileId: String): Null  
  def addDatasetToGraph(datasetId: String): Null
  
  def linkFileToDataset(fileId: String, datasetId: String): Null
  
  def sparqlQuery(queryText: String): String
  
}