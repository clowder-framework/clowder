/**
 *
 */
package services


import play.api.Play.current
import play.api.Play
import com.google.inject.Guice
import com.google.inject.AbstractModule

/**
 * @author Luigi Marini
 *
 */
object DI {
    lazy val injector = {
	    Play.isProd match {
	      case true => Guice.createInjector(new ProdModule)
	      case false => Guice.createInjector(new DevModule)
	    }
  }
}

/**
 * Default production module.
 */
class ProdModule extends AbstractModule {
  protected def configure() {
    bind(classOf[DatasetService]).to(classOf[MongoDBDatasetService])
    bind(classOf[FileService]).to(classOf[MongoDBFileService])
    bind(classOf[QueryService]).to(classOf[QueryServiceFileSystemDB])
    bind(classOf[CollectionService]).to(classOf[MongoDBCollectionService])
  }
}

/**
 * Default development module.
 */
class DevModule extends AbstractModule {
  protected def configure() {
    bind(classOf[DatasetService]).to(classOf[MongoDBDatasetService])
    bind(classOf[FileService]).to(classOf[MongoDBFileService])
    bind(classOf[QueryService]).to(classOf[QueryServiceFileSystemDB])
    bind(classOf[CollectionService]).to(classOf[MongoDBCollectionService])
  }
}