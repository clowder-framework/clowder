package controllers

import java.net.URL
import javax.inject.Inject

import api.{Permission, WithPermission}
import models.{DataMap, ProjectSpace, UUID}
import play.api.Logger
import play.api.data.{Forms, Form}
import play.api.data.Forms._
import play.api.data.format.Formats._
import java.util.Date
import services.{UserService, SpaceService}

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
      "homePages" -> Forms.list(Utils.CustomMappings.urlType)
    )
      ((name, description, logoUrl, bannerUrl, homePages) => ProjectSpace(name = name, description = description, created = new Date, creator = (UUID.apply(""),""),
          homePage = homePages, logoURL = logoUrl, bannerURL = bannerUrl, usersByRole= Map.empty, collectionCount=0, datasetCount=0, userCount=0, metadata=List.empty))
      ((space: ProjectSpace) => Some((space.name, space.description, space.logoURL, space.bannerURL, space.homePage)))
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

              val (id, name) = identity.email match{
                case Some(userEmail)=>{
                  val creator = users.findByEmail(userEmail).get
                  (creator.id, creator.fullName)
                }
                case None =>{(UUID.apply(""), "")}
              }

              //TODO - uncomment the commented out variables when serializing of URL's is done by Salat
              spaces.insert(ProjectSpace(id = space.id, name = space.name, description = space.description, created = space.created, creator = (id, name),
                homePage = List.empty/*space.homePage*/, logoURL = None/*space.logoURL*/, bannerURL = None /*space.bannerURL*/, usersByRole= Map.empty, collectionCount=0, datasetCount=0, userCount=0, metadata=List.empty))
              //TODO - Put Spaces in Elastic Search?
              // index collection
              // val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
              //current.plugin[ElasticsearchPlugin].foreach{_.index("data", "collection", collection.id,
              //  List(("name",collection.name), ("description", collection.description), ("created",dateFormat.format(new Date()))))}
              //TODO -Uncomment when Space list is done
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
  //TODO Dummy function remove when real function is put in
  def list(when: String, date: String, limit: Int, mode: String) = SecuredAction(authorization = WithPermission(Permission.ListSpaces)) {
    implicit request =>
      implicit val user = request.user
      Ok(views.html.newSpace(spaceForm))
  }

}
