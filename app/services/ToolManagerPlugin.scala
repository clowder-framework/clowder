package services

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.mvc._
import play.api.libs.ws.Response
import play.api.libs.ws.WS._
import play.api.{Plugin, Logger, Application}



/**
  * ToolManager plugin.
  *
  */
class ToolManagerPlugin(application: Application) extends Plugin {
  val comments: CommentService = DI.injector.getInstance(classOf[CommentService])
  val files: FileService = DI.injector.getInstance(classOf[FileService])
  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])
  var toolsList: List[String] = List[String]()
  var launchedURLs: List[String] = List[String]()


  override def onStart() {
    Logger.debug("Initializing ToolManagerPlugin")
    updateToolsList
  }

  def updateToolsList(): Boolean = {
    // This will be a call to NDS API to get eligible tools
    toolsList = List("PlantCV", "Frogger")
    true
  }

  def getRunningSessions(): List[String] = {
    // Get active tool/VM sessions for this user

    // launchedURLs
    List("PlantCV Instance 338127", "PlantCV Instance 544712")
  }

  def launchTool(datasetid: String): Boolean = {
    // Initiate the process of creating a new Tool container

    Logger.info("LAUNCH TOOL PLUGIN")
    Logger.info(datasetid)
    val statusRequest: Future[Response] = url("http://ned.usgs.gov/epqs/pqs.php?x=-88.22&y=40.12&output=json").get()
    //val statusRequest: Future[Response] = url("http://141.142.209.108:8080/tools/docker/ipython/notebooks")
    //                                        .withHeaders("Content-Type" -> "application/json")
    //                                        .withQueryString("dataset" -> datasetid)
    //                                        .withQueryString("user" -> "mburnet2@illinois.edu")
    //                                        .withQueryString("pw" -> "tSzx7dINA8RxFEKp7sX8")
    //                                        .get()

    statusRequest.map( response => {
      Logger.info("RESPONSE FROM EXTERNAL API")
      Logger.info(response.body.toString)
      // launchedURLs :+ response.body.URL
    })

    return true
  }

  def attachDataset(): Boolean = {
    true
  }

  override def onStop() {
    toolsList = List[String]()
    Logger.info("ToolManagerPlugin has stopped")
  }

}
