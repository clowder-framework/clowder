package controllers

import play.api.data.Form
import play.api.data.Forms._
import models.{UUID, Collection}
import util.RequiredFieldsConfig
import java.util.Date
import play.api.Logger
import play.api.Play.current
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import api.WithPermission
import api.Permission
import javax.inject.{ Singleton, Inject }
import scala.collection.mutable.ListBuffer
import services.{ DatasetService, CollectionService }
import services._
import org.apache.commons.lang.StringEscapeUtils


object ThumbnailFound extends Exception {}

@Singleton
class Collections @Inject()(datasets: DatasetService, collections: CollectionService, previewsService: PreviewService) extends SecuredController {  

  def newCollection() = SecuredAction(authorization = WithPermission(Permission.CreateCollections)) {
    implicit request =>
      implicit val user = request.user
      Ok(views.html.newCollection(null, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired))
  }
  
  /**
   * List collections.
   */	
  def list(when: String, date: String, limit: Int, mode: String) = SecuredAction(authorization = WithPermission(Permission.ListCollections)) {
    implicit request =>
      implicit val user = request.user
      var direction = "b"
      if (when != "") direction = when
      val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
      var prev, next = ""
      var collectionList = List.empty[models.Collection]
      if (direction == "b") {
        collectionList = collections.listCollectionsBefore(date, limit)
      } else if (direction == "a") {
        collectionList = collections.listCollectionsAfter(date, limit)
      } else {
        badRequest
      }

      // latest object
      val latest = collections.latest()
      // first object
      val first = collections.first()
      var firstPage = false
      var lastPage = false
      if (latest.size == 1) {
        firstPage = collectionList.exists(_.id.equals(latest.get.id))
        lastPage = collectionList.exists(_.id.equals(first.get.id))
        Logger.debug("latest " + latest.get.id + " first page " + firstPage)
        Logger.debug("first " + first.get.id + " last page " + lastPage)
      }
      if (collectionList.size > 0) {
        if (date != "" && !firstPage) {
          // show prev button
          prev = formatter.format(collectionList.head.created)
        }
        if (!lastPage) {
          // show next button
          next = formatter.format(collectionList.last.created)
        }
      }

      var collectionsWithThumbnails = List.empty[models.Collection]
      for (collection <- collectionList) {
        var collectionThumbnail: Option[String] = None
        try {
          for (dataset <- collection.datasets) {
            if (!dataset.thumbnail_id.isEmpty) {
              collectionThumbnail = dataset.thumbnail_id
              throw ThumbnailFound
            }
          }
        } catch {
          case ThumbnailFound =>
        }
        val collectionWithThumbnail = collection.copy(thumbnail_id = collectionThumbnail)
        collectionsWithThumbnails = collectionWithThumbnail +: collectionsWithThumbnails
      }
      collectionsWithThumbnails = collectionsWithThumbnails.reverse
      
      //Modifications to decode HTML entities that were stored in an encoded fashion as part 
      //of the collection's names or descriptions
      var decodedCollections = new ListBuffer[models.Collection]()
      for (aCollection <- collectionsWithThumbnails) {
          val dCollection = Utils.decodeCollectionElements(aCollection)
          decodedCollections += dCollection
      }
      
    //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
    //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar 
    //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14   
    val viewMode: Option[String] = 
        if (mode == null || mode == "") {
          request.cookies.get("view-mode") match {
              case Some(cookie) => Some(cookie.value)
              case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
          }
        } else {
            Some(mode)
        }    
    
      //Pass the viewMode into the view
      Ok(views.html.collectionList(decodedCollections.toList, prev, next, limit, viewMode))
  }

  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description, "created" -> collection.created.toString))
  }
  
  /**
   * Controller flow to create a new collection. Takes two parameters, name, a String, and description, a String. On success,
   * the browser is redirected to the new collection's page. On error, it is redirected back to the dataset creation
   * page with the appropriate error to be displayed.
   *  
   */
  def submit() = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateCollections)) {
    implicit request =>
        Logger.debug("------- in Collections.submit ---------")
        var colName = request.body.asFormUrlEncoded.getOrElse("name", null)
        var colDesc = request.body.asFormUrlEncoded.getOrElse("description", null)
        
      implicit val user = request.user
      user match {
	      case Some(identity) => {	      	            
                if (colName == null || colDesc == null) {
                    //This case shouldn't happen as it is validated on the client. 
                    BadRequest(views.html.newCollection("Name or Description was missing during collection creation.", RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired))
                }
	            
	            var collection = Collection(name = colName(0), description = colDesc(0), created = new Date, author = null)
	           	       
	            Logger.debug("Saving collection " + collection.name)
	            collections.insert(Collection(id = collection.id, name = collection.name, description = collection.description, created = collection.created, author = Some(identity)))
	          
	            //index collection
                val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
                current.plugin[ElasticsearchPlugin].foreach{_.index("data", "collection", collection.id, 
                List(("name",collection.name), ("description", collection.description), ("created",dateFormat.format(new Date()))))}
                
	            // redirect to collection page
	            current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification(Utils.baseUrl(request), "Collection","added",collection.id.toString,collection.name)}
	            Redirect(routes.Collections.collection(collection.id))	        
	      }
	      case None => Redirect(routes.Collections.list()).flashing("error" -> "You are not authorized to create new collections.")
      }
  }

  /**
   * Collection.
   */
  def collection(id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowCollection)) {
    implicit request =>
      Logger.debug(s"Showing collection $id")
      implicit val user = request.user
      collections.get(id) match {
        case Some(collection) => { 
          Logger.debug(s"Found collection $id")
          // only show previewers that have a matching preview object associated with collection
          Logger.debug("Num previewers " + Previewers.findCollectionPreviewers.size)
          
          //Decode the encoded items
          val dCollection = Utils.decodeCollectionElements(collection)
          
          for (p <- Previewers.findCollectionPreviewers) Logger.debug("Previewer " + p)
          val filteredPreviewers = for (
            previewer <- Previewers.findCollectionPreviewers;
            preview <- previewsService.findByCollectionId(id);
            if (previewer.collection);
            if (previewer.supportedPreviews.contains(preview.preview_type.get))
          ) yield {
            previewer
          }
          Logger.debug("Num previewers " + filteredPreviewers.size)
          filteredPreviewers.map(p => Logger.debug(s"Filtered previewers for collection $id $p.id"))
          
          //Decode the datasets so that their free text will display correctly in the view
          val datasetsInside = datasets.listInsideCollection(id)
          var decodedDatasetsInside = new ListBuffer[models.Dataset]()
          for (aDataset <- datasetsInside) {
              val dDataset = Utils.decodeDatasetElements(aDataset)
              decodedDatasetsInside += dDataset
          }
          
          Ok(views.html.collectionofdatasets(decodedDatasetsInside.toList, dCollection, filteredPreviewers.toList))
        }
        case None => {
          Logger.error("Error getting collection " + id); BadRequest("Collection not found")
        }
      }
  }

  def previews(collection_id: UUID) = SecuredAction(authorization = WithPermission(Permission.EditCollection)) {
    implicit request =>
      collections.get(collection_id) match {
        case Some(collection) => {
          val previewsByCol = previewsService.findByCollectionId(collection_id)
          Ok(views.html.collectionPreviews(collection_id.toString, previewsByCol, Previewers.findCollectionPreviewers))
        }
        case None => {
          Logger.error("Error getting collection " + collection_id);
          BadRequest("Collection not found")
        }
      }
  }
}

