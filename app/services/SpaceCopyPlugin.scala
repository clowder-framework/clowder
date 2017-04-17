package services

import play.api.{Application, Logger, Plugin}

/**
  * Plugin to enable copying datasets between spaces.
  */
class SpaceCopyPlugin(application: Application) extends Plugin {

  override def onStart() {
    Logger.info("Space Copy Plugin started")
  }


  override def onStop() {
    Logger.info("Space Copy Plugin has stopped")
  }
}
