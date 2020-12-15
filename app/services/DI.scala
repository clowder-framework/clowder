package services

import play.api.Play.current
import com.google.inject.Guice
import com.google.inject.AbstractModule

/**
 * Guide module configuration.
 *
 *
 */
object DI {
    lazy val injector = Guice.createInjector(new ConfigurationModule)
}

/**
 * Default production module.
 */
class ConfigurationModule extends AbstractModule {
  override def configure() {}

  protected def get[T](key: String, missing: String) : Class[T] = {
    val name = current.configuration.getString(key).getOrElse(missing)
    Class.forName(name).asInstanceOf[Class[T]]
  }
}
