package repository

/**
 * Database registry of services using dependency injection and the cake pattern.
 * 
 * @author Luigi Marini
 *
 */
object DBRegistry extends FileServiceComponent with DiskFileRepositoryComponent {
  val fileRepository = new FileRepository
  val fileService = new FileService
}