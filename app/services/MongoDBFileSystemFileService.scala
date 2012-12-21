<<<<<<< HEAD
package services
=======
/**
 *
 */
package repository
>>>>>>> branch 'master' of https://lmarini@opensource.ncsa.illinois.edu/stash/scm/MED/medici-play.git

/**
 * Use mongo for metadata and the filesystem for blobs.
 * 
 * @author Luigi Marini
 *
 */
object MongoDBFileSystemFileService extends FileService with MongoFileDB with FileSystemDB {
}
