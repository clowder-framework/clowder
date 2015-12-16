package api

import javax.inject.Inject

import play.api.libs.json.Json.toJson
import services.mongodb.MongoDBProjectService

// TODO CATS-66 convert this to non mongo classs
class Projects @Inject()(projects: MongoDBProjectService) extends ApiController {
  /*
   * Add a new project to the database
   */
  def addproject(project: String) = PermissionAction(Permission.EditUser) { implicit request =>
    projects.addNewProject(project)
    Ok(toJson("added project"))
  }
}
