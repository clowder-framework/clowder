package services

import play.api.{ Plugin, Logger, Application }
import play.api.Play.current
import models.FileDAO
import org.bson.types.ObjectId
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.FileWriter
import Transformation.LidoToCidocConvertion
import play.api.libs.json.JsValue
import models.PreviewDAO
import play.api.libs.json.Json
import models.Dataset

/**
 * Service allowing exporting of community-generated and XML-uploaded metadata of files and datasets as RDF.
 *
 * @author Constantinos Sophocleous
 *
 */
class RDFExportService (application: Application) extends Plugin {
  
  val files: FileService =  DI.injector.getInstance(classOf[FileService])
  val datasets: DatasetService =  DI.injector.getInstance(classOf[DatasetService])
  
  val resultsDir = System.getProperty("java.io.tmpdir") + System.getProperty("file.separator") + "medici__rdfdumptemporaryfiles" + System.getProperty("file.separator")
  val filesMappingsQuantity = Integer.parseInt(play.api.Play.configuration.getString("filesxmltordfmapping.dircount").getOrElse("1"))
  val datasetsMappingsQuantity = Integer.parseInt(play.api.Play.configuration.getString("datasetsxmltordfmapping.dircount").getOrElse("1"))
  
  val hostUrl = "http://" + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + play.Play.application().configuration().getString("http.port")

  override def onStart() {
    Logger.debug("Starting RDF exporter Plugin")
  }
  
  override def onStop() {
    Logger.debug("Shutting down RDF exporter Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("rdfexportservice").filter(_ == "disabled").isDefined
  }
  
  def getRDFUserMetadataFile(id: String, mappingNumber: String="1"): Option[java.io.File] = {
    
    files.get(id) match { 
	            case Some(file) => {
	              val theJSON = FileDAO.getUserMetadataJSON(id)
	              val fileSep = System.getProperty("file.separator")
	              
	              //for Unix we need an extra \ in the directory path of the LidoToCidocConvertion output file due to Windows-based behavior of LidoToCidocConvertion  
	              var extraChar = ""
	              val OS = System.getProperty("os.name").toLowerCase()
	              if(OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") >= 0)
	                extraChar = "\\"
	              
		          var resultDir = resultsDir + new ObjectId().toString
		          new java.io.File(resultDir).mkdirs()
	              
	              if(!theJSON.replaceAll(" ","").equals("{}")){
		              val xmlFile = jsonToXML(theJSON)
		              new LidoToCidocConvertion(play.api.Play.configuration.getString("filesxmltordfmapping.dir_"+mappingNumber).getOrElse(""), xmlFile.getAbsolutePath(), resultDir)	                            
		              xmlFile.delete()
	              }
	              else{
	                new java.io.File(resultDir + fileSep + extraChar + "Results.rdf").createNewFile()
	              }
	              val resultFile = new java.io.File(resultDir + fileSep + extraChar + "Results.rdf")
	              Some(resultFile)	              
	            }
	            case None => None
	    }    
  }
  
  def getRDFURLsForFile(id: String): Option[JsValue] = {   
    files.get(id)  match {
	      case Some(file) => {
	        
	        //RDF from XML of the file itself (for XML metadata-only files)
	        val previewsList = PreviewDAO.findByFileId(new ObjectId(id))
	        var rdfPreviewList = List.empty[models.Preview]
	        for(currPreview <- previewsList){
	          if(currPreview.contentType.equals("application/rdf+xml")){
	            rdfPreviewList = rdfPreviewList :+ currPreview
	          }
	        }        
	        var list = for (currPreview <- rdfPreviewList) yield Json.toJson(hostUrl + api.routes.Previews.download(currPreview.id.toString))
	        
	        //RDF from export of file community-generated metadata to RDF 
	        for(i <- 1 to filesMappingsQuantity){
	          var currHostString = hostUrl + api.routes.Files.getRDFUserMetadata(id,i.toString)
	          list = list :+ Json.toJson(currHostString)
	        }
	
	        val listJson = Json.toJson(list.toList)
	        Some(listJson)
	      }
	      case None => None
	    }
  }
  
  def getRDFUserMetadataDataset(id: String, mappingNumber: String="1"): Option[java.io.File] = {
    
    datasets.get(id) match { 
	            case Some(dataset) => {
	              val theJSON = Dataset.getUserMetadataJSON(id)
	              val fileSep = System.getProperty("file.separator")
	              
	              //for Unix we need an extra \ in the directory path of the LidoToCidocConvertion output file due to Windows-based behavior of LidoToCidocConvertion  
	              var extraChar = ""
	              val OS = System.getProperty("os.name").toLowerCase()
	              if(OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") >= 0)
	                extraChar = "\\"
	              
		          var resultDir = resultsDir + new ObjectId().toString
		          new java.io.File(resultDir).mkdirs()
	              
	              if(!theJSON.replaceAll(" ","").equals("{}")){
		              val xmlFile = jsonToXML(theJSON)
		              new LidoToCidocConvertion(play.api.Play.configuration.getString("datasetsxmltordfmapping.dir_"+mappingNumber).getOrElse(""), xmlFile.getAbsolutePath(), resultDir)	                            
		              xmlFile.delete()
	              }
	              else{
	                new java.io.File(resultDir + fileSep + extraChar + "Results.rdf").createNewFile()
	              }
	              val resultFile = new java.io.File(resultDir + fileSep + extraChar + "Results.rdf")
	              Some(resultFile)	              
	            }
	            case None => None
	    }    
  }
  
  def getRDFURLsForDataset(id: String): Option[JsValue] = {   
    datasets.get(id)  match {
	      case Some(dataset) => {
	        
	        //RDF from XML files in the dataset itself (for XML metadata-only files)
	        val previewsList = PreviewDAO.findByDatasetId(new ObjectId(id))
	        var rdfPreviewList = List.empty[models.Preview]
	        for(currPreview <- previewsList){
	          if(currPreview.contentType.equals("application/rdf+xml")){
	            rdfPreviewList = rdfPreviewList :+ currPreview
	          }
	        }        
	        var list = for (currPreview <- rdfPreviewList) yield Json.toJson(hostUrl + api.routes.Previews.download(currPreview.id.toString))
	        
	        for(file <- dataset.files){
	           val filePreviewsList = PreviewDAO.findByFileId(file.id)
	           var fileRdfPreviewList = List.empty[models.Preview]
	           for(currPreview <- filePreviewsList){
		           if(currPreview.contentType.equals("application/rdf+xml")){
		        	   fileRdfPreviewList = fileRdfPreviewList :+ currPreview
		           }
	           }
	           val filesList = for (currPreview <- fileRdfPreviewList) yield Json.toJson(hostUrl + api.routes.Previews.download(currPreview.id.toString))
	           list = list ++ filesList
	        }
	        
	        //RDF from export of file community-generated metadata to RDF 
	        for(i <- 1 to filesMappingsQuantity){
	          var currHostString = hostUrl + api.routes.Datasets.getRDFUserMetadata(id,i.toString)
	          list = list :+ Json.toJson(currHostString)
	        }
	
	        val listJson = Json.toJson(list.toList)
	        Some(listJson)
	      }
	      case None => None
	    }
  }
  
  def jsonToXML(theJSON: String): java.io.File = {
    
    val jsonObject = new JSONObject(theJSON)    
    var xml = org.json.XML.toString(jsonObject)
    
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