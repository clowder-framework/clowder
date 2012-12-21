<<<<<<< HEAD
package services
=======
package repository
>>>>>>> branch 'master' of https://lmarini@opensource.ncsa.illinois.edu/stash/scm/MED/medici-play.git

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
