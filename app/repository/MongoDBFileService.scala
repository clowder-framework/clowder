/**
 *
 */
package repository

/**
 * Use mongo for both metadata and blobs.
 * 
 * @author Luigi Marini
 *
 */
object MongoDBFileService extends FileService with MongoFileDB with GridFSDB {
}