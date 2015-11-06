package api

import javax.inject.{Singleton, Inject}

import models._
import play.api.Logger
import play.api.libs.json._
import play.api.libs.json.Json._
import services.{RelationService}

/**
 * Track relations between resources.
 */
@Singleton
class Relations @Inject()(relations: RelationService) extends ApiController {

  import models.EnumUtils.enumWrites
  implicit val myEnumReads: Reads[ResourceType.Value] = EnumUtils.enumReads(ResourceType)
  implicit val myEnumWrites: Writes[ResourceType.Value] = EnumUtils.enumWrites
  implicit val nodeReads = Json.format[Node]
  implicit val relationsReads = Json.format[Relation]

  def list() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.RelationsRead)) { implicit request =>
    Ok(toJson(relations.list()))
  }

  def get(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.RelationsRead)) { implicit request =>
    relations.get(id) match {
      case Some(r) => Ok(toJson(r))
      case None => BadRequest(Json.obj("status" ->"KO", "message" -> "resource does not exist"))
    }
  }

  def add() = SecuredAction(parse.json, authorization = WithPermission(Permission.RelationsWrite)) {
    implicit request =>
      // TODO get it to work with implicit formats
      var sourceId= (request.body \ "source" \ "id").as[String]
      var sourceType= ResourceType.withName((request.body \ "source" \ "resourceType").as[String])
      var targetId= (request.body \ "target" \ "id").as[String]
      var targetType= ResourceType.withName((request.body \ "target" \ "resourceType").as[String])
      var res = relations.add(Relation(source = Node(sourceId, sourceType), target = Node(targetId, targetType)))

      res match {
        case Some(id) => Ok(Json.obj("status" ->"OK", "message" -> ("Relation '" + id + "' saved."), "id" -> id ))
        case None => Ok(Json.obj("status" ->"OK", "message" -> ("Relation already exists") ))
      }
  }

  def delete(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.RelationsWrite)) { implicit request =>
    relations.delete(id)
    Ok(Json.obj("status" -> "OK"))
  }

  def findTargets(sourceId: String, sourceType: String, targetType: String) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.RelationsRead)) { implicit request =>
    Ok(toJson(relations.findTargets(sourceId, ResourceType.withName(sourceType), ResourceType.withName(targetType))))
  }
}
