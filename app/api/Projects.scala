package api

import services.mongodb.MongoDBProjectService
import javax.inject.Inject
import play.api.Play.current
import play.api.Play.configuration
import models.Project
import play.api.libs.json.Json.toJson

class Projects @Inject() (projects: MongoDBProjectService) extends ApiController {

  /*
   * Add a new project to the database
   */
  def addproject(project: String) = SecuredAction(authorization = WithPermission(Permission.EditUser)) {
    implicit request =>
      projects.addNewProject(project)
      Ok(toJson("added project"))
  }

}
