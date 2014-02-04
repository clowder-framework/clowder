package controllers

import models.Dataset
import models._
import play.api.Play.current
import java.util.ArrayList
import play.api.Logger
import java.io.BufferedReader
import java.io.FileReader
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.FileInputStream
import org.apache.commons.io.FileUtils
import Transformation.LidoToCidocConvertion
import org.bson.types.ObjectId
import Transformation.LidoToCidocConvertion
import org.json.JSONObject


object DatasetsRDFUpdate{

  def modifyRDFOfMetadataChangedDatasets(){    
    val changedDatasets = Dataset.findMetadataChangedDatasets()
    for(changedDataset <- changedDatasets){
      modifyRDFUserMetadata(changedDataset.id.toString)
    }
  }
  
  def modifyRDFUserMetadata(id: String, mappingNumber: String="1") = {
    services.Services.rdfSPARQLService.removeDatasetFromUserGraphs(id)
    Dataset.findOneById(new ObjectId(id)) match { 
	            case Some(dataset) => {
	              val theJSON = Dataset.getUserMetadataJSON(id)
	              val fileSep = System.getProperty("file.separator")
	              val tmpDir = System.getProperty("java.io.tmpdir")
		          var resultDir = tmpDir + fileSep + "medici__rdfuploadtemporaryfiles" + fileSep + new ObjectId().toString
		          val resultDirFile = new java.io.File(resultDir)
		          resultDirFile.mkdirs()
	              
	              if(!theJSON.replaceAll(" ","").equals("{}")){
		              val xmlFile = jsonToXML(theJSON)
		              new LidoToCidocConvertion(play.api.Play.configuration.getString("datasetsxmltordfmapping.dir_"+mappingNumber).getOrElse(""), xmlFile.getAbsolutePath(), resultDir)	                            
		              xmlFile.delete()
	              }
	              else{
	                new java.io.File(resultDir + fileSep + "Results.rdf").createNewFile()
	              }
	              val resultFile = new java.io.File(resultDir + fileSep + "Results.rdf")
	              
	              //Connecting RDF metadata with the entity describing the original file
					val rootNodes = new ArrayList[String]()
					val rootNodesFile = play.api.Play.configuration.getString("datasetRootNodesFile").getOrElse("")
					Logger.debug(rootNodesFile)
					if(!rootNodesFile.equals("*")){
						val rootNodesReader = new BufferedReader(new FileReader(new java.io.File(rootNodesFile)))						
						var line = rootNodesReader.readLine()  
						while (line != null){
						    Logger.debug((line == null).toString() ) 
							rootNodes.add(line.trim())
							line = rootNodesReader.readLine() 
						}
						rootNodesReader.close()
					}
					
					val resultFileConnected = java.io.File.createTempFile("ResultsConnected", ".rdf")
					
					val fileWriter =  new BufferedWriter(new FileWriter(resultFileConnected))		
					val fis = new FileInputStream(resultFile)
					val data = new Array[Byte]  (resultFile.length().asInstanceOf[Int])
				    fis.read(data)
				    fis.close()
				    resultFile.delete()
				    FileUtils.deleteDirectory(resultDirFile)
				    //
				    val s = new String(data, "UTF-8")
					val rdfDescriptions = s.split("<rdf:Description")
					fileWriter.write(rdfDescriptions(0))
					var i = 0
					for( i <- 1 to (rdfDescriptions.length - 1)){
						fileWriter.write("<rdf:Description" + rdfDescriptions(i))
						if(rdfDescriptions(i).contains("<rdf:type")){
							var isInRootNodes = false
							if(rootNodesFile.equals("*"))
								isInRootNodes = true
							else{
								var j = 0
								try{
									for(j <- 0 to (rootNodes.size()-1)){
										if(rdfDescriptions(i).contains("\"" + rootNodes.get(j) + "\"")){
											isInRootNodes = true
											throw MustBreak
										}
									}
								}catch {case MustBreak => }
							}
							
							if(isInRootNodes){
								val theResource = rdfDescriptions(i).substring(rdfDescriptions(i).indexOf("\"")+1, rdfDescriptions(i).indexOf("\"", rdfDescriptions(i).indexOf("\"")+1))
								val theHost = "http://" + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + play.Play.application().configuration().getString("http.port")
								var connection = "<rdf:Description rdf:about=\"" + theHost +"/api/datasets/"+ id
								connection = connection	+ "\"><P129_is_about xmlns=\"http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#\" rdf:resource=\"" + theResource
								connection = connection	+ "\"/></rdf:Description>"
								fileWriter.write(connection)
							}	
						}
					}
					fileWriter.close()
	              
					services.Services.rdfSPARQLService.addFromFile(id, resultFileConnected, "dataset")
					resultFileConnected.delete()
					
					services.Services.rdfSPARQLService.addDatasetToGraph(id, "rdfCommunityGraphName")
					
					Dataset.setUserMetadataWasModified(id, false)
	            }
	            case None => {}
	 }
  }
  
  def jsonToXML(theJSON: String): java.io.File = {
    
    val jsonObject = new JSONObject(theJSON)    
    var xml = org.json.XML.toString(jsonObject)
    
    Logger.debug("thexml: " + xml)
    
    //Remove spaces from XML tags
    var currStart = xml.indexOf("<")
    var currEnd = -1
    var xmlNoSpaces = ""
    while(currStart != -1){
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd+1,currStart)
      currEnd = xml.indexOf(">", currStart+1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart,currEnd+1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd+1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd+1)
    
    val xmlFile = java.io.File.createTempFile("xml",".xml")
    val fileWriter =  new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()
    
    return xmlFile    
  }
  
}