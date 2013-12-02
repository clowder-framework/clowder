/**
 *
 */
package api

import java.util.Date
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import models.Comment
import models.Dataset
import models.File
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import play.api.mvc.Action
import play.api.mvc.Controller
import services.Services
import jsonutils.JsonUtil
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import models.FileDAO
import models.Extraction
import models.Tag
import services.ElasticsearchPlugin
import controllers.Previewers
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.Routes
import controllers.SecuredController
import models.Collection
import org.bson.types.ObjectId
import securesocial.views.html.notAuthorized
import play.api.Play.current
import com.mongodb.casbah.Imports._
import com.mongodb.WriteConcern

import services.Services
import scala.util.parsing.json.JSONArray

import models.PreviewDAO

import org.json.JSONObject
import org.json.XML
import Transformation.LidoToCidocConvertion
import java.io.BufferedWriter
import java.io.FileWriter
import play.api.libs.iteratee.Enumerator
import java.io.FileInputStream
import play.api.libs.concurrent.Execution.Implicits._


/**
 * Dataset API.
 *
 * @author Luigi Marini
 *
 */
object ActivityFound extends Exception { }

@Api(value = "/datasets", listingPath = "/api-docs.{format}/datasets", description = "Maniputate datasets")
object Datasets extends ApiController {

