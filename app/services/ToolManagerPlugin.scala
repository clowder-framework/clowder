package services

import java.util.{Calendar, Date}
import java.text.SimpleDateFormat
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
  var toolPath: String = "" // API endpoint that was used to launch this instance, e.g. "rstudio"
  var toolName: String = ""
  var uploadHistory: Map[UUID, (String, String, String)] = Map() // datasetId -> (dsName, uploadtime, uploaderId)
  var owner: Option[models.User] = None
  var created = (new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")).format(Calendar.getInstance.getTime)
  var updated = created

  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val users: UserService = DI.injector.getInstance(classOf[UserService])

  // Add a new dataset upload event to the uploadHistory
  def updateHistory(datasetId: UUID, uploaderId: String): Unit = {
    datasets.get(datasetId).map( ds => {
      val upTime = (new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")).format(Calendar.getInstance.getTime)
      uploadHistory(datasetId) = (ds.name, upTime, uploaderId)
    })
    updateTimestamp()
  }

  // Setter functions also update the updated timestamp
  def setID(externalid: String): Unit = {
    externalId = externalid
    updateTimestamp()
  }
  def setURL(instanceURL: String): Unit = {
    url = instanceURL
    updateTimestamp()
  }
  def setName(instanceName: String): Unit = {
    name = instanceName
    updateTimestamp()
  }
  def setOwner(ownerId: UUID): Unit = {
    users.get(ownerId) match {
      case Some(u) => owner = Some(u)
      case None => owner = None
    }
    updateTimestamp()
  }
  def setToolInfo(toolpath: String, toolname: String): Unit = {
    toolPath = toolpath
    toolName = toolname
    updateTimestamp()
  }

  def setTimes(createTime: String, updateTime: String): Unit = {
    created = createTime
    updated = updateTime
  }

  def updateTimestamp(): Unit = {
    updated = (new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")).format(Calendar.getInstance.getTime)
  }
}

/**
  * ToolManager plugin.
  * This manages ToolInstances(), each describing a running tool/analysis environment/VM that was launched
  * from Clowder. Supports launching, stopping, getting info of analysis environment sessions.
  */
class ToolManagerPlugin(application: Application) extends Plugin {
  var toolList: JsObject = JsObject(Seq[(String, JsValue)]()) // ToolAPIEndpoint -> {"name": <>, "description": <>}
  var instanceMap: Map[UUID, ToolInstance] = Map() // ToolManager SessionId -> ToolInstance instance

  val comments: CommentService = DI.injector.getInstance(classOf[CommentService])
  val files: FileService = DI.injector.getInstance(classOf[FileService])
  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])

  //TODO: decide on terminology. API has "tools" and "instances" - should we have Instance Manager? consistent naming!

  override def onStart() {
    Logger.debug("Initializing ToolManagerPlugin")
    refreshLaunchableToolsFromServer()
    refreshActiveInstanceListFromServer()
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
    val apipath = play.Play.application().configuration().getString("toolmanagerURI")
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
    * Update instanceMap with server-side list, in case Plugin has been stopped
    */
  def refreshActiveInstanceListFromServer(): Unit = {
    val apipath = play.Play.application().configuration().getString("toolmanagerURI")
    val statusRequest: Future[Response] = url(apipath+"/instances").get()

    statusRequest.map( response => {
      val jsonObj = Json.parse(response.body)

      jsonObj match {
        case _: JsUndefined => {}
        case j: JsObject => {
          for (externalID <- j.keys) {
            // check to make sure this externalID isn't already present in instanceMap
            var alreadyPresent = false
            for (existingUUID <- instanceMap.keys) {
              if (instanceMap(existingUUID).externalId == externalID) {
                alreadyPresent = true
              }
            }

            // if not, create it
            if (!alreadyPresent) {
              val instance = new ToolInstance()
              instance.externalId = externalID

              // TODO: how to get JsValue without the wrapping quotes?
              instance.setName((j \ externalID \ "name").toString.replace("\"",""))
              instance.setURL((j \ externalID \ "url").toString.replace("\"",""))
              instance.setOwner(new UUID((j \ externalID \ "ownerId").toString.replace("\"","")))
              instance.setToolInfo(
                (j \ externalID \ "toolPath").toString.replace("\"",""),
                (j \ externalID \ "toolName").toString.replace("\"",""))

              // TODO: normalize time zone - server time is currently shown as-received
              val t = (j \ externalID \ "created").toString.replace("\"","")
              instance.setTimes(t, t)

              val histList = (j \ externalID \ "uploadHistory").as[List[JsObject]]
              for (histEntry <- histList) {
                val entryTime = (histEntry \ "time").toString.replace("\"","")
                val entryUrl = (histEntry \ "url").toString.replace("\"","")
                val entryOwner = (histEntry \ "uploaderId").toString.replace("\"","")
                val entryId = (histEntry \ "datasetId").toString.replace("\"","")
                val entryName = (histEntry \ "datasetName").toString.replace("\"","")
                instance.uploadHistory(new UUID(entryId)) = (entryName, entryTime, entryOwner)
              }

              instanceMap(instance.id) = instance
            }
          }
        }
      }
    })
  }

  /**
    * Send request to API to launch a new tool.
    * @param hostURL is url of API server
    * @param instanceName user-provided name of Session to display
    * @param datasetId clowder ID of dataset to attach
    * @param toolPath name of environment type that is being launched
    * @return ID of session that was launched
    */
  def launchTool(hostURL: String, instanceName: String, toolPath: String, datasetId: UUID, datasetName: String, ownerId: Option[UUID]): UUID = {
    // Generate a new session & add to instanceMap
    val instance = new ToolInstance()
    instance.setName(instanceName)
    val toolName = (toolList \ toolPath \ "name") match {
      case j: JsUndefined => "Unknown"
      case j: JsString => j.toString.replace("\"","")
    }
    instance.setToolInfo(toolPath, toolName)
    var oId = ""
    ownerId.map( i => {
      instance.setOwner(i)
      oId = i.toString
    })
    instanceMap(instance.id) = instance

    // Send request to API to launch Tool
    // TODO: Figure out something better than the key here
    val dsURL = hostURL+controllers.routes.Datasets.dataset(datasetId).url
    val apipath = play.Play.application().configuration().getString("toolmanagerURI") + "/instances/" + toolPath
    val statusRequest: Future[Response] = url(apipath).post(Json.obj(
      "dataset" -> (dsURL.replace("/datasets", "/api/datasets")+"/download"),
      "key" -> play.Play.application().configuration().getString("commKey"),
      "name" -> instanceName,
      "ownerId" -> oId,
      "datasetId" -> datasetId,
      "datasetName" -> datasetName
    ))
    instance.updateHistory(datasetId, oId)

    statusRequest.map( response => {
      val externalURL = (Json.parse(response.body) \ "URL")
      val externalID = (Json.parse(response.body) \ "id")

      externalURL match {
        case _: JsUndefined => {}
        case _ => {
          val matched = instanceMap(instance.id)
          matched.setURL(externalURL.toString)
          instanceMap(instance.id) = matched
        }
      }

      externalID match {
        case _: JsUndefined => {}
        case _ => {
          val matched = instanceMap(instance.id)
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
      instanceMap.get(instanceID).map( ts => {
        uploaded(instanceID) = ts
      })
    }

    return uploaded
  }

  /**
    * Get a simple map of instanceID to instanceName
    */
  def getInstances(): Map[String, String] = {
    refreshActiveInstanceListFromServer()

    val instances = Map[String, String]()
    for ((instanceID, instance) <- instanceMap) {
      instances(instanceID.toString)  = instance.name
    }
    return instances
  }

  /**
    * Upload dataset files to an existing instance.
    */
  def uploadDatasetToInstance(hostURL: String, instanceID: UUID, datasetId: UUID, datasetName: String, userId: Option[UUID]): Unit = {
    val dsURL = hostURL+controllers.routes.Datasets.dataset(datasetId).url
    var oId = ""
    userId.map(i => oId = i.toString)

    instanceMap.get(instanceID).map(instance => {
      instance.updateHistory(datasetId, oId)

      val apipath = play.Play.application().configuration().getString("toolmanagerURI") + "/instances/" + instance.toolPath

      val statusRequest: Future[Response] = url(apipath).put(Json.obj(
        "dataset" -> (dsURL.replace("/datasets", "/api/datasets")+"/download"),
        "key" -> play.Play.application().configuration().getString("commKey"),
        "id" -> instance.externalId.toString,
        "uploaderId" -> oId,
        "datasetId" -> datasetId,
        "datasetName" -> datasetName
      ))
      statusRequest.map( response => {
        Logger.info(response.body.toString)
      })
    })
  }

  /**
    * Terminate a running tool instance.
    * @param toolPath is the endpoint to issue DELETE request to
    * @param instanceID ID of ToolInstance to stop
    */
  def removeInstance(toolPath: String, instanceID: UUID): Unit = {
    val apipath = play.Play.application().configuration().getString("toolmanagerURI") + "/instances/" + toolPath

    instanceMap.get(instanceID).map( ts => {
      val instanceApiID = ts.externalId // External identifier on NDS api
      val statusRequest: Future[Response] = url(apipath+"?id="+instanceApiID.toString()).delete()
    })

    instanceMap = instanceMap - instanceID
  }

  override def onStop() {
    instanceMap = Map()
    Logger.info("ToolManagerPlugin has stopped")
  }

}
