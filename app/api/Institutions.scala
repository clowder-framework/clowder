package api

import services.mongodb.MongoDBInstitutionService
import javax.inject.Inject
import play.api.Play.current
import play.api.Play.configuration
import models.Institution
import play.api.libs.json.Json.toJson

class Institutions @Inject() (institutions: MongoDBInstitutionService) extends ApiController {

  /*
   * Add a new institution to the database
   */
  def addinstitution(institution: String) = SecuredAction(authorization = WithPermission(Permission.AddInstitution)) {
    implicit request =>
      institutions.addNewInstitution(institution)
      Ok(toJson("added institution"))
  }

}
