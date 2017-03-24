package controllers

import api.Permission
import models.{UUID, ResourceRef}
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json._
import services.{ToolInstance, ToolManagerPlugin}
import scala.collection.immutable._
import scala.collection.mutable.{Map => MutableMap}

/**
  * A dataset is a collection of files and streams.
  */
class ToolManager extends SecuredController {

  /**
    * With permission, prepare Tool Manager page with list of currently running tool instances.
    */
  def toolManager() = PermissionAction(Permission.ExecuteOnDataset) { implicit request =>
    implicit val user = request.user

    var instanceMap = MutableMap[UUID, ToolInstance]()
    var toolList: JsObject = JsObject(Seq[(String, JsValue)]())
    // Get mapping of instanceIDs to URLs API has returned
    current.plugin[ToolManagerPlugin].map( mgr => {
      mgr.refreshActiveInstanceListFromServer()
      toolList = mgr.toolList
      instanceMap = mgr.instanceMap
    })

    Ok(views.html.toolManager(toolList, instanceMap.keys.toList, instanceMap))
  }

  /**
    * Construct the sidebar listing active tools relevant to the given datasetId
    *
    * @param datasetId UUID of dataset that is currently displayed
    */
  def refreshToolSidebar(datasetId: UUID, datasetName: String) = PermissionAction(Permission.ExecuteOnDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    implicit val user = request.user

    // Get mapping of instanceIDs to returned URLs
    var instanceMap = MutableMap[UUID, ToolInstance]()
    // Get mapping of instanceID -> ToolInstance if datasetID is in uploadHistory
    current.plugin[ToolManagerPlugin].map( mgr => instanceMap = mgr.getInstancesWithDataset(datasetId))
    Ok(views.html.datasets.tools(instanceMap.keys.toList, instanceMap, datasetId, datasetName))
  }

  /**
    * Send request to ToolManagerPlugin to launch a new tool instance and upload datasetID.
    */
  def launchTool(instanceName: String, tooltype: String, datasetId: UUID, datasetName: String) = PermissionAction(Permission.ExecuteOnDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    implicit val user = request.user

    val hostURL = controllers.Utils.baseUrl(request)
    val userId: Option[UUID] = user match {
      case Some(u) => Some(u.id)
      case None => None
    }

    current.plugin[ToolManagerPlugin] match {
      case Some(mgr) => {
        val instanceID = mgr.launchTool(hostURL, instanceName, tooltype, datasetId, datasetName, userId)
        Ok(instanceID.toString)
      }
      case None => BadRequest("No ToolManagerPlugin found.")
    }
  }

  /**
    * Fetch list of launchable tools from Plugin.
    */
  def getLaunchableTools() = PermissionAction(Permission.ExecuteOnDataset) { implicit request =>
    implicit val user = request.user

    current.plugin[ToolManagerPlugin] match {
      case Some(mgr) => {
        val tools = mgr.getLaunchableTools()
        Ok(tools)
      }
      case None => BadRequest("No ToolManagerPlugin found.")
    }
  }

  /**
    * Upload a dataset to an existing tool instance. Does not check for or prevent against duplication.
    */
  def uploadDatasetToTool(instanceID: UUID, datasetID: UUID, datasetName: String) = PermissionAction(Permission.ExecuteOnDataset, Some(ResourceRef(ResourceRef.dataset, datasetID))) { implicit request =>
    implicit val user = request.user

    val hostURL = request.headers.get("Host").getOrElse("")
    val userId: Option[UUID] = user match {
      case Some(u) => Some(u.id)
      case None => None
    }

    current.plugin[ToolManagerPlugin] match {
      case Some(mgr) => {
        mgr.uploadDatasetToInstance(hostURL, instanceID, datasetID, datasetName, userId)
        Ok("request sent")
      }
      case None => BadRequest("No ToolManagerPlugin found.")
    }
  }

  /**
    * Get full list of running instances from Plugin.
    */
  def getInstances() = PermissionAction(Permission.ExecuteOnDataset) { implicit request =>
    implicit val user = request.user

    current.plugin[ToolManagerPlugin] match {
      case Some(mgr) => {
        val instances = mgr.getInstances()
        Ok(toJson(instances.toMap))
      }
      case None => BadRequest("No ToolManagerPlugin found.")
    }
  }

  /**
    * Get remote URL of running instance, if available.
    */
  def getInstanceURL(instanceID: UUID) = PermissionAction(Permission.ExecuteOnDataset) { implicit request =>
    implicit val user = request.user

    val url = current.plugin[ToolManagerPlugin] match {
      case Some(mgr) => mgr.checkForInstanceURL(instanceID)
      case None => ""
    }
    Ok(url)
  }

  /**
    * Send request to server to destroy instance, and remove from Plugin.
    */
  def removeInstance(toolPath: String, instanceID: UUID) = PermissionAction(Permission.ExecuteOnDataset) { implicit request =>
    implicit val user = request.user

    current.plugin[ToolManagerPlugin] match {
      case Some(mgr) => {
        mgr.removeInstance(toolPath, instanceID)
        Ok(instanceID.toString)
      }
      case None => BadRequest("No ToolManagerPlugin found.")
    }
  }
}