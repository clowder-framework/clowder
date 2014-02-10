package services

/**
 * Use mongo for both metadata and blobs.
 * 
 * @author Luigi Marini
 *
 */
class MongoDBFileService extends FileService with MongoFileDB with GridFSDB {
}
