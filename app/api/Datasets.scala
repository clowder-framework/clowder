/**
 *
 */
package api

import java.util.Date
import com.wordnik.swagger.annotations.{ApiResponse, ApiResponses, Api, ApiOperation}
import models._
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.mvc.AnyContent
import jsonutils.JsonUtil
import controllers.Previewers
import org.bson.types.ObjectId
import play.api.Play.current
import javax.inject.{Singleton, Inject}
import com.mongodb.casbah.Imports._
import org.json.JSONObject
import Transformation.LidoToCidocConvertion
import java.io.BufferedWriter
import java.io.FileWriter
import play.api.libs.iteratee.Enumerator
import java.io.FileInputStream
import play.api.libs.concurrent.Execution.Implicits._
import services._
import play.api.libs.json.JsString
import play.api.libs.json.JsResult
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import util.License
import scala.Some
import models.File
import play.api.Play.configuration
import controllers.Utils
import services._

/**
 * Dataset API.
 *
 * @author Luigi Marini
 *
 */
@Api(value = "/datasets", listingPath = "/api-docs.json/datasets", description = "A dataset is a container for files and metadata")
@Singleton
class Datasets @Inject()(
  datasets: DatasetService,
  files: FileService,
  collections: CollectionService,
  sections: SectionService,
  comments: CommentService,
  previews: PreviewService,
  extractions: ExtractionService,
  rdfsparql: RdfSPARQLService,
  events: EventService,
  spaces: SpaceService,
  userService: UserService) extends ApiController {


  /**
   * Generic function to call the right method to return a list of datasets. This will allow to search for
   * specific datasets if date and/or title are given.
   */
  private def listDataset(title: Option[String], date: Option[String], limit: Int,  user: Option[User], showAll: Boolean):List[Dataset] = {
    (title, date) match {
      case (Some(t), Some(d)) => {
        datasets.listAccess(d, true, limit, t, user, showAll)
      }
      case (Some(t), None) => {
        datasets.listAccess(limit, t, user, showAll)
      }
      case (None, Some(d)) => {
        datasets.listAccess(d, true, limit, user, showAll)
      }
      case (None, None) => {
        datasets.listAccess(limit, user, showAll)
      }
    }
  }

  /**
   * List all datasets.
   */
  @ApiOperation(value = "List all datasets",
    notes = "Returns list of datasets and descriptions.",
    responseClass = "None", httpMethod = "GET")
  def list(title: Option[String], date: Option[String], limit: Int) = PrivateServerAction { implicit request =>
    Ok(toJson(listDataset(title, date, limit, request.user, request.superAdmin)))
  }

  /**
   * List all datasets outside a collection.
   */
  @ApiOperation(value = "List all datasets outside a collection",
    notes = "Returns list of datasets and descriptions.",
    responseClass = "None", httpMethod = "GET")
  def listOutsideCollection(collectionId: UUID) = PrivateServerAction { implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val list = for (dataset <- datasets.listAccess(0, request.user, request.superAdmin); if (!datasets.isInCollection(dataset, collection)))
          yield dataset
        Ok(toJson(list))
      }
      case None => {
        val list = datasets.listAccess(0, request.user, request.superAdmin)
        Ok(toJson(list))
      }
    }
  }

  /**
   * Create new dataset
   */
  @ApiOperation(value = "Create new dataset",
      notes = "New dataset containing one existing file, based on values of fields in attached JSON. Returns dataset id as JSON object.",
      responseClass = "None", httpMethod = "POST")
  def createDataset() = PermissionAction(Permission.CreateDataset)(parse.json) { implicit request =>
    Logger.debug("--- API Creating new dataset ----")
    (request.body \ "name").asOpt[String].map { name =>
      (request.body \ "description").asOpt[String].map { description =>
        (request.body \ "file_id").asOpt[String].map { file_id =>
            (request.body \ "space").asOpt[String].map { space =>
                  files.get(UUID(file_id)) match {
                    case Some(file) =>
                      var d : Dataset = null
                      if (space == "default") {
                          d = Dataset(name=name,description=description, created=new Date(), author=request.user.get, licenseData = License.fromAppConfig())
                      }
                      else {
                          d = Dataset(name=name,description=description, created=new Date(), author=request.user.get, licenseData = License.fromAppConfig(), spaces = List(UUID(space)))
                      }
                      events.addObjectEvent(request.user, d.id, d.name, "create_dataset")
                      datasets.insert(d) match {
                        case Some(id) => {
                          files.index(UUID(file_id))
                          if(!file.xmlMetadata.isEmpty) {
                            val xmlToJSON = files.getXMLMetadataJSON(UUID(file_id))
                            datasets.addXMLMetadata(UUID(id), UUID(file_id), xmlToJSON)
                            current.plugin[ElasticsearchPlugin].foreach {
                             _.index("data", "dataset", UUID(id),
                               List(("name", d.name), ("description", d.description), ("xmlmetadata", xmlToJSON)))
                            }
                          } else {
                            current.plugin[ElasticsearchPlugin].foreach {
                              _.index("data", "dataset", UUID(id), List(("name", d.name), ("description", d.description)))
                            }
                          }
                          current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification(Utils.baseUrl(request),"Dataset","added",id, name)}                  
                          Ok(toJson(Map("id" -> id)))
                        }
                        case None => Ok(toJson(Map("status" -> "error")))
                      }
                    case None => BadRequest(toJson("Bad file_id = " + file_id))
                }
          }.getOrElse(BadRequest(toJson("Missing parameter [space]")))
        }.getOrElse(BadRequest(toJson("Missing parameter [file_id]")))
      }.getOrElse(BadRequest(toJson("Missing parameter [description]")))
    }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }
  
  /**
   * Create new dataset with no file required. However if there are comma separated file IDs passed in, add all of those as existing
   * files. This is to facilitate multi-file-uploader usage for new files, as well as to allow multiple existing files to be
   * added as part of dataset creation.
   * 
   * A JSON document is the payload for this endpoint. Required elements are name, description, and space. Optional element is
   * existingfiles, which will be a comma separated String of existing file IDs to be added to the new dataset. 
   */
  @ApiOperation(value = "Create new dataset with no file",
      notes = "New dataset requiring zero files based on values of fields in attached JSON. Returns dataset id as JSON object. Requires name, description, and space. Optional list of existing file ids to add.",
      responseClass = "None", httpMethod = "POST")
  def createEmptyDataset() = PermissionAction(Permission.CreateDataset)(parse.json) { implicit request =>
    (request.body \ "name").asOpt[String].map { name =>
      (request.body \ "description").asOpt[String].map { description =>   
          (request.body \ "space").asOpt[List[String]].map { space =>
              var spaceList: List[UUID] = List.empty;
              space.map {
                aSpace => spaceList = UUID(aSpace) :: spaceList
              }
              var d : Dataset = null
              if (space == "default") {
                  d = Dataset(name=name,description=description, created=new Date(), author=request.user.get, licenseData = License.fromAppConfig())
              }
              else {
              	  d = Dataset(name=name,description=description, created=new Date(), author=request.user.get, licenseData = License.fromAppConfig(), spaces = spaceList)
              }
            events.addObjectEvent(request.user, d.id, d.name, "create_dataset")
            datasets.insert(d) match {
                case Some(id) => {
                  //In this case, the dataset has been created and inserted. Now notify the space service and check
                  //for the presence of existing files.
                  Logger.debug("About to call addDataset on spaces service")
                  //Below call is not what is needed? That already does what we are doing in the Dataset constructor... 
                  //Items from space model still missing. New API will be needed to update it most likely.

                  space.map {
                    aSpace => if(aSpace != "default") spaces.addDataset(UUID(id), UUID(aSpace))
                  }
                  (request.body \ "existingfiles").asOpt[String].map { fileString =>
                    var idArray = fileString.split(",").map(_.trim())
                    for (anId <- idArray) {
                      datasets.get(UUID(id)) match {
                        case Some(dataset) => {
                          files.get(UUID(anId)) match {
                            case Some(file) => {
                              attachExistingFileHelper(UUID(id), UUID(anId), dataset, file, request.user)
                              Ok(toJson(Map("status" -> "success")))
                            }
                            case None => {
                              Logger.error("Error getting file" + anId)
                              BadRequest(toJson(s"The given file id $anId is not a valid ObjectId."))
                            }
                          }
                        }
                        case None => {
                          Logger.error("Error getting dataset" + id)
                          BadRequest(toJson(s"The given dataset id $id is not a valid ObjectId."))
                        }
                      }
                    }
                    Ok(toJson(Map("id" -> id)))
                  }.getOrElse(Ok(toJson(Map("id" -> id))))
                }
                case None => Ok(toJson(Map("status" -> "error")))
              }            
          }.getOrElse(BadRequest(toJson("Missing parameter [space]")))
      }.getOrElse(BadRequest(toJson("Missing parameter [description]")))
    }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }
  
  /**
   * Create new dataset with no file required. However if there are comma separated file IDs passed in, add all of those as existing
   * files. This is to facilitate multi-file-uploader usage for new files, as well as to allow multiple existing files to be
   * added as part of dataset creation.
   * 
   * A JSON document is the payload for this endpoint. Required elements are name and description. Optional element is existingfiles,
   * which will be a comma separated String of existing file IDs to be added to the new dataset. 
   */
  @ApiOperation(value = "Attach multiple files to an existing dataset",
      notes = "Add multiple files, by ID, to a dataset that is already in the system. Requires file ids and dataset id.",
      responseClass = "None", httpMethod = "POST")
  def attachMultipleFiles() = PermissionAction(Permission.AddResourceToDataset)(parse.json) { implicit request =>
      (request.body \ "datasetid").asOpt[String].map { dsId =>
          (request.body \ "existingfiles").asOpt[String].map { fileString =>
                  var idArray = fileString.split(",").map(_.trim())
                  for (anId <- idArray) {                      
                      datasets.get(UUID(dsId)) match {
					      case Some(dataset) => {
					          files.get(UUID(anId)) match {
					              case Some(file) => {
					            	  attachExistingFileHelper(UUID(dsId), UUID(anId), dataset, file, request.user)
					            	  Ok(toJson(Map("status" -> "success")))
					              }
					              case None => {
					            	  Logger.error("Error getting file" + anId)
					            	  BadRequest(toJson(s"The given file id $anId is not a valid ObjectId."))
					              }
					          }
				        }
				        case None => {
				            Logger.error("Error getting dataset" + dsId)
				            BadRequest(toJson(s"The given dataset id $dsId is not a valid ObjectId."))
				        }
				      }                      
                  }
                  Ok(toJson(Map("id" -> dsId)))
              }.getOrElse(BadRequest(toJson("Missing parameter [existingfiles]")))
      }.getOrElse(BadRequest(toJson("Missing parameter [datasetid]")))
  }

  /**
   * Reindex the given dataset, if recursive is set to true it will
   * also reindex all files in that dataset.
   */
  @ApiOperation(value = "Reindex a dataset",
    notes = "Reindex the existing dataset, if recursive is set to true if will also reindex all files in that dataset.",
    httpMethod = "GET")
  def reindex(id: UUID, recursive: Boolean) = PermissionAction(Permission.CreateDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
      datasets.get(id) match {
        case Some(ds) => {
          current.plugin[ElasticsearchPlugin].foreach {
            _.index(ds, recursive)
          }
          Ok(toJson(Map("status" -> "success")))
        }
        case None => {
          Logger.error("Error getting dataset" + id)
          BadRequest(toJson(s"The given dataset id $id is not a valid ObjectId."))
        }
      }
  }
  
  /**
   * Functionality broken out from attachExistingFile, in order to allow the core work of file attachment to be called from
   * multiple API endpoints.
   * 
   * @param dsId A UUID that specifies the dataset that will be modified
   * @param fileId A UUID that specifies the file to attach to the dataset
   * @param dataset Reference to the model of the dataset that is specified
   * @param file Reference to the model of the file that is specified   
   */
  def attachExistingFileHelper(dsId: UUID, fileId: UUID, dataset: Dataset, file: File, user: Option[User]) = {
      if (!files.isInDataset(file, dataset)) {
            datasets.addFile(dsId, file)	 
            events.addSourceEvent(user , file.id, file.filename, dataset.id, dataset.name, "attach_file_dataset")        
            files.index(fileId)
            if (!file.xmlMetadata.isEmpty){
              datasets.index(dsId)
            }	            
   
            if(dataset.thumbnail_id.isEmpty && !file.thumbnail_id.isEmpty){
                datasets.updateThumbnail(dataset.id, UUID(file.thumbnail_id.get))
                
                for(collectionId <- dataset.collections){
                  collections.get(UUID(collectionId)) match{
                    case Some(collection) =>{
                    	if(collection.thumbnail_id.isEmpty){ 
                    		collections.updateThumbnail(collection.id, UUID(file.thumbnail_id.get))
                    	}
                    }
                    case None=>Logger.debug(s"No collection found with id $collectionId") 
              }
            }
        }
        
        //add file to RDF triple store if triple store is used
        if (file.filename.endsWith(".xml")) {
          configuration.getString("userdfSPARQLStore").getOrElse("no") match {
            case "yes" => rdfsparql.linkFileToDataset(fileId, dsId)
            case _ => Logger.trace("Skipping RDF store. userdfSPARQLStore not enabled in configuration file")
          }
        }
        Logger.info("Adding file to dataset completed")
      } else {
          Logger.info("File was already in dataset.")
      }
  }

  @ApiOperation(value = "Attach existing file to dataset",
      notes = "If the file is an XML metadata file, the metadata are added to the dataset.",
      responseClass = "None", httpMethod = "POST")
  def attachExistingFile(dsId: UUID, fileId: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, dsId))) { implicit request =>
     datasets.get(dsId) match {
      case Some(dataset) => {
        files.get(fileId) match {
          case Some(file) => {
        	  attachExistingFileHelper(dsId, fileId, dataset, file, request.user)
              Ok(toJson(Map("status" -> "success")))
            }
            case None => {
                Logger.error("Error getting file" + fileId)
                BadRequest(toJson(s"The given dataset id $dsId is not a valid ObjectId."))
            }
          }
        }
        case None => {
            Logger.error("Error getting dataset" + dsId)
            BadRequest(toJson(s"The given dataset id $dsId is not a valid ObjectId."))
        }
      }
  }

  @ApiOperation(value = "Detach file from dataset",
      notes = "File is not deleted, only separated from the selected dataset. If the file is an XML metadata file, the metadata are removed from the dataset.",
      responseClass = "None", httpMethod = "POST")
  def detachFile(datasetId: UUID, fileId: UUID, ignoreNotFound: String) = PermissionAction(Permission.CreateDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
     datasets.get(datasetId) match{
      case Some(dataset) => {
    	  detachFileHelper(datasetId, fileId, dataset, request.user)
        }
        case None => {
          ignoreNotFound match {
            case "True" => Ok(toJson(Map("status" -> "success")))
            case "False" => Logger.error(s"Error getting dataset $datasetId"); InternalServerError
          }
        }
      }
  }
  
  /**
   * Utility function to consolidate the utility portions of the detach file functionality 
   * so that it can be easily called from multiple API operations.
   * 
   * @param datasetId The id of the dataset that a file is being detached from
   * @param fileId The id of the file to detach from the dataset
   * @param dataset The reference to the model of the dataset being operated on
   * 
   */
  def detachFileHelper(datasetId: UUID, fileId: UUID, dataset: models.Dataset, user: Option[User]) = {      
	  files.get(fileId) match {
		  case Some(file) => {		       
			  if(files.isInDataset(file, dataset)){
				  //remove file from dataset
				  datasets.removeFile(dataset.id, file.id)
          events.addSourceEvent(user , file.id, file.filename, dataset.id, dataset.name, "detach_file_dataset") 
				  files.index(fileId)
				  if (!file.xmlMetadata.isEmpty)
					  datasets.index(datasetId)
	
				  Logger.debug("----- Removing a file from dataset completed")

				  if(!dataset.thumbnail_id.isEmpty && !file.thumbnail_id.isEmpty){
					  if(dataset.thumbnail_id.get == file.thumbnail_id.get){
						  datasets.createThumbnail(dataset.id)

						  for(collectionId <- dataset.collections){
							  collections.get(UUID(collectionId)) match{
							  case Some(collection) =>{		                              
								  if(!collection.thumbnail_id.isEmpty){
									  if(collection.thumbnail_id.get == dataset.thumbnail_id.get){
										  collections.createThumbnail(collection.id)
									  }		                        
								  }
							  }
							  case None=>{}
							  }
						  }
					  }		                        
				  }
	
				  //remove link between dataset and file from RDF triple store if triple store is used
				  if (file.filename.endsWith(".xml")) {
					  configuration.getString("userdfSPARQLStore").getOrElse("no") match {
						  case "yes" => rdfsparql.detachFileFromDataset(fileId, datasetId)
						  case _ => Logger.trace("Skipping RDF store. userdfSPARQLStore not enabled in configuration file")
					  }
				  }
			  }
			  else  Logger.debug("----- File was already out of the dataset.")
			  Ok(toJson(Map("status" -> "success")))
		  }
		  case None => {
		       Logger.debug("----- detach helper NONE case")
		       Ok(toJson(Map("status" -> "success")))
		  }
	  }
  }
  
  //////////////////

  @ApiOperation(value = "List all datasets in a collection", notes = "Returns list of datasets and descriptions.", responseClass = "None", httpMethod = "GET")
  def listInCollection(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    Ok(toJson(datasets.listCollection(collectionId.stringify)))
  }

  @ApiOperation(value = "Add metadata to dataset", notes = "Returns success of failure", responseClass = "None", httpMethod = "POST")
  def addMetadata(id: UUID) = PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
      Logger.debug(s"Adding metadata to dataset $id")
      datasets.addMetadata(id, Json.stringify(request.body))
      datasets.index(id)
      Ok(toJson(Map("status" -> "success")))
  }

  @ApiOperation(value = "Add user-generated metadata to dataset",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def addUserMetadata(id: UUID) = PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user  
    Logger.debug(s"Adding user metadata to dataset $id")
      datasets.addUserMetadata(id, Json.stringify(request.body))
    
    datasets.get(id) match {
      case Some(dataset) => {
        events.addObjectEvent(user, id, dataset.name, "addMetadata_dataset")
      }
    }
     
    datasets.index(id)
    configuration.getString("userdfSPARQLStore").getOrElse("no") match {
      case "yes" => datasets.setUserMetadataWasModified(id, true)
      case _ => Logger.debug("userdfSPARQLStore not enabled")
    }
    Ok(toJson(Map("status" -> "success")))
  }
  
  
  def datasetFilesGetIdByDatasetAndFilename(datasetId: UUID, filename: String): Option[String] = {
    datasets.get(datasetId) match {
      case Some(dataset) => {
        for (file <- dataset.files) {
          if (file.filename.equals(filename)) {
            return Some(file.id.toString)
          }
        }
        Logger.error(s"File does not exist in dataset $datasetId.")
        None
      }
      case None => Logger.error(s"Error getting dataset $datasetId."); None
    }
  }

  @ApiOperation(value = "List files in dataset",
      notes = "Datasets and descriptions.",
      responseClass = "None", httpMethod = "GET")
  def datasetFilesList(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
      datasets.get(id) match {
        case Some(dataset) => {
          val list = for (f <- dataset.files) yield jsonFile(f)
          Ok(toJson(list))
        }
        case None => Logger.error("Error getting dataset" + id); InternalServerError
      }
  }

  def jsonFile(file: File): JsValue = {
    toJson(Map("id" -> file.id.toString, "filename" -> file.filename, "contentType" -> file.contentType,
               "date-created" -> file.uploadDate.toString(), "size" -> file.length.toString))
  }
  
  //Update Dataset Information code starts

  /**
   * REST endpoint: POST: update the administrative information associated with a specific Dataset
   * 
   *  Takes one arg, id:
   *  
   *  id, the UUID associated with this dataset 
   *  
   *  The data contained in the request body will contain data to be updated associated by the following String key-value pairs:
   *  
   *  description -> The text for the updated description for the dataset
   *  name -> The text for the updated name for this dataset
   *  
   *  Currently description and owner are the only fields that can be modified, however this api is extensible enough to add other existing
   *  fields, or new fields, in the future.  
   *  
   */
  @ApiOperation(value = "Update dataset administrative information",
      notes = "Takes one argument, a UUID of the dataset. Request body takes key-value pairs for name and description.",
      responseClass = "None", httpMethod = "POST")
  def updateInformation(id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user  
    if (UUID.isValid(id.stringify)) {          

          //Set up the vars we are looking for
          var description: String = null;
          var name: String = null;
          
          var aResult: JsResult[String] = (request.body \ "description").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {
                description = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"description data is missing."))
              }                            
          }
          
          aResult = (request.body \ "name").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {
                name = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"name data is missing."))
              }                            
          }
          Logger.debug(s"updateInformation for dataset with id  $id. Args are $description and $name")
          
          datasets.updateInformation(id, description, name)
          datasets.get(id) match {
            case Some(dataset) => {
              events.addObjectEvent(user, id, dataset.name, "update_dataset_information")
            }
          }
          Ok(Json.obj("status" -> "success"))
      } 
      else {
        Logger.error(s"The given id $id is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
      }
  }

  @ApiOperation(value = "Update dataset name",
    notes = "Takes one argument, a UUID of the dataset. Request body takes key-value pair for name.",
    responseClass = "None", httpMethod = "POST")
  def updateName(id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {

      //Set up the vars we are looking for
      var name: String = null;

      val aResult = (request.body \ "name").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          name = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"name data is missing."))
        }
      }
      Logger.debug(s"updateInformation for dataset with id  $id. New name is: $name")

      datasets.updateName(id, name)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, "update_dataset_information")
        }
      }
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }

  @ApiOperation(value = "Update dataset description.",
    notes = "Takes one argument, a UUID of the dataset. Request body takes key-value pair for description.",
    responseClass = "None", httpMethod = "POST")
  def updateDescription(id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {

      //Set up the vars we are looking for
      var description: String = null;

      var aResult: JsResult[String] = (request.body \ "description").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          description = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"description data is missing."))
        }
      }
      Logger.debug(s"updateInformation for dataset with id  $id. New description is:  $description ")

      datasets.updateDescription(id, description)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, "update_dataset_information")
        }
      }
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }
  //End, Update Dataset Information code
  
  //Update License code 
  /**
   * REST endpoint: POST: update the license data associated with a specific Dataset
   * 
   *  Takes one arg, id:
   *  
   *  id, the UUID associated with this dataset 
   *  
   *  The data contained in the request body will be containe the following key-value pairs:
   *  
   *  licenseType, currently:
   *        license1 - corresponds to Limited 
   *        license2 - corresponds to Creative Commons
   *        license3 - corresponds to Public Domain
   *        
   *  rightsHolder, currently only required if licenseType is license1. Reflects the specific name of the organization or person that holds the rights
   *   
   *  licenseText, currently tied to the licenseType
   *        license1 - Free text that a user can enter to describe the license
   *        license2 - 1 of 6 options (or their abbreviations) that reflects the specific set of 
   *        options associated with the Creative Commons license, these are:
   *            Attribution-NonCommercial-NoDerivs (by-nc-nd)
   *            Attribution-NoDerivs (by-nd)
   *            Attribution-NonCommercial (by-nc)
   *            Attribution-NonCommercial-ShareAlike (by-nc-sa)
   *            Attribution-ShareAlike (by-sa)
   *            Attribution (by)
   *        license3 - Public Domain Dedication
   *        
   *  licenseUrl, free text that a user can enter to go with the licenseText in the case of license1. Fixed URL's for the other 2 cases.
   *  
   *  allowDownload, true or false, whether the file or dataset can be downloaded. Only relevant for license1 type.  
   */
  @ApiOperation(value = "Update license information to a dataset",
      notes = "Takes four arguments, all Strings. licenseType, rightsHolder, licenseText, licenseUrl",
      responseClass = "None", httpMethod = "POST")
  def updateLicense(id: UUID) = PermissionAction(Permission.EditLicense, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
      if (UUID.isValid(id.stringify)) {

          //Set up the vars we are looking for
          var licenseType: String = null;
          var rightsHolder: String = null;
          var licenseText: String = null;
          var licenseUrl: String = null;
          var allowDownload: String = null;
          
          var aResult: JsResult[String] = (request.body \ "licenseType").validate[String]
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {
                licenseType = s.get                
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"licenseType data is missing."))
              }                            
          }
          
          aResult = (request.body \ "rightsHolder").validate[String]
                  
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {
                rightsHolder = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"rightsHolder data is missing.")) 
              }              
          }
          
          aResult = (request.body \ "licenseText").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {                
                licenseText = s.get
                
                //Modify the abbreviations if they were sent in that way
                if (licenseText == "by-nc-nd") {
                    licenseText = "Attribution-NonCommercial-NoDerivs"
                }
                else if (licenseText == "by-nd") {
                    licenseText = "Attribution-NoDerivs"
                }
                else if (licenseText == "by-nc") {
                    licenseText = "Attribution-NonCommercial"
                }
                else if (licenseText == "by-nc-sa") {
                    licenseText = "Attribution-NonCommercial-ShareAlike"
                }
                else if (licenseText == "by-sa") {
                    licenseText = "Attribution-ShareAlike"
                }
                else if (licenseText == "by") {
                    licenseText = "Attribution"
                }
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"licenseText data is missing."))
              }              
          }
          
          aResult = (request.body \ "licenseUrl").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {                
                licenseUrl = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"licenseUrl data is missing."))
              }
          }
          
          aResult = (request.body \ "allowDownload").validate[String]
          
          // Pattern matching
          aResult match {
              case s: JsSuccess[String] => {                
                allowDownload = s.get
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson(s"allowDownload data is missing."))
              }
          }          
          
          Logger.debug(s"updateLicense for dataset with id  $id. Args are $licenseType, $rightsHolder, $licenseText, $licenseUrl, $allowDownload")
          
          datasets.updateLicense(id, licenseType, rightsHolder, licenseText, licenseUrl, allowDownload)
          Ok(Json.obj("status" -> "success"))
      } 
      else {
        Logger.error(s"The given id $id is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
      }
  }  
  //End, Update License code
  
  // ---------- Tags related code starts ------------------
  /**
   * REST endpoint: GET: get the tag data associated with this section.
   * Returns a JSON object of multiple fields.
   * One returned field is "tags", containing a list of string values.
   */
  @ApiOperation(value = "Get the tags associated with this dataset", notes = "Returns a JSON object of multiple fields", responseClass = "None", httpMethod = "GET")
  def getTags(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
      Logger.info(s"Getting tags for dataset with id  $id.")
      /* Found in testing: given an invalid ObjectId, a runtime exception
       * ("IllegalArgumentException: invalid ObjectId") occurs.  So check it first.
       */
      if (UUID.isValid(id.stringify)) {
        datasets.get(id) match {
          case Some(dataset) =>
            Ok(Json.obj("id" -> dataset.id.toString, "name" -> dataset.name, "tags" -> Json.toJson(dataset.tags.map(_.name))))
          case None => {
            Logger.error(s"The dataset with id $id is not found.")
            NotFound(toJson(s"The dataset with id $id is not found."))
          }
        }
      } else {
        Logger.error(s"The given id $id is not a valid ObjectId.")
        BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
      }
  }

  @ApiOperation(value = "Remove tag of dataset",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def removeTag(id: UUID) = PermissionAction(Permission.DeleteTag, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
      Logger.debug("Removing tag " + request.body)
      request.body.\("tagId").asOpt[String].map {
        tagId =>
          Logger.debug(s"Removing $tagId from $id.")
          datasets.removeTag(id, UUID(tagId))
          datasets.index(id)
      }
      Ok(toJson(""))
  }

  /**
   * REST endpoint: POST: Add tags to a dataset.
   * Requires that the request body contains a "tags" field of List[String] type.
   */
  @ApiOperation(value = "Add tags to dataset",
      notes = "Requires that the request body contains a 'tags' field of List[String] type.",
      responseClass = "None", httpMethod = "POST")
  def addTags(id: UUID) = PermissionAction(Permission.AddTag, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
      addTagsHelper(TagCheck_Dataset, id, request)
  }

  /**
   * REST endpoint: POST: remove tags.
   * Requires that the request body contains a "tags" field of List[String] type.
   */
  @ApiOperation(value = "Remove tags of dataset",
      notes = "Requires that the request body contains a 'tags' field of List[String] type.",
      responseClass = "None", httpMethod = "POST")
  def removeTags(id: UUID) = PermissionAction(Permission.DeleteTag, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
      removeTagsHelper(TagCheck_Dataset, id, request)
  }
  				
  /*
 *  Helper function to handle adding and removing tags for files/datasets/sections.
 *  Input parameters:
 *      obj_type: one of the three TagCheckObjType's: TagCheck_File, TagCheck_Dataset or TagCheck_Section
 *      op_type:  one of the two strings "add", "remove"
 *      id:       the id in the original addTags call
 *      request:  the request in the original addTags call
 *  Return type:
 *      play.api.mvc.SimpleResult[JsValue]
 *      in the form of Ok, NotFound and BadRequest
 *      where: Ok contains the JsObject: "status" -> "success", the other two contain a JsString,
 *      which contains the cause of the error, such as "No 'tags' specified", and
 *      "The file with id 5272d0d7e4b0c4c9a43e81c8 is not found".
 */
  def addTagsHelper(obj_type: TagCheckObjType, id: UUID, request: UserRequest[JsValue]) = {
    val tagCheck = checkErrorsForTag(obj_type, id, request)

    val error_str = tagCheck.error_str
    val not_found = tagCheck.not_found
    val userOpt = tagCheck.userOpt
    val extractorOpt = tagCheck.extractorOpt
    val tags = tagCheck.tags

    // Now the real work: adding the tags.
    if ("" == error_str) {
      // Clean up leading, trailing and multiple contiguous white spaces.
      val tagsCleaned = tags.get.map(_.trim().replaceAll("\\s+", " "))
      (obj_type) match {
        case TagCheck_File => files.addTags(id, userOpt, extractorOpt, tagsCleaned)
        case TagCheck_Dataset => {
          datasets.addTags(id, userOpt, extractorOpt, tagsCleaned)
          datasets.get(id) match {
            case Some(dataset) => {
              events.addObjectEvent(request.user, id, dataset.name, "add_tags_dataset")
            }
          }
          datasets.index(id)
        }
        case TagCheck_Section => sections.addTags(id, userOpt, extractorOpt, tagsCleaned)
      }
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

  def removeTagsHelper(obj_type: TagCheckObjType, id: UUID, request: UserRequest[JsValue]) = {
    val tagCheck = checkErrorsForTag(obj_type, id, request)

    val error_str = tagCheck.error_str
    val not_found = tagCheck.not_found
    val userOpt = tagCheck.userOpt
    val extractorOpt = tagCheck.extractorOpt
    val tags = tagCheck.tags

    // Now the real work: removing the tags.
    if ("" == error_str) {
      // Clean up leading, trailing and multiple contiguous white spaces.
      val tagsCleaned = tags.get.map(_.trim().replaceAll("\\s+", " "))
      (obj_type) match {
        case TagCheck_File => files.removeTags(id, userOpt, extractorOpt, tagsCleaned)
        case TagCheck_Dataset => {
        	datasets.removeTags(id, userOpt, extractorOpt, tagsCleaned)
          datasets.get(id) match {
            case Some(dataset) => {
              events.addObjectEvent(request.user, id, dataset.name, "remove_tags_dataset")
            }
          }
        	datasets.index(id)

        }

        case TagCheck_Section => sections.removeTags(id, userOpt, extractorOpt, tagsCleaned)
      }
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

  // TODO: move helper methods to standalone service
  val USERID_ANONYMOUS = "anonymous"

  // Helper class and function to check for error conditions for tags.
  class TagCheck {
    var error_str: String = ""
    var not_found: Boolean = false
    var userOpt: Option[String] = None
    var extractorOpt: Option[String] = None
    var tags: Option[List[String]] = None
  }

  /*
  *  Helper function to check for error conditions.
  *  Input parameters:
  *      obj_type: one of the three TagCheckObjType's: TagCheck_File, TagCheck_Dataset or TagCheck_Section
  *      id:       the id in the original addTags call
  *      request:  the request in the original addTags call
  *  Returns:
  *      tagCheck: a TagCheck object, containing the error checking results:
  *
  *      If error_str == "", then no error is found;
  *      otherwise, it contains the cause of the error.
  *      not_found is one of the error conditions, meaning the object with
  *      the given id is not found in the DB.
  *      userOpt, extractorOpt and tags are set according to the request's content,
  *      and will remain None if they are not specified in the request.
  *      We change userOpt from its default None value, only if the userId
  *      is not USERID_ANONYMOUS.  The use case for this is the extractors
  *      posting to the REST API -- they'll use the commKey to post, and the original
  *      userId of these posts is USERID_ANONYMOUS -- in this case, we'd like to
  *      record the extractor_id, but omit the userId field, so we leave userOpt as None.
  */
  def checkErrorsForTag(obj_type: TagCheckObjType, id: UUID, request: UserRequest[JsValue]): TagCheck = {
    val userObj = request.user
    Logger.debug("checkErrorsForTag: user id: " + userObj.get.identityId.userId + ", user.firstName: " + userObj.get.firstName
      + ", user.LastName: " + userObj.get.lastName + ", user.fullName: " + userObj.get.fullName)
    val userId = userObj.get.identityId.userId
    if (USERID_ANONYMOUS == userId) {
      Logger.debug("checkErrorsForTag: The user id is \"anonymous\".")
    }

    var userOpt: Option[String] = None
    var extractorOpt: Option[String] = None
    var error_str = ""
    var not_found = false
    val tags = request.body.\("tags").asOpt[List[String]]

    if (tags.isEmpty) {
      error_str = "No \"tags\" specified, request.body: " + request.body.toString
    } else if (!UUID.isValid(id.stringify)) {
      error_str = "The given id " + id + " is not a valid ObjectId."
    } else {
      obj_type match {
        case TagCheck_File => not_found = files.get(id).isEmpty
        case TagCheck_Dataset => not_found = datasets.get(id).isEmpty
        case TagCheck_Section => not_found = sections.get(id).isEmpty
        case _ => error_str = "Only file/dataset/section is supported in checkErrorsForTag()."
      }
      if (not_found) {
        error_str = s"The $obj_type with id $id is not found"
      }
    }
    if ("" == error_str) {
      if (USERID_ANONYMOUS == userId) {
        val eid = request.body.\("extractor_id").asOpt[String]
        eid match {
          case Some(extractor_id) => extractorOpt = eid
          case None => error_str = "No \"extractor_id\" specified, request.body: " + request.body.toString
        }
      } else {
        userOpt = Option(userId)
      }
    }
    val tagCheck = new TagCheck
    tagCheck.error_str = error_str
    tagCheck.not_found = not_found
    tagCheck.userOpt = userOpt
    tagCheck.extractorOpt = extractorOpt
    tagCheck.tags = tags
    tagCheck
  }

  /**
   * REST endpoint: POST: remove all tags.
   */
  @ApiOperation(value = "Remove all tags of dataset",
      notes = "Forcefully remove all tags for this dataset.  It is mainly intended for testing.",
      responseClass = "None", httpMethod = "POST")
  def removeAllTags(id: UUID) = PermissionAction(Permission.DeleteTag, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
      Logger.info(s"Removing all tags for dataset with id: $id.")
      if (UUID.isValid(id.stringify)) {
        datasets.get(id) match {
          case Some(dataset) => {
            datasets.removeAllTags(id)
            datasets.index(id) 

            Ok(Json.obj("status" -> "success"))
          }
          case None => {
            val msg = s"The dataset with id $id is not found."
            Logger.error(msg)
            NotFound(toJson(msg))
          }
        }
      } else {
        val msg = s"The given id $id is not a valid ObjectId."
        Logger.error(msg)
        BadRequest(toJson(msg))
      }
  }

  // ---------- Tags related code ends ------------------

  @ApiOperation(value = "Add comment to dataset", notes = "", responseClass = "None", httpMethod = "POST")
  def comment(id: UUID) = PermissionAction(Permission.AddComment, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
      request.user match {
        case Some(identity) => {
          request.body.\("text").asOpt[String] match {
            case Some(text) => {
              val comment = new Comment(identity, text, dataset_id = Some(id))
              comments.insert(comment)
              datasets.index(id)
              datasets.get(id) match {
                case Some(dataset) => {
                  events.addSourceEvent(request.user, comment.id, comment.text , dataset.id, dataset.name, "add_comment_dataset")
                }
              }
              Ok(comment.id.toString())
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
  def searchDatasetsUserMetadata = PermissionAction(Permission.ViewDataset)(parse.json) { implicit request =>
      Logger.debug("Searching datasets' user metadata for search tree.")

      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: " + searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

      var searchQuery = datasets.searchUserMetadataFormulateQuery(searchTree)

      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning datasets list.")

      val list = for (dataset <- searchQuery) yield dataset
      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
  }

  /**
   * List datasets satisfying a general metadata search tree.
   */
  def searchDatasetsGeneralMetadata = PermissionAction(Permission.ViewDataset)(parse.json) { implicit request =>
      Logger.debug("Searching datasets' metadata for search tree.")

      var searchJSON = Json.stringify(request.body)
      Logger.debug("thejsson: " + searchJSON)
      var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

      var searchQuery = datasets.searchAllMetadataFormulateQuery(searchTree)

      //searchQuery = searchQuery.reverse

      Logger.debug("Search completed. Returning datasets list.")

      val list = for (dataset <- searchQuery) yield dataset
      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
  }

  /**
   * Return whether a dataset is currently being processed.
   */
  @ApiOperation(value = "Is being processed",
      notes = "Return whether a dataset is currently being processed by a preprocessor.",
      responseClass = "None", httpMethod = "GET")
  def isBeingProcessed(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
      datasets.get(id) match {
        case Some(dataset) => {
          val filesInDataset = dataset.files map {f => files.get(f.id).get}

          var isActivity = "false"
          try {
            for (f <- filesInDataset) {
              extractions.findIfBeingProcessed(f.id) match {
                case false =>
                case true => {
                  isActivity = "true"
                  throw ActivityFound
                }
              }
            }
          } catch {
            case ActivityFound =>
          }

          Ok(toJson(Map("isBeingProcessed" -> isActivity)))
        }
        case None => {
          Logger.error(s"Error getting dataset $id"); InternalServerError
        }
      }
  }

  // TODO make a case class to represent very long tuple below
  def jsonPreviewsFiles(filesList: List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]): JsValue = {
    val list = for (filePrevs <- filesList) yield jsonPreviews(filePrevs._1, filePrevs._2)
    toJson(list)
  }

  // TODO make a case class to represent very long tuple below
  def jsonPreviews(prvFile: models.File, prvs: Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]): JsValue = {
    val list = for (prv <- prvs) yield jsonPreview(prv._1, prv._2, prv._3, prv._4, prv._5, prv._6, prv._7)
    val listJson = toJson(list.toList)
    toJson(Map[String, JsValue]("file_id" -> JsString(prvFile.id.toString), "previews" -> listJson))
  }

  def jsonPreview(pvId: String, pId: String, pPath: String, pMain: String, pvRoute: String, pvContentType: String, pvLength: Long): JsValue = {
    if (pId.equals("X3d"))
      toJson(Map("pv_id" -> pvId, "p_id" -> pId,
                 "p_path" -> controllers.routes.Assets.at(pPath).toString,
                 "p_main" -> pMain, "pv_route" -> pvRoute,
                 "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString,
                 "pv_annotationsEditPath" -> api.routes.Previews.editAnnotation(UUID(pvId)).toString,
                 "pv_annotationsListPath" -> api.routes.Previews.listAnnotations(UUID(pvId)).toString,
                 "pv_annotationsAttachPath" -> api.routes.Previews.attachAnnotation(UUID(pvId)).toString))
    else
      toJson(Map("pv_id" -> pvId, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString, "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString))
  }

  @ApiOperation(value = "Get dataset previews",
      notes = "Return the currently existing previews of the selected dataset (full description, including paths to preview files, previewer names etc).",
      responseClass = "None", httpMethod = "GET")
  def getPreviews(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
      datasets.get(id) match {
        case Some(dataset) => {
          val innerFiles = dataset.files map {f => files.get(f.id).get}
          val datasetWithFiles = dataset.copy(files = innerFiles)
          val previewers = Previewers.findPreviewers
          //NOTE Should the following code be unified somewhere since it is duplicated in Datasets and Files for both api and controllers
          val previewslist = for (f <- datasetWithFiles.files; if (f.showPreviews.equals("DatasetLevel"))) yield {
            val pvf = for (p <- previewers; pv <- f.previews; if (p.contentType.contains(pv.contentType))) yield {
              (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
            }
            if (pvf.length > 0) {
              (f -> pvf)
            } else {
              val ff = for (p <- previewers; if (p.contentType.contains(f.contentType))) yield {
                //Change here. If the license allows the file to be downloaded by the current user, go ahead and use the 
                //file bytes as the preview, otherwise return the String null and handle it appropriately on the front end
                if (f.licenseData.isDownloadAllowed(request.user)) {
                    (f.id.toString, p.id, p.path, p.main, controllers.routes.Files.file(f.id) + "/blob", f.contentType, f.length)
                }
                else {
                    (f.id.toString, p.id, p.path, p.main, "null", f.contentType, f.length)
                }
              }
              (f -> ff)
            }
          }
          Ok(jsonPreviewsFiles(previewslist.asInstanceOf[List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]]))
        }
        case None => {
          Logger.error("Error getting dataset" + id); InternalServerError
        }
      }
  }

  //Detach and delete dataset code 
  /**
   * REST endpoint: DELETE: detach all files from a dataset and then delete the dataset
   * 
   *  Takes one arg, id:
   *  
   *  @param id, the UUID associated with the dataset to detach all files from and then delete.
   *  
   */
  @ApiOperation(value = "Detach and delete dataset", 
          notes = "Detaches all files before proceeding to perform the stanadard delete on the dataset.",
          responseClass = "None", httpMethod="DELETE")
  def detachAndDeleteDataset(id: UUID) = PermissionAction(Permission.DeleteDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
          datasets.get(id) match{
              case Some(dataset) => {                  
                  for (f <- dataset.files) {                      
                    detachFileHelper(dataset.id, f.id, dataset, request.user)
                  }
            	  deleteDatasetHelper(dataset.id, request)
            	  Ok(toJson(Map("status" -> "success")))
              }
              case None=> {
                  Ok(toJson(Map("status" -> "success")))
              }
          }          
  }
  
  /**
   * Utility function to consolidate the utility portions of the delete dataset functionality 
   * so that it can be easily called from multiple API operations.
   * 
   * @param id The id of the dataset that a file is being detached from
   * @param request The implicit request parameter which is part of the REST API call
   * 
   */
  def deleteDatasetHelper(id: UUID, request: UserRequest[AnyContent]) = {
      datasets.get(id) match {
        case Some(dataset) => {
          //remove dataset from RDF triple store if triple store is used
          configuration.getString("userdfSPARQLStore").getOrElse("no") match {
            case "yes" => rdfsparql.removeDatasetFromGraphs(id)
            case _ => Logger.debug("userdfSPARQLStore not enabled")
          }
          events.addObjectEvent(request.user, dataset.id, dataset.name, "delete_dataset")
          datasets.removeDataset(id)

          current.plugin[ElasticsearchPlugin].foreach {
        	  _.delete("data", "dataset", id.stringify)
          }
          
          for(file <- dataset.files)
        	  files.index(file.id)
                    
          current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification(Utils.baseUrl(request), "Dataset","removed",dataset.id.stringify, dataset.name)}
          Ok(toJson(Map("status"->"success")))
        }
        case None => Ok(toJson(Map("status" -> "success")))
     }
  }
  
  @ApiOperation(value = "Delete dataset",
      notes = "Cascading action (deletes all previews and metadata of the dataset and all files existing only in the deleted dataset).",
      responseClass = "None", httpMethod = "POST")
  def deleteDataset(id: UUID) = PermissionAction(Permission.DeleteDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
        deleteDatasetHelper(id, request)
  }

  @ApiOperation(value = "Get the user-generated metadata of the selected dataset in an RDF file",
      notes = "",
      responseClass = "None", httpMethod = "GET")
  def getRDFUserMetadata(id: UUID, mappingNumber: String="1") = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    current.plugin[RDFExportService].isDefined match{
      case true => {
        current.plugin[RDFExportService].get.getRDFUserMetadataDataset(id.toString, mappingNumber) match{
          case Some(resultFile) =>{
            Ok.chunked(Enumerator.fromStream(new FileInputStream(resultFile)))
			            	.withHeaders(CONTENT_TYPE -> "application/rdf+xml")
			            	.withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + resultFile.getName()))
          }
          case None => BadRequest(toJson("Dataset not found " + id))
        }
      }
      case _ => Ok("RDF export plugin not enabled")
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
    while (currStart != -1) {
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1, currStart)
      currEnd = xml.indexOf(">", currStart + 1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart, currEnd + 1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd + 1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1)

    val xmlFile = java.io.File.createTempFile("xml", ".xml")
    val fileWriter = new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()

    return xmlFile
  }
  
  @ApiOperation(value = "Get URLs of dataset's RDF metadata exports",
      notes = "URLs of metadata exported as RDF from XML files contained in the dataset, as well as the URL used to export the dataset's user-generated metadata as RDF.",
      responseClass = "None", httpMethod = "GET")
  def getRDFURLsForDataset(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    current.plugin[RDFExportService].isDefined match{
      case true =>{
	    current.plugin[RDFExportService].get.getRDFURLsForDataset(id.toString)  match {
	      case Some(listJson) => {
	        Ok(listJson) 
	      }
	      case None => Logger.error(s"Error getting dataset $id"); InternalServerError
	    }
      }
      case false => {
        Ok("RDF export plugin not enabled")
      }
    }
  }

  @ApiOperation(value = "Get technical metadata of the dataset",
      notes = "",
      responseClass = "None", httpMethod = "GET")
  def getTechnicalMetadataJSON(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
      datasets.get(id) match {
        case Some(dataset) => Ok(datasets.getTechnicalMetadataJSON(id))
        case None => Logger.error("Error finding dataset" + id); InternalServerError
      }
  }

  
  def getXMLMetadataJSON(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id)  match {
      case Some(dataset) => {
        Ok(datasets.getXMLMetadataJSON(id))
      }
      case None => {Logger.error("Error finding dataset" + id); InternalServerError}      
    }
  }

  def getUserMetadataJSON(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id)  match {
      case Some(dataset) => {
        Ok(datasets.getUserMetadataJSON(id))
      }
      case None => {
        Logger.error("Error finding dataset" + id);
        InternalServerError
      }      

    }
  }
  
  def setNotesHTML(id: UUID) = PermissionAction(Permission.CreateNote, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
	  request.user match {
	    case Some(identity) => {
		    request.body.\("notesHTML").asOpt[String] match {
			    case Some(html) => {
			        datasets.setNotesHTML(id, html)
			        //index(id)
			        Ok(toJson(Map("status"->"success")))
			    }
			    case None => {
			    	Logger.error("no html specified.")
			    	BadRequest(toJson("no html specified."))
			    }
		    }
	    }
	    case None => {
	      Logger.error(("No user identity found in the request, request body: " + request.body))
	      BadRequest(toJson("No user identity found in the request, request body: " + request.body))
	    }
    }
  }
  
  def dumpDatasetGroupings = ServerAdminAction { request =>
  
	  val unsuccessfulDumps = datasets.dumpAllDatasetGroupings
	  if(unsuccessfulDumps.size == 0)
	    Ok("Dumping of dataset file groupings was successful for all datasets.")
	  else{
	    var unsuccessfulMessage = "Dumping of dataset file groupings was successful for all datasets except dataset(s) with id(s) "
	    for(badDataset <- unsuccessfulDumps){
	      unsuccessfulMessage = unsuccessfulMessage + badDataset + ", "
	    }
	    unsuccessfulMessage = unsuccessfulMessage.substring(0, unsuccessfulMessage.length()-2) + "."
	    Ok(unsuccessfulMessage)  
	  }      
	}

  def dumpDatasetsMetadata = ServerAdminAction { request =>
  
	  val unsuccessfulDumps = datasets.dumpAllDatasetMetadata
	  if(unsuccessfulDumps.size == 0)
	    Ok("Dumping of datasets metadata was successful for all datasets.")
	  else{
	    var unsuccessfulMessage = "Dumping of datasets metadata was successful for all datasets except dataset(s) with id(s) "
	    for(badDataset <- unsuccessfulDumps){
	      unsuccessfulMessage = unsuccessfulMessage + badDataset + ", "
	    }
	    unsuccessfulMessage = unsuccessfulMessage.substring(0, unsuccessfulMessage.length()-2) + "."
	    Ok(unsuccessfulMessage)  
	  }      
	}

  @ApiOperation(value = "Follow dataset.",
    notes = "Add user to dataset followers and add dataset to user followed datasets.",
    responseClass = "None", httpMethod = "POST")
  def follow(id: UUID, name: String) = AuthenticatedAction {
    request =>
      val user = request.user

      user match {
        case Some(loggedInUser) => {
          datasets.get(id) match {
            case Some(dataset) => {
              events.addObjectEvent(user, id, name, "follow_dataset")
              datasets.addFollower(id, loggedInUser.id)
              userService.followDataset(loggedInUser.id, id)

              val recommendations = getTopRecommendations(id, loggedInUser)
              recommendations match {
                case x::xs => Ok(Json.obj("status" -> "success", "recommendations" -> recommendations))
                case Nil => Ok(Json.obj("status" -> "success"))
              }
            }
            case None => {
              NotFound
            }
          }
        }
        case None => {
          Unauthorized
        }
      }
  }

  @ApiOperation(value = "Unfollow dataset.",
    notes = "Remove user from dataset followers and remove dataset from user followed datasets.",
    responseClass = "None", httpMethod = "POST")
  def unfollow(id: UUID, name: String) = AuthenticatedAction { implicit request =>
      implicit val user = request.user

      user match {
        case Some(loggedInUser) => {
          datasets.get(id) match {
            case Some(dataset) => {
              events.addObjectEvent(user, id, name, "unfollow_dataset")
              datasets.removeFollower(id, loggedInUser.id)
              userService.unfollowDataset(loggedInUser.id, id)
              Ok
            }
            case None => {
              NotFound
            }
          }
        }
        case None => {
          Unauthorized
        }
      }
  }

  def getTopRecommendations(followeeUUID: UUID, follower: User): List[MiniEntity] = {
    val followeeModel = datasets.get(followeeUUID)
    followeeModel match {
      case Some(followeeModel) => {
        val sourceFollowerIDs = followeeModel.followers
        val excludeIDs = follower.followedEntities.map(typedId => typedId.id) ::: List(followeeUUID, follower.id)
        val num = play.api.Play.configuration.getInt("number_of_recommendations").getOrElse(10)
        userService.getTopRecommendations(sourceFollowerIDs, excludeIDs, num)
      }
      case None => {
        List.empty
      }
    }
  }
}

object ActivityFound extends Exception {}
