package services

import play.api.{Application, Logger, Plugin}
/**
  * Plugin to enable sharing datasets and collections between Project Spaces
  */
class SpaceSharingPlugin(application: Application) extends Plugin {

  override def onStart() {
    Logger.info("Space Sharing Plugin started")
  }

  override def onStop() {
    Logger.info("Space Sharing Plugin has stopped")
  }
}
