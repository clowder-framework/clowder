package controllers

import java.net.URL
import javax.inject.Inject

import api.{Permission, WithPermission}
import models.{Dataset, Collection, ProjectSpace, UUID}
import play.api.Logger
import play.api.data.{Forms, Form}
import play.api.data.Forms._
import play.api.data.format.Formats._
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
      "homePages" -> Forms.list(Utils.CustomMappings.urlType)
    )
      ((name, description, logoUrl, bannerUrl, homePages) => ProjectSpace(name = name, description = description,
        created = new Date, creator = UUID.generate(),
          homePage = homePages, logoURL = logoUrl, bannerURL = bannerUrl, usersByRole= Map.empty, collectionCount=0,
        datasetCount=0, userCount=0, metadata=List.empty))
      ((space: ProjectSpace) => Some((space.name, space.description, space.logoURL, space.bannerURL, space.homePage)))
  )

  /**
   * Space main page.
   */
  def getSpace(id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowSpace)) { implicit request =>
    implicit val user = request.user
    spaces.get(id) match {
      case Some(s) => {
          val collectionsInSpace = spaces.getCollectionsInSpace(id)
          for (aCollection <- collectionsInSpace) {
              Logger.debug("A collection in the space is " + aCollection.name)
          }
          
          val datasetsInSpace = spaces.getDatasetsInSpace(id)
          for (aDataset <- datasetsInSpace) {
              Logger.debug("A dataset in the space is " + aDataset.name)
          }
          Ok(views.html.spaces.space(s, collectionsInSpace, datasetsInSpace))
      }
      case None => InternalServerError("Space not found")
    }
  }

  def newSpace() = SecuredAction(authorization = WithPermission(Permission.CreateSpaces)) {
    implicit request =>
      implicit val user = request.user
      //TODO - bug in html page. If there is an error with one of the fields, the delete button for home pages disappears
      //inserting the following snippet inside the @repeat block in the newSpace.scala.html shows the delete button on error, one too many though
     /* @if(myForm.hasErrors && myForm("homePages").indexes.length > 1) {
      <div class="home-page-delete"><a href="#">delete</a></div>
    }
    */
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
              val userId = request.mediciUser.fold(UUID.generate)(_.id)

              //TODO - uncomment the commented out variables when serializing of URL's is done by Salat
              spaces.insert(ProjectSpace(id = space.id, name = space.name, description = space.description,
                created = space.created, creator = userId, homePage = List.empty/*space.homePage*/,
                logoURL = None/*space.logoURL*/, bannerURL = None /*space.bannerURL*/, usersByRole= Map.empty,
                collectionCount=0, datasetCount=0, userCount=0, metadata=List.empty))
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

  /**
   * Show the list page
   */
  def list(order: Option[String]=None, direction: String="asc", start: Option[String]=None, limit: Int=20,
           filter: Option[String]=None, mode: String="tile") =
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
      // TODO fetch 1 extra space so we have the next/prev item
      val s = spaces.list(order, d, start, limit, None)
      // TODO fill in
      val canDelete = false
      // TODO fetch page before/after so we have prev item
      val prev = ""
      val next = ""
          
        //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
        //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar 
        //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14   
        var viewMode = mode;
        //Always check to see if there is a session value          
        request.cookies.get("view-mode") match {
            case Some(cookie) => {
                viewMode = cookie.value
            }
            case None => {
                //If there is no cookie, and a mode was not passed in, default it to tile
                if (viewMode == null || viewMode == "") {
                    viewMode = "tile"
                }
            }
        }    
      Ok(views.html.spaces.listSpaces(s, order, direction, start, limit, filter, viewMode, canDelete, prev, next))
  }
}
