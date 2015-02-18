package controllers

import javax.inject.Inject

import api.{Permission, WithPermission}
import models.{Dataset, Collection, ProjectSpace, UUID}
import play.api.Logger
import play.api.data.{Forms, Form}
import play.api.data.Forms._
import java.util.Date
import services.{UserService, SpaceService}
import util.Direction._

/**
 * Spaces allow users to partition the data into realms only accessible to users with the right permissions.
 *
 * @author Luigi Marini
 * @author Rob Kooper
 *
 */
class Spaces @Inject()(spaces: SpaceService, users: UserService) extends SecuredController {

  /**
   * New project space form.
   */
  val spaceForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText,
      "logoUrl" -> optional(Utils.CustomMappings.urlType),
      "bannerUrl" -> optional(Utils.CustomMappings.urlType),
      "homePages" -> Forms.list(Utils.CustomMappings.urlType),
      "buttonValue" -> text
    )
      (
          (name, description, logoUrl, bannerUrl, homePages, _) => ProjectSpace(name = name, description = description,
            created = new Date, creator = UUID.generate(), homePage = homePages, logoURL = logoUrl, bannerURL = bannerUrl,
            usersByRole= Map.empty, collectionCount=0, datasetCount=0, userCount=0, metadata=List.empty)
        )
      (
          (space: ProjectSpace) => Some((space.name, space.description, space.logoURL, space.bannerURL, space.homePage, "create"))
        )
  )

  /**
   * Space main page.
   */
  def getSpace(id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowSpace)) { implicit request =>
    implicit val user = request.user
    spaces.get(id) match {
      case Some(s) => Ok(views.html.spaces.space(s, List.empty[Collection], List.empty[Dataset]))
      case None => InternalServerError("Space not found")
    }
  }

  def newSpace() = SecuredAction(authorization = WithPermission(Permission.CreateSpaces)) {
    implicit request =>
      implicit val user = request.user
    Ok(views.html.spaces.newSpace(spaceForm))
  }

  def updateSpace(id:UUID) = SecuredAction(authorization = WithPermission(Permission.EditSpace)) {
    implicit request =>
      implicit val user = request.user
      spaces.get(id) match {
        case Some(s) => {Ok(views.html.spaces.editSpace(spaceForm.fill(s)))}
        case None => InternalServerError("Space not found")
      }
    }
  /**
   * Create collection.
   */
  def submit() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.CreateSpaces )) {
    implicit request =>
      request.body.asMultipartFormData.get.dataParts.get("buttonValue").headOption match {
        case Some(x) => {
          implicit val user = request.user
          user match {
            case Some(identity) => {
              val userId = request.mediciUser.fold(UUID.generate)(_.id)
              x(0) match {
                case ("Create") => {
                  spaceForm.bindFromRequest.fold(
                    errors => BadRequest(views.html.spaces.newSpace(errors)),
                    space => {
                      Logger.debug("Saving space " + space.name)

                      // insert space
                      spaces.insert(ProjectSpace(id = space.id, name = space.name, description = space.description,
                        created = space.created, creator = userId, homePage = space.homePage,
                        logoURL = space.logoURL, bannerURL = space.bannerURL, usersByRole = Map.empty,
                        collectionCount = 0, datasetCount = 0, userCount = 0, metadata = List.empty))
                      //TODO - Put Spaces in Elastic Search?
                      // index collection
                      // val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
                      //current.plugin[ElasticsearchPlugin].foreach{_.index("data", "collection", collection.id,
                      // Notify admins a new space is created
                      //  List(("name",collection.name), ("description", collection.description), ("created",dateFormat.format(new Date()))))}
                      //current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification(Utils.baseUrl(request), "Space","added",space.id.toString,space.name)}
                      // redirect to space page
                      Redirect(routes.Spaces.getSpace(space.id))
                    })
                }
                case ("Update") => {             // TODO: update space
                  spaceForm.bindFromRequest.fold(
                    errors => BadRequest(views.html.spaces.newSpace(errors)),
                    space => {
                      Logger.debug("updating space " + space.name)
                      spaces.get(space.id) match {
                        case Some(existing_space) =>{
                        val edited_space = existing_space.copy(name=space.name, description = space.description, logoURL = space.logoURL, bannerURL = space.bannerURL, homePage = space.homePage)
                          spaces.update(edited_space)
                      }
                        case None => {BadRequest("The space does not exist")}
                      }
                   Redirect(routes.Spaces.getSpace(space.id))
                })}
                case (_) => BadRequest("")
              }
            }

          }
        }
        case None => BadRequest("This action is not allowed")

      }
  }

   /**
   * Show the list page
   */
  def list(order: Option[String], direction: String, start: Option[String], limit: Int,
           filter: Option[String], mode: String) =
    SecuredAction(authorization = WithPermission(Permission.ListSpaces)) {
    implicit request =>
      implicit val user = request.user
      val d = if (direction.toLowerCase.startsWith("a")) {
        ASC
      } else if (direction.toLowerCase.startsWith("d")) {
        DESC
      } else if (direction == "1") {
        ASC
      } else if (direction == "-1") {
        DESC
      } else {
        ASC
      }
      val spaceList = spaces.list(order, d, start, limit, filter)
      val deletePermission = WithPermission(Permission.DeleteDatasets).isAuthorized(user)
      val prev = if (spaceList.size > 0) {
        spaces.getPrev(order, d, spaceList.head.created, limit, filter).getOrElse("")
      } else {
        ""
      }
      val next = if (spaceList.size > 0) {
        spaces.getNext(order, d, spaceList.last.created, limit, filter).getOrElse("")
      } else {
        ""
      }
      Ok(views.html.spaces.listSpaces(spaceList, order, direction, start, limit, filter, mode, deletePermission, prev, next))
  }
}
