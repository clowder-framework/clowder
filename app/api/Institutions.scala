package api

import services.mongodb.MongoDBInstitutionService
import javax.inject.Inject
import play.api.libs.json.Json.toJson

// TODO CATS-66 remove MongoDBInstitutionService, make part of UserService?
class Institutions @Inject() (institutions: MongoDBInstitutionService) extends ApiController {

  /*
   * Add a new institution to the database
   */
  def addinstitution(institution: String) = PermissionAction(Permission.EditUser) { request =>
    institutions.addNewInstitution(institution)
    Ok(toJson("added institution"))
  }
}
