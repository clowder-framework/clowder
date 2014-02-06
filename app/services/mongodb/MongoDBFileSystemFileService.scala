package services.mongodb

import services.{FileSystemDB, FileService}

/**
 * Use mongo for metadata and the filesystem for blobs.
 * 
 * @author Luigi Marini
 *
 */
object MongoDBFileSystemFileService extends FileService with MongoFileDB with FileSystemDB {
}
