package services

/**
 * Service definition to interact with projects database.
 *
 */
trait ProjectService {

  /**
   * List all projects in the system.
   */
  def getAllProjects: List[String]

  def addNewProject(project: String)

}
