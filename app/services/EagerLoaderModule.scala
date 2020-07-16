package services

import com.google.inject.AbstractModule

/** Eagerly load Startup script */
class EagerLoaderModule extends AbstractModule {

  override def configure() = {
    bind(classOf[StartUpService]).asEagerSingleton
  }
}
