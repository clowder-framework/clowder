package services

/**
 * Service definition to interact with institutions database.
 *
 */
trait InstitutionService {

  /**
   * List all institutions in the system.
   */
  def getAllInstitutions: List[String]

  def addNewInstitution(institution: String)

}
