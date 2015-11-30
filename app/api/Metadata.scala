package api

import java.net.URL
import java.util.Date
import javax.inject.{Inject, Singleton}

import models.{ResourceRef, UUID, UserAgent, _}
import play.api.Logger
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.mvc.Action
import services._

import scala.concurrent.Future

/**
 * Manipulate generic metadata.
 */
@Singleton
class Metadata @Inject()(
  metadataService: MetadataService,
  contextService: ContextLDService,
  userService: UserService,
  datasets: DatasetService,
  files: FileService) extends ApiController {
  
  def search() = PermissionAction(Permission.ViewDataset)(parse.json) { implicit request =>
    Logger.debug("Searching metadata")
    val results = metadataService.search(request.body)
    Ok(toJson(results))
  }

  def searchByKeyValue(key: Option[String], value: Option[String], count: Int = 0) = PermissionAction(Permission.ViewDataset) {
      implicit request =>
        val response = for {
          k <- key
          v <- value
        } yield {
          val results = metadataService.search(k, v, count)
          val datasetsResults = results.flatMap { d =>
            if (d.resourceType == ResourceRef.dataset) datasets.get(d.id) else None
          }
          val filesResults = results.flatMap { f =>
            if (f.resourceType == ResourceRef.file) files.get(f.id) else None
          }
          import Dataset.datasetWrites
          import File.FileWrites
          // Use "distinct" to remove duplicate results.
          Ok(JsObject(Seq("datasets" -> toJson(datasetsResults.distinct), "files" -> toJson(filesResults.distinct))))
        }
        response getOrElse BadRequest(toJson("You must specify key and value"))
  }

  def getDefinitions() = PermissionAction(Permission.ViewDataset) {
    implicit request =>
      val vocabularies = metadataService.getDefinitions()
      Ok(toJson(vocabularies))
  }

  def getDefinition(id: UUID) = PermissionAction(Permission.AddMetadata).async { implicit request =>
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val foo = for {
      md <- metadataService.getDefinition(id)
      url <- (md.json \ "definitions_url").asOpt[String]
    } yield {
      WS.url(url).get().map(response => Ok(response.body.trim))
    }
    foo.getOrElse {
      Future(InternalServerError)
    }
  }

  def getUrl(inUrl: String) = PermissionAction(Permission.AddMetadata).async { implicit request =>
    // Use java.net.URI instead of URLDecoder.decode to decode the path.
    import java.net.URI
    Logger.debug("Metadata getUrl: inUrl = '" + inUrl + "'.")
    // Replace " " with "+", otherwise the decoded URL might contain spaces and break Ws.url(url).
    val url = new URI(inUrl).getPath().replaceAll(" ", "+")
    Logger.debug("Metadata getUrl decoded: url = '" + url + "'.")
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    WS.url(url).get().map {
      response => Ok(response.body.trim)
    }
  }

  def addDefinition() = PermissionAction(Permission.AddMetadata)(parse.json) {
    implicit request =>
      request.user match {
        case Some(user) => {
          val body = request.body

          if ((body \ "label").asOpt[String].isDefined && (body \ "type").asOpt[String].isDefined && (body \ "uri").asOpt[String].isDefined) {
            val definition = MetadataDefinition(json = body)
            metadataService.addDefinition(definition)
            Ok(JsObject(Seq("status" -> JsString("ok"))))
          } else {
            BadRequest(toJson("Invalid resource type"))
          }

        }
        case None => BadRequest(toJson("Invalid user"))
      }
  }

  def addUserMetadata() = PermissionAction(Permission.AddMetadata)(parse.json) {
    implicit request =>
      request.user match {
        case Some(user) => {
          val json = request.body
          // when the new metadata is added
          val createdAt = new Date()

          // build creator uri
          // TODO switch to internal id and then build url when returning?
          val userURI = controllers.routes.Application.index().absoluteURL() + "api/users/" + user.id
          val creator = UserAgent(user.id, "cat:user", MiniUser(user.id, user.fullName, user.avatarUrl.getOrElse(""), user.email), Some(new URL(userURI)))

          val context: JsValue = (json \ "@context")

          // figure out what resource this is attached to
          val attachedTo =
            if ((json \ "file_id").asOpt[String].isDefined)
              Some(ResourceRef(ResourceRef.file, UUID((json \ "file_id").as[String])))
            else if ((json \ "dataset_id").asOpt[String].isDefined)
              Some(ResourceRef(ResourceRef.dataset, UUID((json \ "dataset_id").as[String])))
            else None

          // check if the context is a URL to external endpoint
          val contextURL: Option[URL] = context.asOpt[String].map(new URL(_))

          // check if context is a JSON-LD document
          val contextID: Option[UUID] =
            if (context.isInstanceOf[JsObject]) {
              context.asOpt[JsObject].map(contextService.addContext(new JsString("context name"), _))
            } else if (context.isInstanceOf[JsArray]) {
              context.asOpt[JsArray].map(contextService.addContext(new JsString("context name"), _))
            } else None

          //parse the rest of the request to create a new models.Metadata object
          val content = (json \ "content")
          val version = None

          if (attachedTo.isDefined) {
            val metadata = models.Metadata(UUID.generate, attachedTo.get, contextID, contextURL, createdAt, creator,
              content, version)

            //add metadata to mongo
            metadataService.addMetadata(metadata)

            Ok(JsObject(Seq("status" -> JsString("ok"))))
          } else {
            BadRequest(toJson("Invalid resource type"))
          }
        }
        case None => BadRequest(toJson("Invalid user"))
      }
  }

  def removeMetadata(id:UUID) = PermissionAction(Permission.DeleteMetadata, Some(ResourceRef(ResourceRef.metadata, id))) { implicit request =>
    metadataService.getMetadataById(id) match{
      case Some(m) => {
        metadataService.removeMetadata(id)
        Ok(JsObject(Seq("status" -> JsString("ok"))))
      }
      case None => BadRequest(toJson("Invalid Metadata"))
    }
  }
}
