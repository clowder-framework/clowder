package api

import java.net.URL
import java.util.Date
import javax.inject.{Inject, Singleton}

import jsonutils.JsonUtil
import models.{UserAgent, UUID, ResourceRef}
import play.api.Logger
import play.api.libs.json._
import play.api.libs.json.Json._
import models._
import services.{UserService, ContextLDService, MetadataService}
import play.api.Play.configuration

/**
 * Manipulate generic metadata.
 */
@Singleton
class Metadata @Inject()(metadataService: MetadataService, contextService: ContextLDService, userService: UserService) extends ApiController {
  
  def search() = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) { request =>
    Logger.debug("Searching metadata")
    val results = metadataService.search(request.body)
    Ok(toJson(results))
  }
  
  def getUserMetadata() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.AddMetadata)) {
    implicit request =>
      request.user match {
        case Some(user) => Ok(JsObject(Seq("status" -> JsString(user.identityId.userId))))
        case None => BadRequest(toJson("Invalid user"))
      }
  }

  def addUserMetadata() = SecuredAction(authorization = WithPermission(Permission.AddMetadata)) {
    implicit request =>
      request.user match {
        case Some(user) => {
          val json = request.body
          // when the new metadata is added
          val createdAt = new Date()

          // build creator uri
          // TODO switch to internal id and then build url when returning?
          val userURI = controllers.routes.Application.index().absoluteURL() + "api/users/" + user.id
          val creator = UserAgent(user.id, "cat:user", MiniUser(user.id, user.fullName, user.avatarUrl.get, user.email), Some(new URL(userURI)))

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
}
