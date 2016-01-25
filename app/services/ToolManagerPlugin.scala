package services

import models.UUID

import scala.collection.mutable.Map
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.mvc._
import play.api.libs.json._
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

  var idMap: Map[UUID, String] = Map() // SessionID -> URL from api, or empty string
  var dsMap: Map[UUID, List[Map[String,String]]] = Map() // SessionID -> list of attached dataset (name, url) pairs


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
    * Fetch URLs of all running tool sessions the current user can access from external API.
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
  def getRunningSessionIDs(): List[UUID] = {
    //_refreshRunningSessions
    //return runningTools

    val sessionids = idMap.keys.toList
    return sessionids
  }

  /**
    * Send request to API to launch a new tool.
    * @param datasetid clowder ID of dataset to attach
    * @return ID of session that was launched
    */
  def launchTool(dsUrl: String, sessionId: UUID, datasetid: UUID, datasetname: String) = {
    Logger.debug("LAUNCH TOOL WITH DATASET: "+dsUrl)
    Logger.debug("SESSIONID GENERATED: "+sessionId)

    // Map session to empty URL for now, and add this dataset to its list
    idMap(sessionId) = "";
    dsMap(sessionId) = List(Map(datasetname -> dsUrl));

    val postdata = Json.obj(
      "dataset" -> dsUrl,
      "user" -> "mburnet2@illinois.edu",
      "pw" -> "tSzx7dINA8RxFEKp7sX8",
      "host" -> "http://141.142.209.108"
    );
    val statusRequest: Future[Response] = url("http://141.142.209.108:8080/tools/docker/ipython").post(postdata);

    statusRequest.map( response => {
      Logger.debug(("API RESPONSED: "+response.body.toString))
      val resp = (Json.parse(response.body) \ "URL")

      resp match {
        case _: JsUndefined => {}
        case _ => {
          Logger.debug(("MAPPING "+sessionId+" TO "+resp.toString))
          idMap(sessionId) = resp.toString
        }
      }
    })
  }

  /**
    * Check to see whether request UUID has received a URL from API yet.
   */
  def checkForSessionUrl(sessionId: UUID): Option[String] = {
    var completed: Option[String] = None

    Logger.debug("CHECKING STATUS OF SESSIONID: "+sessionId)

    if (idMap.contains(sessionId)) {
      completed = idMap.get(sessionId)
    }

    return completed
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
