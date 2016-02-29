package services

import java.util.Calendar
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
  * ToolSession describes an active analysis environment. Each instance keeps a history of datasets that were
  * uploaded, even if those datasets were changed or removed within the environment itself.
  */
class ToolInstance() {
  var id: UUID = UUID()
  var name = "" // Human-readable name for easier management of lists
  var url: String = ""
  var externalId: String = "" // For tracking token used by external API
  var toolType: String = ""
  var uploadHistory: Map[UUID, String] = Map()
  var owner = None: Option[models.User]
  var created = Calendar.getInstance.getTime
  var updated = created

  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val users: UserService = DI.injector.getInstance(classOf[UserService])

  def setID(externalid: String): Unit = {
    // Define the external Tool URL this ToolInstance points to
    externalId = externalid
    updateTimestamp()
  }

  def setURL(sessionurl: String): Unit = {
    // Define the external Tool URL this ToolInstance points to
    url = sessionurl
    updateTimestamp()
  }

  def updateHistory(datasetId: UUID): Unit = {
    // Register a new Dataset to this ToolInstance (does not send request to API)
    datasets.get(datasetId) match {
      case Some(ds) => {
        uploadHistory(datasetId) = ds.name
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
  * This manages ToolInstances(), each describing a running tool/analysis environment/VM that was launched
  * from Clowder. Supports launching, stopping, getting info of analysis environment sessions.
  */
class ToolManagerPlugin(application: Application) extends Plugin {
  val comments: CommentService = DI.injector.getInstance(classOf[CommentService])
  val files: FileService = DI.injector.getInstance(classOf[FileService])
  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])

  var toolList: JsObject = JsObject(Seq[(String, JsValue)]()) // ToolAPIEndpoint -> {"name": <>, "description": <>}
  var instanceMap: Map[UUID, ToolInstance] = Map() // ToolManager SessionId -> ToolInstance instance

  override def onStart() {
    Logger.debug("Initializing ToolManagerPlugin")
    refreshLaunchableToolsFromServer()
  }

  /**
    * Get list of valid endpoints for tool selection.
    * @return list of tools that can be selected for launch
    */
  def getLaunchableTools(): JsObject = {
    // refresh is asynchronous so it won't finish before toolList is returned; still refresh for next time.
    refreshLaunchableToolsFromServer()
    return toolList
  }

  /**
    * Update toolList from server to get list of eligible API endpoints to launch tools from.
    */
  def refreshLaunchableToolsFromServer(): Unit = {
    val apipath = play.Play.application().configuration().getString("toolmanager.host") + ":" +
                  play.Play.application().configuration().getString("toolmanager.port")
    val statusRequest: Future[Response] = url(apipath+"/tools").get()

    statusRequest.map( response => {
      val jsonObj = Json.parse(response.body)

      jsonObj match {
        case _: JsUndefined => {}
        case j: JsObject => {
          toolList = j
        }
      }
    })
  }

  /**
    * Send request to API to launch a new tool.
    * @param hostURL is url of API server
    * @param instanceName user-provided name of Session to display
    * @param datasetId clowder ID of dataset to attach
    * @param toolType name of environment type that is being launched
    * @return ID of session that was launched
    */
  def launchTool(hostURL: String, instanceName: String, toolType: String, datasetId: UUID, ownerId: Option[UUID]): UUID = {
    // Generate a new session & add to instanceMap
    val instance = new ToolInstance()
    instance.setName(instanceName)
    instance.setToolType(toolType)
    ownerId match {
      case Some(o) => instance.setOwner(o)
      case None => {}
    }
    instanceMap(instance.id) = instance

    // Send request to API to launch Tool
    // TODO: Figure out something better than the key here
    val dsURL = hostURL+controllers.routes.Datasets.dataset(datasetId).url
    instance.updateHistory(datasetId)

    val apipath = play.Play.application().configuration().getString("toolmanager.host") + ":" +
                  play.Play.application().configuration().getString("toolmanager.port") + "/tools/" + toolType
    val statusRequest: Future[Response] = url(apipath).post(Json.obj(
      "dataset" -> (dsURL.replace("/datasets", "/api/datasets")+"/download"),
      "key" -> play.Play.application().configuration().getString("commKey")
    ))

    statusRequest.map( response => {
      val externalURL = (Json.parse(response.body) \ "URL")
      val externalID = (Json.parse(response.body) \ "id")

      externalURL match {
        case _: JsUndefined => {}
        case _ => {
          var matched = instanceMap(instance.id)
          matched.setURL(externalURL.toString)
          instanceMap(instance.id) = matched
        }
      }

      externalID match {
        case _: JsUndefined => {}
        case _ => {
          var matched = instanceMap(instance.id)
          matched.setID(externalID.toString.replace("\"",""))
          instanceMap(instance.id) = matched
        }
      }
    })

    return instance.id
  }

  /**
    * Return URL associated with instanceID if available, otherwise a blank string
    * @param instanceID instanceID to check
    * @return URL string or blank string depending on availability
   */
  def checkForInstanceURL(instanceID: UUID): String = {
    val result: String = instanceMap.get(instanceID) match {
      case Some(instance) => instance.url
      case None => ""
    }

    return result
  }

  /**
    * Get a subset of instanceMap with ToolInstances that have datasetID in history
    * @param datasetID filter instanceMap to instances with this dataset attached
    * @return map of instanceID to ToolInstance instance
    */
  def getInstancesWithDataset(datasetID: UUID): Map[UUID, ToolInstance] = {
    val historyIDs = for{(instanceID, instance) <- instanceMap
                          if instance.uploadHistory.contains(datasetID)
    }yield instanceID

    val uploaded = Map[UUID,ToolInstance]()
    for (instanceID <- historyIDs) {
      instanceMap.get(instanceID) match {
        case Some(ts) =>
          uploaded(instanceID) = ts
        case None => {}
      }
    }

    return uploaded
  }

  /**
    * Get a map of instanceID to instanceName
    */
  def getInstances(): Map[String, String] = {
    val instances = Map[String, String]()

    for ((instanceID, instance) <- instanceMap) {
      instances(instanceID.toString)  = instance.name
    }

    return instances
  }

  /**
    * Upload dataset files to an existing instance.
    * @param instanceID ID of instance to attach dataset to
    * @param datasetID clowder ID of dataset to attach
    */
  def uploadDataset(instanceID: String, datasetID: String): Boolean = {
    val apihost = play.Play.application().configuration().getString("toolmanager.host")
    val statusRequest: Future[Response] = url(apihost+":8080/attachDataset").post(Json.obj(
      "key" -> play.Play.application().configuration().getString("commKey"),
      "session" -> instanceID,
      "host" -> apihost
    ))

    statusRequest.map( response => {
      Logger.info(response.body.toString)
    })

    return true
  }

  /**
    * Terminate a running tool instance.
    * @param toolType is the endpoint to issue DELETE request to
    * @param instanceID ID of ToolInstance to stop
    */
  def removeInstance(toolType: String, instanceID: UUID): Unit = {
    val apipath = play.Play.application().configuration().getString("toolmanager.host") + ":" +
                  play.Play.application().configuration().getString("toolmanager.port") + "/tools/" + toolType

    instanceMap.get(instanceID) match {
      case Some(ts) => {
        val instanceApiID = ts.externalId // External identifier on NDS api
        val statusRequest: Future[Response] = url(apipath+"?id="+instanceApiID.toString()).delete()
      }
    }

    instanceMap = instanceMap - instanceID
  }

  override def onStop() {
    instanceMap = Map()
    Logger.info("ToolManagerPlugin has stopped")
  }

}
