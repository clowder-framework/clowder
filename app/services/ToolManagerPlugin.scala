package services

import models.UUID

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
  var runningTools: List[String] = List[String]()


  override def onStart() {
    Logger.debug("Initializing ToolManagerPlugin")
    _refreshToolsList
    _refreshRunningSessions
  }

  /**
    * Call external API to get list of valid endpoints for tool selection.
    */
  def _refreshToolsList() = {
    toolsList = List("PlantCV", "Frogger")

    val request: Future[Response] = url("http://141.142.209.108:8080/tools/docker/ipython/notebooks")
      .withHeaders("Content-Type" -> "application/json")
      .withQueryString("user" -> "")
      .withQueryString("pw" -> "")
      .get()

    request.map( response => {
      Logger.info(response.body.toString)
      // toolsList = response.body.list
    })
  }

  /**
    * Call external API to get list of valid endpoints for tool selection.
    * @return list of tools that can be selected for launch
    */
  def getToolsList(): List[String] = {
    _refreshToolsList
    return toolsList
  }

  /**
    * Fetch URLs of all running tool sessions the current user can access.
    */
  def _refreshRunningSessions() = {
    runningTools = List("PlantCV Instance 338127", "PlantCV Instance 544712")

    val request: Future[Response] = url("http://141.142.209.108:8080/getRunningTools")
      .withHeaders("Content-Type" -> "application/json")
      .withQueryString("user" -> "")
      .withQueryString("pw" -> "")
      .get()

    request.map( response => {
      Logger.info(response.body.toString)
      // runningTools = response.body.list
    })
  }

  /**
    * Fetch URLs of all running tool sessions the current user can access.
    * @return list of URLs for currently running sessions
    */
  def getRunningSessions(): List[String] = {
    _refreshRunningSessions
    return runningTools
  }

  /**
    * Send request to API to launch a new tool.
    * @param datasetid clowder ID of dataset to attach
    * @return ID of session that was launched
    */
  def launchTool(datasetid: String): UUID = {
    val newSessionID = UUID()

    Logger.info("LAUNCH TOOL PLUGIN WITH "+datasetid)
    val statusRequest: Future[Response] = url("http://141.142.209.108:8080/tools/docker/ipython/notebooks")
                                            .withHeaders("Content-Type" -> "application/json")
                                            .withQueryString("dataset" -> datasetid)
                                            .withQueryString("user" -> "")
                                            .withQueryString("pw" -> "")
                                            .get()

    statusRequest.map( response => {
      Logger.info("RESPONSE FROM EXTERNAL API")
      Logger.info(response.body.toString)
      // newSessionID =  response.body.id
    })

    return newSessionID
  }

  /**
    * Attach a dataset's files to an existing session.
    * @param sessionid ID of session to attach dataset to
    * @param datasetid clowder ID of dataset to attach
    */
  def attachDataset(sessionid: String, datasetid: String): Boolean = {
    Logger.info("LAUNCH TOOL PLUGIN WITH "+datasetid)
    val statusRequest: Future[Response] = url("http://141.142.209.108:8080/attachDataset")
      .withHeaders("Content-Type" -> "application/json")
      .withQueryString("session" -> sessionid)
      .withQueryString("dataset" -> datasetid)
      .withQueryString("user" -> "")
      .withQueryString("pw" -> "")
      .get()

    statusRequest.map( response => {
      Logger.info(response.body.toString)
    })

    return true
  }

  /**
    * Terminate a running tool session.
    * @param sessionid ID of session to stop
    * @return
    */
  def closeSession(sessionid: String): Boolean = {
    val statusRequest: Future[Response] = url("http://141.142.209.108:8080/terminate")
      .withHeaders("Content-Type" -> "application/json")
      .withQueryString("session" -> sessionid)
      .withQueryString("user" -> "")
      .withQueryString("pw" -> "")
      .get()

    statusRequest.map( response => {
      Logger.info(response.body.toString)
    })

    return true
  }

  override def onStop() {
    toolsList = List[String]()
    Logger.info("ToolManagerPlugin has stopped")
  }

}