  /**
   * List all datasets.
   */
  def list = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ListDatasets)) { request =>    
      val list = for (dataset <- Services.datasets.listDatasets()) yield jsonDataset(dataset)
      Ok(toJson(list))
  }
  
    /**
   * List all datasets outside a collection.
   */
  def listOutsideCollection(collectionId: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ListDatasets)) { request =>
      Collection.findOneById(new ObjectId(collectionId)) match{
        case Some(collection) => {
          val list = for (dataset <- Services.datasets.listDatasetsChronoReverse; if(!isInCollection(dataset,collection))) yield jsonDataset(dataset)
          Ok(toJson(list))
        }
        case None =>{
          val list = for (dataset <- Services.datasets.listDatasetsChronoReverse) yield jsonDataset(dataset)
          Ok(toJson(list))
        } 
      }
  }
  
  def isInCollection(dataset: Dataset, collection: Collection): Boolean = {
    for(collDataset <- collection.datasets){
      if(collDataset.id == dataset.id)
        return true
    }
    return false
  }
  
  /**
   * Create new dataset
   */
    def createDataset() = SecuredAction(authorization=WithPermission(Permission.CreateDatasets)) { request =>
      Logger.debug("Creating new dataset")
      (request.body \ "name").asOpt[String].map { name =>
      	  (request.body \ "description").asOpt[String].map { description =>
      	    (request.body \ "file_id").asOpt[String].map { file_id =>
      	      FileDAO.get(file_id) match {
      	        case Some(file) =>
      	           val d = Dataset(name=name,description=description, created=new Date(), files=List(file), author=request.user.get)
		      	   Dataset.insert(d) match {
		      	     case Some(id) => {
		      	       import play.api.Play.current
		      	       api.Files.index(file_id)
		      	       if(!file.xmlMetadata.isEmpty){
		      	    	   Dataset.addXMLMetadata(id.toString, file_id, FileDAO.getXMLMetadataJSON(file_id))
		      	       }		      	       
		      	        current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", id.toString, 
		      	        			List(("name",d.name), ("description", d.description)))}
		      	       Ok(toJson(Map("id" -> id.toString)))
		      	     }
		      	     case None => Ok(toJson(Map("status" -> "error")))
		      	   }
      	        case None => BadRequest(toJson("Bad file_id = " + file_id))
      	      }
      	   }.getOrElse {
      		BadRequest(toJson("Missing parameter [file_id]"))
      	  }
      	  }.getOrElse {
      		BadRequest(toJson("Missing parameter [description]"))
      	  }
      }.getOrElse {
        BadRequest(toJson("Missing parameter [name]"))
      }

    }
    
  def attachExistingFile(dsId: String, fileId: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.CreateDatasets)) { request =>
    Services.datasets.get(dsId) match {
      case Some(dataset) => {
        Services.files.get(fileId) match {
          case Some(file) => {
            val theFile = FileDAO.get(fileId).get
            if(!isInDataset(theFile,dataset)){
	            Dataset.addFile(dsId, theFile)	            
	            api.Files.index(fileId)
	            Logger.info("Adding file to dataset completed")
	            
	            if(dataset.thumbnail_id.isEmpty && !theFile.thumbnail_id.isEmpty){
		                        Dataset.dao.collection.update(MongoDBObject("_id" -> dataset.id), 
		                        $set("thumbnail_id" -> theFile.thumbnail_id), false, false, WriteConcern.SAFE)		                        
		       }
            }
            else{
              Logger.info("File was already in dataset.")
            }
            Ok(toJson(Map("status" -> "success")))
          }
          case None => { Logger.error("Error getting file" + fileId); InternalServerError }
        }        
      }
      case None => { Logger.error("Error getting dataset" + dsId); InternalServerError }
    }  
  }
  
  def isInDataset(file: File, dataset: Dataset): Boolean = {
    for(dsFile <- dataset.files){
      if(dsFile.id == file.id)
        return true
    }
    return false
  }
  
  def detachFile(datasetId: String, fileId: String, ignoreNotFound: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.CreateCollections)) { request =>
    Services.datasets.get(datasetId) match{
      case Some(dataset) => {
        Services.files.get(fileId) match {
          case Some(file) => {
            val theFile = FileDAO.get(fileId).get
            if(isInDataset(theFile,dataset)){
	            //remove file from dataset
	            Dataset.removeFile(dataset.id.toString, theFile.id.toString)
	            api.Files.index(fileId)
	            Logger.info("Removing file from dataset completed")
	            
	            if(!dataset.thumbnail_id.isEmpty && !theFile.thumbnail_id.isEmpty){
	              if(dataset.thumbnail_id.get == theFile.thumbnail_id.get){
		             Dataset.newThumbnail(dataset.id.toString)
		          }		                        
		       }
	            
            }
            else{
              Logger.info("File was already out of the dataset.")
            }
            Ok(toJson(Map("status" -> "success")))
          }
          case None => {
        	  Ok(toJson(Map("status" -> "success")))
          }
        }
      }
      case None => {
        ignoreNotFound match{
          case "True" =>
            Ok(toJson(Map("status" -> "success")))
          case "False" =>
        	Logger.error("Error getting dataset" + datasetId); InternalServerError
        }
      }     
    }
  }
  
  def getInCollection(collectionId: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowCollection)) { request =>
    Collection.findOneById(new ObjectId(collectionId)) match{
      case Some(collection) => {
        val list = for (dataset <- Dataset.listInsideCollection(collectionId)) yield jsonDataset(dataset)
        Ok(toJson(list))
      }
      case None => {
        Logger.error("Error getting collection" + collectionId); InternalServerError
      }
    }
  }
  

  def jsonDataset(dataset: Dataset): JsValue = {
    var datasetThumbnail = "None"
    if(!dataset.thumbnail_id.isEmpty)
      datasetThumbnail = dataset.thumbnail_id.toString().substring(5,dataset.thumbnail_id.toString().length-1)
    
    toJson(Map("id" -> dataset.id.toString, "datasetname" -> dataset.name, "description" -> dataset.description, "created" -> dataset.created.toString, "thumbnail" -> datasetThumbnail))
  }

  @ApiOperation(value = "Add metadata to dataset", notes = "Returns success of failure", responseClass = "None", httpMethod = "POST")
  def addMetadata(id: String) = SecuredAction(authorization=WithPermission(Permission.AddDatasetsMetadata)) { request =>
      Logger.debug("Adding metadata to dataset " + id)
      Dataset.addMetadata(id, Json.stringify(request.body))
      index(id)
      Ok(toJson(Map("status" -> "success")))
  }

  def addUserMetadata(id: String) = SecuredAction(authorization=WithPermission(Permission.AddDatasetsMetadata)) { request =>
      Logger.debug("Adding user metadata to dataset " + id)
      Dataset.addUserMetadata(id, Json.stringify(request.body))
      index(id)
      Ok(toJson(Map("status" -> "success")))
    }

  def datasetFilesGetIdByDatasetAndFilename(datasetId: String, filename: String): Option[String] = {
      Services.datasets.get(datasetId) match {
        case Some(dataset) => {
          //        val files = dataset.files map { f =>
          //          FileDAO.get(f.id.toString).get
          //        }		  
          for (file <- dataset.files) {
            if (file.filename.equals(filename)) {
              return Some(file.id.toString)
            }
          }
          Logger.error("File does not exist in dataset" + datasetId); return None
        }
        case None => { Logger.error("Error getting dataset" + datasetId); return None }
      }
    }

  def datasetFilesList(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDataset)) { request =>
      Services.datasets.get(id) match {
        case Some(dataset) => {
          val list = for (f <- dataset.files) yield jsonFile(f)
          Ok(toJson(list))
        }
        case None => { Logger.error("Error getting dataset" + id); InternalServerError }
      }
    }

  def jsonFile(file: File): JsValue = {
    toJson(Map("id" -> file.id.toString, "filename" -> file.filename, "contentType" -> file.contentType, "date-created" -> file.uploadDate.toString(), "size" -> file.length.toString))
  }

  def index(id: String) {
    Services.datasets.get(id) match {
      case Some(dataset) => {
        var tagListBuffer = new ListBuffer[String]()
        
        for (tag <- dataset.tags){
          tagListBuffer += tag.name
        }          
        
        val tagsJson = new JSONArray(tagListBuffer.toList)

        Logger.debug("tagStr=" + tagsJson);

        val comments = for(comment <- Comment.findCommentsByDatasetId(id,false)) yield {
          comment.text
        }
        val commentJson = new JSONArray(comments)

        Logger.debug("commentStr=" + commentJson.toString())
        
        val usrMd = Dataset.getUserMetadataJSON(id)
        Logger.debug("usrmd=" + usrMd)
        
        val techMd = Dataset.getTechnicalMetadataJSON(id)
        Logger.debug("techmd=" + techMd)

        current.plugin[ElasticsearchPlugin].foreach {
          _.index("data", "dataset", id,
            List(("name", dataset.name), ("description", dataset.description), ("tag", tagsJson.toString), ("comments", commentJson.toString), ("usermetadata", usrMd), ("technicalmetadata", techMd)))
        }
      }
      case None => Logger.error("Dataset not found: " + id)
    }
  }

  def tag(id: String) = SecuredAction(parse.json, authorization = WithPermission(Permission.CreateTags)) { implicit request =>
    Logger.debug("Tagging " + request.body)
    
    val userObj = request.user;
    val tagId = new ObjectId
    
    request.body.\("tag").asOpt[String].map { tag =>
      Logger.debug("Tagging " + id + " with " + tag)
      val tagObj = Tag(id = tagId, name = tag, userId = userObj.get.identityId.toString, created = new Date)
      Dataset.tag(id, tagObj)
      index(id)
    }
    Ok(toJson(tagId.toString))
  }
    
  def removeTag(id: String) = SecuredAction(parse.json,authorization=WithPermission(Permission.DeleteTags)) {implicit request =>
    Logger.debug("Removing tag " + request.body)
    
    request.body.\("tagId").asOpt[String].map { tagId =>
		  Logger.debug("Removing " + tagId + " from "+ id)
		  Dataset.removeTag(id, tagId)
		}
      Ok(toJson(""))    
  }

  def comment(id: String) = SecuredAction(authorization=WithPermission(Permission.CreateComments)) { implicit request =>
    request.user match {
      case Some(identity) => {
	    request.body.\("text").asOpt[String] match {
	      case Some(text) => {
	        val comment = new Comment(identity, text, dataset_id=Some(id))
	        Comment.save(comment)
	        index(id)
	        Ok(comment.id.toString)
	      }
	      case None => {
	        Logger.error("no text specified.")
	        BadRequest
	      }
	    }
      }
      case None => BadRequest
    }
  }

  /**
   * List datasets satisfying a user metadata search tree.
   */
  def searchDatasetsUserMetadata = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { request =>
      Logger.debug("Searching datasets' user metadata for search tree.")
      
      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: "+searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]
      
      var searchQuery = Dataset.searchUserMetadataFormulateQuery(searchTree)
      
      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning datasets list.")

      val list = for (dataset <- searchQuery) yield jsonDataset(dataset)
      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
    }
  
  /**
   * List datasets satisfying a general metadata search tree.
   */
  def searchDatasetsGeneralMetadata = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { request =>
      Logger.debug("Searching datasets' metadata for search tree.")
      
      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: "+searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]
      
      var searchQuery = Dataset.searchAllMetadataFormulateQuery(searchTree)
      
      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning datasets list.")

      val list = for (dataset <- searchQuery) yield jsonDataset(dataset)
      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
    } 
  
  /**
   * Return whether a dataset is currently being processed.
   */
  def isBeingProcessed(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDataset)) { request =>
  	Services.datasets.get(id)  match {
  	  case Some(dataset) => {
  	    val files = dataset.files map { f =>
          FileDAO.get(f.id.toString).get
        }
  	    
  	    var isActivity = "false"
        try{
        	for(f <- files){
        		Extraction.findIfBeingProcessed(f.id) match{
        			case false => 
        			case true => { 
        				isActivity = "true"
        				throw ActivityFound
        			  }  
        			}

        	}
        }catch{
          case ActivityFound =>
        }
        
        Ok(toJson(Map("isBeingProcessed"->isActivity))) 
  	  }
  	  case None => {Logger.error("Error getting dataset" + id); InternalServerError}
  	}  	
  }
  
  
  
  def jsonPreviewsFiles(filesList: List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]): JsValue = {
    val list = for (filePrevs <- filesList) yield jsonPreviews(filePrevs._1, filePrevs._2)
    toJson(list)
  }  
  def jsonPreviews(prvFile: models.File, prvs: Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]): JsValue = {
    val list = for (prv <- prvs) yield jsonPreview(prv._1, prv._2, prv._3, prv._4, prv._5, prv._6, prv._7)
    val listJson = toJson(list.toList)
    toJson(Map[String, JsValue]("file_id" -> JsString(prvFile.id.toString), "previews" -> listJson))
  }
  def jsonPreview(pvId: java.lang.String, pId: String, pPath: String, pMain: String, pvRoute: java.lang.String, pvContentType: String, pvLength: Long): JsValue = {
    if(pId.equals("X3d"))
    	toJson(Map("pv_id" -> pvId, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString, "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString,
    			"pv_annotationsEditPath" -> api.routes.Previews.editAnnotation(pvId).toString, "pv_annotationsListPath" -> api.routes.Previews.listAnnotations(pvId).toString, "pv_annotationsAttachPath" -> api.routes.Previews.attachAnnotation(pvId).toString)) 
    else    
    	toJson(Map("pv_id" -> pvId, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString , "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString))  
  }  
  def getPreviews(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDataset)) { request =>
    Services.datasets.get(id)  match {
      case Some(dataset) => {
        val files = dataset.files map { f =>
          FileDAO.get(f.id.toString).get
        }
        
        val datasetWithFiles = dataset.copy(files = files)
        val previewers = Previewers.findPreviewers
        val previewslist = for(f <- datasetWithFiles.files; if(f.showPreviews.equals("DatasetLevel"))) yield {
          val pvf = for(p <- previewers ; pv <- f.previews; if (p.contentType.contains(pv.contentType))) yield { 
            (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id.toString).toString, pv.contentType, pv.length)
          }        
          if (pvf.length > 0) {
            (f -> pvf)
          } else {
  	        val ff = for(p <- previewers ; if (p.contentType.contains(f.contentType))) yield {
  	          (f.id.toString, p.id, p.path, p.main, controllers.routes.Files.file(f.id.toString) + "/blob", f.contentType, f.length)
  	        }
  	        (f -> ff)
          }
        }
        Ok(jsonPreviewsFiles(previewslist.asInstanceOf[List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]])) 
      }
      case None => {Logger.error("Error getting dataset" + id); InternalServerError}
    }
  }
  
  def deleteDataset(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.DeleteDatasets)) { request =>
    Services.datasets.get(id)  match {
      case Some(dataset) => {
        Dataset.removeDataset(id)
        Ok(toJson(Map("status"->"success")))
      }
      case None => Ok(toJson(Map("status"->"success")))
    }
  }
  
  
  def getRDFUserMetadata(id: String, mappingNumber: String="1") = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDatasetsMetadata)) {implicit request =>
    Services.datasets.get(id) match { 
            case Some(dataset) => {
              val theJSON = Dataset.getUserMetadataJSON(id)
              val fileSep = System.getProperty("file.separator")
	          var resultDir = play.api.Play.configuration.getString("rdfdumptemporary.dir").getOrElse("") + fileSep + new ObjectId().toString
	          new java.io.File(resultDir).mkdir()
              
              if(!theJSON.replaceAll(" ","").equals("{}")){
	              val xmlFile = jsonToXML(theJSON)	              	              
	              new LidoToCidocConvertion(play.api.Play.configuration.getString("datasetsxmltordfmapping.dir_"+mappingNumber).getOrElse(""), xmlFile.getAbsolutePath(), resultDir)	                            
	              xmlFile.delete()
              }
              else{
                new java.io.File(resultDir + fileSep + "Results.rdf").createNewFile()
              }
              val resultFile = new java.io.File(resultDir + fileSep + "Results.rdf")
              
              Ok.chunked(Enumerator.fromStream(new FileInputStream(resultFile)))
		            	.withHeaders(CONTENT_TYPE -> "application/rdf+xml")
		            	.withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + resultFile.getName()))
            }
            case None => BadRequest(toJson("Dataset not found " + id))
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
  
  def getRDFURLsForDataset(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDatasetsMetadata)) { request =>
    Services.datasets.get(id)  match {
      case Some(dataset) => {
        
        //RDF from XML files in the dataset itself (for XML metadata-only files)
        val previewsList = PreviewDAO.findByDatasetId(new ObjectId(id))
        var rdfPreviewList = List.empty[models.Preview]
        for(currPreview <- previewsList){
          if(currPreview.contentType.equals("application/rdf+xml")){
            rdfPreviewList = rdfPreviewList :+ currPreview
          }
        }        
        var hostString = "http://" + request.host + request.path.replaceAll("datasets/getRDFURLsForDataset/[A-Za-z0-9_]*$", "previews/")
        var list = for (currPreview <- rdfPreviewList) yield Json.toJson(hostString + currPreview.id.toString)
        
        for(file <- dataset.files){
           val filePreviewsList = PreviewDAO.findByFileId(file.id)
           var fileRdfPreviewList = List.empty[models.Preview]
           for(currPreview <- filePreviewsList){
	           if(currPreview.contentType.equals("application/rdf+xml")){
	        	   fileRdfPreviewList = fileRdfPreviewList :+ currPreview
	           }
           }
           val filesList = for (currPreview <- fileRdfPreviewList) yield Json.toJson(hostString + currPreview.id.toString)
           list = list ++ filesList
        }
        
        //RDF from export of dataset community-generated metadata to RDF
        var connectionChars = ""
		if(hostString.contains("?")){
			connectionChars = "&mappingNum="
		}
		else{
			connectionChars = "?mappingNum="
		}        
        hostString = "http://" + request.host + request.path.replaceAll("/getRDFURLsForDataset/", "/rdfUserMetadataDataset/") + connectionChars
        
        val mappingsQuantity = Integer.parseInt(play.api.Play.configuration.getString("datasetsxmltordfmapping.dircount").getOrElse("1"))
        for(i <- 1 to mappingsQuantity){
          var currHostString = hostString + i
          list = list :+ Json.toJson(currHostString)
        }

        val listJson = toJson(list.toList)
        
        Ok(listJson) 
      }
      case None => {Logger.error("Error getting dataset" + id); InternalServerError}
    }
    
  }
  
  def getTechnicalMetadataJSON(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDatasetsMetadata)) { request =>
    Services.datasets.get(id)  match {
      case Some(dataset) => {
        Ok(Dataset.getTechnicalMetadataJSON(id))
      }
      case None => {Logger.error("Error finding dataset" + id); InternalServerError}      
    }
  }
  
}
