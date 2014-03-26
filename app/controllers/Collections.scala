package controllers

import play.api.data.Form
import play.api.data.Forms._
import models.{UUID, Collection}
import java.util.Date
import play.api.Logger
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import api.WithPermission
import api.Permission
import javax.inject.{Singleton, Inject}
import services.{DatasetService, CollectionService}

object ThumbnailFound extends Exception {}

@Singleton
class Collections @Inject()(datasets: DatasetService, collections: CollectionService) extends SecuredController {

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

  def newCollection() = SecuredAction(authorization = WithPermission(Permission.CreateCollections)) {
    implicit request =>
      implicit val user = request.user
      Ok(views.html.newCollection(collectionForm))
  }

  /**
   * List collections.
   */
  def list(when: String, date: String, limit: Int) = SecuredAction(authorization = WithPermission(Permission.ListCollections)) {
    implicit request =>
      implicit val user = request.user
      var direction = "b"
      if (when != "") direction = when
      val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
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

      Ok(views.html.collectionList(collectionsWithThumbnails, prev, next, limit))
  }

  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description, "created" -> collection.created.toString))
  }

  /**
   * Create collection.
   */
  def submit() = SecuredAction(authorization = WithPermission(Permission.CreateCollections)) {
    implicit request =>
      implicit val user = request.user

      collectionForm.bindFromRequest.fold(
        errors => BadRequest(views.html.newCollection(errors)),
        collection => {
          Logger.debug("Saving dataset " + collection.name)
          collections.insert(collection)
          Redirect(routes.Collections.collection(collection.id))
        })
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
          Ok(views.html.collectionofdatasets(collection.datasets, collection.name, collection.id.toString()))
        }
        case None => {
          Logger.error("Error getting collection " + id); BadRequest("Collection not found")
        }
      }
  }
}