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
  val datasets: DatasetService = MongoDBDatasetService
  val collections: CollectionService = MongoDBCollectionService
  val queries: QueryService=QueryServiceFileSystemDB
}
