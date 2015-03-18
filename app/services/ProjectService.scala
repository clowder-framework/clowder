package services

/**
 * Service definition to interact with projects database.
 *
 * @author Will Hennessy
 */
trait ProjectService {

  /**
   * List all projects in the system.
   */
  def getAllProjects: List[String]

  def addNewProject(project: String)

}
