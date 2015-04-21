package services

/**
 * Service definition to interact with institutions database.
 *
 * @author Will Hennessy
 */
trait InstitutionService {

  /**
   * List all institutions in the system.
   */
  def getAllInstitutions: List[String]

  def addNewInstitution(institution: String)

}
