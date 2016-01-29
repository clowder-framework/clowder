package services

import java.util.{Calendar, Base64}
import java.nio.charset.StandardCharsets
import scala.collection.mutable.Map
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.ws.Response
import play.api.libs.ws.WS._
import play.api.{Plugin, Logger, Application}

import models.UUID

/**
  * ToolSession describes a running external Tool/VM associated with one or more attached datasets.
  */
class ToolSession () {
  var id: UUID = UUID()
  var name = ""
  var url: String = ""
  var externalId: String = "" // For tracking token used by external API
  var toolType: String = ""
  var attachedDatasets: Map[UUID, String] = Map()
  var owner = None: Option[models.User]
  var created = Calendar.getInstance.getTime
  var updated = created

  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val users: UserService = DI.injector.getInstance(classOf[UserService])

  def attachURL(sessionurl: String): Unit = {
    // Define the external Tool URL this ToolSession points to
    url = sessionurl
    updateTimestamp()
  }

  def attachDataset(datasetId: UUID): Unit = {
    // Register a new Dataset to this ToolSession (does not send request to API)
    datasets.get(datasetId) match {
      case Some(ds) => {
        attachedDatasets(datasetId) = ds.name
      }
      case None => {}
    }
    updateTimestamp()
  }

  def setName(sessname: String): Unit = {
    name = sessname
    updateTimestamp()
  }

  def setOwner(ownerId: UUID): Unit = {
    users.get(ownerId) match {
      case Some(u) => owner = Some(u)
      case None => owner = None
    }
    updateTimestamp()
  }

  def setToolType(tooltype: String): Unit = {
    toolType = tooltype
    updateTimestamp()
  }

  def updateTimestamp(): Unit = {
    updated = Calendar.getInstance.getTime
  }
}

/**
  * ToolManager plugin.
  * This manages ToolSessions(), each describing a running tool/analysis environment/VM that was launched
  * from Clowder. Supports launching, stopping, getting info of analysis environment sessions.
  */
