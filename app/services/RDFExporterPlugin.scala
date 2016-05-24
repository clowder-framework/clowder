package services

import play.api.{Logger, Plugin, Application}
import play.libs.Akka
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current

/**
 * Plugin for RDF Exporter
 */
class RDFExporterPlugin(application: Application) extends Plugin {

  val files: FileService =  DI.injector.getInstance(classOf[FileService])

  override def onStart() {
    Logger.debug("Starting up RDF Exporter Plugin")
    //Clean temporary RDF files if RDF exporter is activated
    if(current.plugin[RDFExportService].isDefined){
      var timeInterval = play.Play.application().configuration().getInt("rdfTempCleanup.checkEvery")
      Akka.system().scheduler.schedule(0.minutes, timeInterval.intValue().minutes){
        files.removeTemporaries()
      }
    }
  }

  override def onStop() {
    Logger.debug("Shutting down RDF Exporter Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("rdfexporter").filter(_ == "no").isDefined
  }
}
