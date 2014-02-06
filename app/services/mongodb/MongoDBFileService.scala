package services.mongodb

import services.FileService

/**
 * Use mongo for both metadata and blobs.
 * 
 * @author Luigi Marini
 *
 */
class MongoDBFileService extends FileService with MongoFileDB with GridFSDB {
}