class ToolManagerPlugin(application: Application) extends Plugin {
  val comments: CommentService = DI.injector.getInstance(classOf[CommentService])
  val files: FileService = DI.injector.getInstance(classOf[FileService])
  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])

  var toolsList: List[String] = List[String]()
  var sessionMap: Map[UUID, ToolSession] = Map() // ToolManager SessionId -> ToolSession instance

  override def onStart() {
    Logger.debug("Initializing ToolManagerPlugin")
  }

  /**
    * Call external API to get list of valid endpoints for tool selection.
    * @return list of tools that can be selected for launch
    */
  def getLaunchableTools(): List[String] = {
    toolsList = List("Jupyter", "PlantCV")

    //val request: Future[Response] = url("http://141.142.209.108:8080/tools").get()

    //request.map( response => {
    //  Logger.info(response.body.toString)
    //  // toolsList = response.body.list
    //})

    return toolsList
  }

  /**
    * Send request to API to launch a new tool.
    * @param sessionName user-provided name of Session to display
    * @param datasetId clowder ID of dataset to attach
    * @param toolType name of environment type that is being launched
    * @return ID of session that was launched
    */
  def launchTool(hostURL: String, sessionName: String, toolType: String, datasetId: UUID, ownerId: Option[UUID]): UUID = {
    // Generate a new session & add to sessionMap
    val newSession = new ToolSession()
    newSession.setName(sessionName)
    newSession.setToolType(toolType)
    newSession.attachDataset(datasetId)
    ownerId match {
      case Some(o) => newSession.setOwner(o)
      case None => {}
    }
    sessionMap(newSession.id) = newSession

    // Send request to API to launch Tool
    // TODO: Figure out something better than the key here
    var dsURL = controllers.routes.Datasets.dataset(datasetId).url
    //val appContext = play.Play.application().configuration().getString("application.context")
    //if (appContext != null) dsURL = appContext+dsURL
    dsURL = hostURL + dsURL

    Logger.debug(dsURL.replace("/datasets", "/api/datasets")+"/download")
    val statusRequest: Future[Response] = url("http://141.142.209.108:8080/tools/docker/ipython").post(Json.obj(
      "dataset" -> (dsURL.replace("/datasets", "/api/datasets")+"/download"),
      "key" -> play.Play.application().configuration().getString("commKey"),
      "user" -> "mburnet2@illinois.edu",
      "pw" -> "tSzx7dINA8RxFEKp7sX8",
      "host" -> "http://141.142.209.108"
    ))

    statusRequest.map( response => {
      Logger.debug(("TOOL API RESPONSED: "+response.body.toString))
      val externalURL = (Json.parse(response.body) \ "URL")

      externalURL match {
        case _: JsUndefined => {}
        case _ => {
          var matchedSess = sessionMap(newSession.id)
          matchedSess.attachURL(externalURL.toString)
          sessionMap(newSession.id) = matchedSess
        }
      }
    })

    return newSession.id
  }

  /**
    * Return URL associated with sessionID if available, otherwise a blank string
    * @param sessionId sessionID to check
    * @return URL string or blank string depending on availability
   */
  def checkForSessionUrl(sessionId: UUID): String = {
    val completed: String = sessionMap.get(sessionId) match {
      case Some(sess) => sess.url
      case None => ""
    }

    return completed
  }

  /**
    * Get a subset of sessionMap which only includes ToolSessions that have datasetId attached
    * @param datasetId filter sessionMap to sessions with this dataset attached
    * @return Map of sessionId to ToolSession instance
    */
  def getAttachedSessions(datasetId: UUID): Map[UUID, ToolSession] = {
    val attachedIds = for{(sessId, sess) <- sessionMap
                        if sess.attachedDatasets.contains(datasetId)
                        }yield sessId

    val attached = Map[UUID,ToolSession]()
    for (sessionId <- attachedIds) {
      Logger.debug(sessionId.toString)
      sessionMap.get(sessionId) match {
        case Some(ts) => attached(sessionId) = ts
        case None => {}
      }
    }

    return attached
  }

  /**
    * Get a map of SessionID to sessionName that only includes sessions that don't have datasetId attached
    * @param datasetId Result will include sessions without this dataset attached
    * @return Map of sessionID string to user-defined session Name
    */
  def getUnattachedSessions(datasetId: UUID): Map[String, String] = {
    val unSess = Map[String, String]()

    for ((sessId, sess) <- sessionMap) {
      val attachedDsMap = sess.attachedDatasets
      var foundDs = false
      for (dsId <- attachedDsMap.keys.toList) {
        if (dsId == datasetId) foundDs = true
      }
      if (foundDs == false) unSess(sessId.toString)  = sess.name
    }

    return unSess
  }

  /**
    * Attach a dataset's files to an existing session.
    * @param sessionId ID of session to attach dataset to
    * @param datasetid clowder ID of dataset to attach
    */
  def attachDataset(sessionId: String, datasetid: String): Boolean = {
    val statusRequest: Future[Response] = url("http://141.142.209.108:8080/attachDataset").post(Json.obj(
      "key" -> play.Play.application().configuration().getString("commKey"),
      "session" -> sessionId,
      "host" -> "http://141.142.209.108"
    ))

    statusRequest.map( response => {
      Logger.info(response.body.toString)
    })

    return true
  }

  /**
    * Terminate a running tool session.
    * @param sessionId ID of Tool Session to stop
    */
  def removeSession(sessionId: UUID): Unit = {
    //val statusRequest: Future[Response] = url("http://141.142.209.108:8080/tools/docker/ipython/<id>").delete()

    sessionMap.get(sessionId) match {
      case Some(ts) => {
        val sessApiId = ts.externalId // External identifier on NDS api
      }
    }
    //statusRequest.map( response => {
    //  Logger.info(response.body.toString)
    //})

    sessionMap = sessionMap - sessionId
  }

  override def onStop() {
    toolsList = List[String]()
    sessionMap = Map()
    Logger.info("ToolManagerPlugin has stopped")
  }

}
