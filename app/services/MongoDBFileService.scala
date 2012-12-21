<<<<<<< HEAD
package services
=======
/**
 *
 */
package repository
>>>>>>> branch 'master' of https://lmarini@opensource.ncsa.illinois.edu/stash/scm/MED/medici-play.git

/**
 * Use mongo for both metadata and blobs.
 * 
 * @author Luigi Marini
 *
 */
object MongoDBFileService extends FileService with MongoFileDB with GridFSDB {
}
