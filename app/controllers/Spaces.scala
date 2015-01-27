package controllers

import javax.inject.Inject

import api.{Permission, WithPermission}
import models.{ProjectSpace, UUID}
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import java.util.Date
import services.SpaceService

/**
 * Spaces allow users to partition the data into realms only accessible to users with the right permissions.
 *
 * @author Luigi Marini
 * @author Rob Kooper
 *
 */
class Spaces @Inject()(spaces: SpaceService) extends SecuredController {
  /**
   * New project space form.
   */
  val spaceForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText
    )
      ((name, description) => ProjectSpace(name = name, description = description, created = new Date, creator = (UUID.apply(""),""),
          homePage = List.empty, logoURL = None, bannerURL = None, usersByRole= Map.empty, collectionCount=0, datasetCount=0, userCount=0, metadata=List.empty))
      ((space: ProjectSpace) => Some((space.name, space.description)))
  )

  def newSpace() = SecuredAction(authorization = WithPermission(Permission.CreateSpaces)) {
    implicit request =>
      implicit val user = request.user
      Ok(views.html.newSpace(spaceForm))
  }

  /**
   * Create collection.
   */
  def submit() = SecuredAction(authorization = WithPermission(Permission.CreateSpaces)) {
    implicit request =>
      implicit val user = request.user
      user match {
        case Some(identity) => {

          spaceForm.bindFromRequest.fold(
            errors => BadRequest(views.html.newSpace(errors)),
            space => {
              Logger.debug("Saving space " + space.name)
              spaces.insert(ProjectSpace(id = space.id, name = space.name, description = space.description, created = space.created, creator = (UUID.apply(""),""),
                homePage = List.empty, logoURL = None, bannerURL = None, usersByRole= Map.empty, collectionCount=0, datasetCount=0, userCount=0, metadata=List.empty))

              // index collection
              // val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
              //current.plugin[ElasticsearchPlugin].foreach{_.index("data", "collection", collection.id,
              //  List(("name",collection.name), ("description", collection.description), ("created",dateFormat.format(new Date()))))}

              // redirect to collection page
              //Redirect(routes.Spaces.space(space.id))
              //current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification(Utils.baseUrl(request), "Space","added",space.id.toString,space.name)}
              //Redirect(routes.Spaces.space(space.id))
              Redirect(routes.Spaces.list()).flashing("error" -> "You created new space.")
            })
        }
        case None => Redirect(routes.Spaces.list()).flashing("error" -> "You are not authorized to create new spaces.")
      }
  }

  def list(when: String, date: String, limit: Int, mode: String) = SecuredAction(authorization = WithPermission(Permission.ListSpaces)) {
    implicit request =>
      implicit val user = request.user
      Ok(views.html.newSpace(spaceForm))
  }

}
