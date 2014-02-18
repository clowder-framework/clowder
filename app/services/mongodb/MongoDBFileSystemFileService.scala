package services.mongodb

import services.FileService
import services.filesystem.FileSystemDB

/**
 * Use mongo for metadata and the filesystem for blobs.
 * 
 * @author Luigi Marini
 *
 */
@deprecated
object MongoDBFileSystemFileService extends FileService with MongoFileDB with FileSystemDB {
}
