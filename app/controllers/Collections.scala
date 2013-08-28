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
import services.Services
import models.Dataset
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import com.mongodb.casbah.commons.MongoDBObject

object Collections  extends Controller with SecuredController {

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
   
   def newCollection()  = SecuredAction(parse.anyContent, allowKey=false, authorization=WithPermission(Permission.CreateCollections)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.newCollection(collectionForm))
  }
  
  /**
   * List collections.
   */
  def list(when: String, date: String, limit: Int) = SecuredAction(parse.anyContent, allowKey=false, authorization=WithPermission(Permission.ListCollections)) { implicit request =>
    implicit val user = request.user
    var direction = "b"
    if (when != "") direction = when
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    var prev, next = ""
    var collections = List.empty[models.Collection]
    if (direction == "b") {
	    collections = Services.collections.listCollectionsBefore(date, limit)
    } else if (direction == "a") {
    	collections = Services.collections.listCollectionsAfter(date, limit)
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
    	firstPage = collections.exists(_.id == latest(0).id)
    	lastPage = collections.exists(_.id == first(0).id)
    	Logger.debug("latest " + latest(0).id + " first page " + firstPage )
    	Logger.debug("first " + first(0).id + " last page " + lastPage )
    }
    if (collections.size > 0) {  
      if (date != "" && !firstPage) { // show prev button
    	prev = formatter.format(collections.head.created)
      }
      if (!lastPage) { // show next button
    	next = formatter.format(collections.last.created)
      }
    }
    Ok(views.html.collectionList(collections, prev, next, limit))
  }
  
  
  
  
   /**
   * Create collection.
   */
  def submit() = SecuredAction(parse.anyContent, allowKey=false, authorization=WithPermission(Permission.CreateCollections)) { implicit request =>
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
			      } 
	)
  }
  
   /**
   * Collection.
   */
  def collection(id: String) = SecuredAction(parse.anyContent, allowKey=false, authorization=WithPermission(Permission.ShowCollection)) { implicit request =>
    
  	implicit val user = request.user
  	Services.collections.get(id)  match {
  	  case Some(collection) => {
  	    Ok(views.html.collectionofdatasets(collection))
  	  }
  	  case None => {Logger.error("Error getting collection " + id); InternalServerError}
  	}
  }
  
  
  
}