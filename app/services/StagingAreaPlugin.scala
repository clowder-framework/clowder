package services

import play.api.{Plugin, Logger, Application}
/**
 * Staging Area Plugin.
 */
class StagingAreaPlugin(application: Application) extends Plugin{

  override def onStart() {
    Logger.debug("Staging Area Plugin started")
  }

  override def onStop() {
    Logger.info("Staging Area Plugin has stopped")
  }
}
