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
  * ToolSession describes a running external Tool/VM associated with one or more datasets in Clowder.
  */
class ToolSession () {
  var id: UUID = UUID()
  var name = ""
  var url: String = ""
  var externalId: String = "" // For tracking token used by external API
  var toolType: String = ""
  var attachedDatasets: Map[UUID, String] = Map()
  var created = Calendar.getInstance.getTime
  var updated = created
  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])

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
  *
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

    //val request: Future[Response] = url("http://141.142.209.108:8080/tools/docker/ipython/notebooks")
    //  .withHeaders("Content-Type" -> "application/json")
    //  .withQueryString("user" -> "")
    //  .withQueryString("pw" -> "")
    //  .get()

    //request.map( response => {
    //  Logger.info(response.body.toString)
    //  // toolsList = response.body.list
    //})

    return toolsList
  }

  /**
    * Send request to API to launch a new tool.
    * @param datasetId clowder ID of dataset to attach
    * @return ID of session that was launched
    */
  def launchTool(sessionName: String, toolType: String, datasetId: UUID): UUID = {
    // Generate a new session & add to sessionMap
    val newSession = new ToolSession()
    newSession.setName(sessionName)
    newSession.setToolType(toolType)
    newSession.attachDataset(datasetId)
    sessionMap(newSession.id) = newSession

    // Send request to API to launch Tool
    // TODO: Figure out something better than the key here
    val dsURL = controllers.routes.Datasets.dataset(datasetId).url
    val statusRequest: Future[Response] = url("http://141.142.209.108:8080/tools/docker/ipython").post(Json.obj(
      "dataset" -> (dsURL.replace("/datasets", "/api/datasets")+"/download"),
      "key" -> play.Play.application().configuration().getString("commKey"),
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
    * Check to see whether request UUID has received a URL from API yet.
   */
  def checkForSessionUrl(sessionId: UUID): String = {
    var completed = ""

    if (sessionMap.contains(sessionId)) {
      sessionMap.get(sessionId) match {
        case Some(sess) => completed = sess.url
        case None => {}
      }
    }

    return completed
  }

  def getAttachedSessions(datasetId: UUID): Map[UUID, ToolSession] = {
    // Return sessionMap filtered to only sessions containing provided UUID
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

  def getUnattachedSessions(datasetId: UUID): Map[String, String] = {
    // Return a list of tool session Ids that do NOT have datasetID attached already
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
    * @param sessionid ID of session to attach dataset to
    * @param datasetid clowder ID of dataset to attach
    */
  def attachDataset(sessionid: String, datasetid: String): Boolean = {
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
    * @param sessionId ID of session to stop
    * @return
    */
  def removeSession(sessionId: UUID): Boolean = {
    //val statusRequest: Future[Response] = url("http://141.142.209.108:8080/terminate")
     // .withHeaders("Content-Type" -> "application/json")
     // .withQueryString("session" -> sessionid)
     // .delete()

    //statusRequest.map( response => {
    //  Logger.info(response.body.toString)
    //})

    sessionMap = sessionMap - sessionId

    return true
  }

  override def onStop() {
    toolsList = List[String]()
    sessionMap = Map()
    Logger.info("ToolManagerPlugin has stopped")
  }

}
