package services

/**
 * Wire services to specific implementations.
 * 
 * @author Luigi Marini
 *
 */
object Services {
  
  // wire application
  val files: FileService = MongoDBFileService
}