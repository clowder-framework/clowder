package controllers

import play.api.mvc.Controller
import play.api.data.Form
import play.api.data.Forms._
import models.Collection
import java.util.Date
import play.api.mvc.Flash
import play.api.Logger
import play.api.Play.current
import services.ElasticsearchPlugin
import models.Dataset
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import com.mongodb.casbah.commons.MongoDBObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import api.WithPermission
import api.Permission
import javax.inject.{ Singleton, Inject }
import services.{ DatasetService, CollectionService }
import services.AdminsNotifierPlugin

object ThumbnailFound extends Exception { }

@Singleton
class Collections @Inject() (datasets: DatasetService, collections: CollectionService) extends SecuredController {

  /**
   * New dataset form.
   */
  val collectionForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText
    )
    ((name, description) => Collection(name = name, description = description, created = new Date))
    ((collection: Collection) => Some((collection.name, collection.description)))
   )
   
   def newCollection()  = SecuredAction(authorization=WithPermission(Permission.CreateCollections)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.newCollection(collectionForm))
  }
  
  /**
   * List collections.
   */
  def list(when: String, date: String, limit: Int) = SecuredAction(authorization=WithPermission(Permission.ListCollections)) { implicit request =>
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
    val latest = Collection.find(MongoDBObject()).sort(MongoDBObject("created" -> -1)).limit(1).toList
    // first object
    val first = Collection.find(MongoDBObject()).sort(MongoDBObject("created" -> 1)).limit(1).toList
    var firstPage = false
    var lastPage = false
    if (latest.size == 1) {
    	firstPage = collectionList.exists(_.id == latest(0).id)
    	lastPage = collectionList.exists(_.id == first(0).id)
    	Logger.debug("latest " + latest(0).id + " first page " + firstPage )
    	Logger.debug("first " + first(0).id + " last page " + lastPage )
    }
    if (collectionList.size > 0) {  
      if (date != "" && !firstPage) { // show prev button
    	prev = formatter.format(collectionList.head.created)
      }
      if (!lastPage) { // show next button
    	next = formatter.format(collectionList.last.created)
      }
    }
    
    var collectionsWithThumbnails = List.empty[models.Collection]
    for(collection <- collectionList){
      var collectionThumbnail:Option[String] = None
      try{
	        for(dataset <- collection.datasets){
	          if(!dataset.thumbnail_id.isEmpty){
	            collectionThumbnail = dataset.thumbnail_id
	            throw ThumbnailFound		
	          }
	        }
        }catch {
        	case ThumbnailFound =>
        }
      val collectionWithThumbnail = collection.copy(thumbnail_id = collectionThumbnail)
      collectionsWithThumbnails = collectionWithThumbnail +: collectionsWithThumbnails       
    }
    collectionsWithThumbnails = collectionsWithThumbnails.reverse
    
    Ok(views.html.collectionList(collectionsWithThumbnails, prev, next, limit))
  }
  
  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description, "created" -> collection.created.toString))
  }
 
   /**
   * Create collection.
   */
  def submit() = SecuredAction(authorization=WithPermission(Permission.CreateCollections)) { implicit request =>
    implicit val user = request.user
    
        collectionForm.bindFromRequest.fold(
          errors => BadRequest(views.html.newCollection(errors)),
	      collection => {
		        Logger.debug("Saving dataset " + collection.name)
		        		     
			        // TODO create a service instead of calling salat directly
		            Collection.save(collection)
		            
		            // index collection
//		            current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", id, 
//		                List(("name",dt.name), ("description", dt.description)))}

		            // redirect to collection page
		            Redirect(routes.Collections.collection(collection.id.toString))
		            current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("Collection","added",collection.id.toString,collection.name)}
		            Redirect(routes.Collections.collection(collection.id.toString))
			      } 
	)
  }
  
   /**
   * Collection.
   */
  def collection(id: String) = SecuredAction(authorization=WithPermission(Permission.ShowCollection)) { implicit request =>
    
  	implicit val user = request.user
  	collections.get(id)  match {
  	  case Some(collection) => {
  	    Ok(views.html.collectionofdatasets(datasets.listInsideCollection(id),collection.name, collection.id.toString()))
  	  }
  	  case None => {Logger.error("Error getting collection " + id); InternalServerError}
  	}
  }
  
  
  
}