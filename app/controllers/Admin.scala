package controllers

import javax.inject.{Inject, Singleton}

import api.{Permission, _}
import models.{Role, UUID, VersusIndexTypeName}
import play.api.Logger
import play.api.Play.current
import play.api.data.Forms._
import play.api.data.{Form, Forms}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import services._

import scala.collection.immutable._
import scala.concurrent.Future

/**
 * Administration pages.
 */
@Singleton
class Admin @Inject() (sectionIndexInfo: SectionIndexInfoService, userService: UserService, metadataService: MetadataService) extends SecuredController {

  def customize = ServerAdminAction { implicit request =>
    val theme = AppConfiguration.getTheme
    Logger.debug("Theme id " + theme)
    implicit val user = request.user
    Ok(views.html.admin.customize(theme,
      AppConfiguration.getDisplayName,
      AppConfiguration.getWelcomeMessage,
      AppConfiguration.getGoogleAnalytics,
      AppConfiguration.getAmplitudeApiKey))
  }

  def tos = ServerAdminAction { implicit request =>
    implicit val user = request.user
    val tosText = AppConfiguration.getTermsOfServicesTextRaw
    val tosHtml = if (AppConfiguration.isTermOfServicesHtml) {
      "checked"
    } else {
      ""
    }
    Ok(views.html.admin.tos(tosText, tosHtml))
  }

  def adminIndex = ServerAdminAction { implicit request =>
    implicit val user = request.user
    Ok(views.html.adminIndex())
  }

  def reindexFiles = PermissionAction(Permission.MultimediaIndexDocument) { implicit request =>
    Ok("Reindexing")
  }

  def sensors = ServerAdminAction { implicit request =>
    implicit val user = request.user
    Ok(views.html.sensors.admin(AppConfiguration.getSensorsTitle,
      AppConfiguration.getSensorTitle,
      AppConfiguration.getParametersTitle,
      AppConfiguration.getParameterTitle
    ))
  }

  /**
   * Gets the available Adapters from Versus
   */
  def getAdapters() = ServerAdminAction.async { implicit request =>
    current.plugin[VersusPlugin] match {
      case Some(plugin) => {
        val adapterListResponse = plugin.getAdapters()
        for {
          adapterList <- adapterListResponse
        } yield {
          Ok(adapterList.json)
        }
      }

      case None => {
        Future(Ok("No Versus Service"))
      }
    }
  }

  /**
   * Gets all the distinct types of sections that are getting indexes (i.e. 'face', 'census')
   */
  def getSections() = ServerAdminAction { implicit request =>
    val types = sectionIndexInfo.getDistinctTypes
    val json = Json.toJson(types)
    Ok(json)
  }

  /**
   * Gets available extractors from Versus
   */
  def getExtractors() = ServerAdminAction.async { implicit request =>
    current.plugin[VersusPlugin] match {
      case Some(plugin) => {
        val extractorListResponse = plugin.getExtractors()
        for {
          extractorList <- extractorListResponse
        } yield {
          Ok(extractorList.json)
        }
      }

      case None => {
        Future(Ok("No Versus Service"))
      }
    }
  }

  /**
   * Gets available Measures from Versus
   */
  def getMeasures() = ServerAdminAction.async { implicit request =>
    current.plugin[VersusPlugin] match {
      case Some(plugin) => {
        val measureListResponse= plugin.getMeasures()
        for {
          measureList <- measureListResponse
        } yield {
          Ok(measureList.json)
        }
      }

      case None => {
        Future(Ok("No Versus Service"))
      }
    }
  }

  /**
   * Gets available Indexers from Versus
   */
  def getIndexers() = ServerAdminAction.async { implicit request =>
    current.plugin[VersusPlugin] match {
      case Some(plugin) => {
        val indexerListResponse = plugin.getIndexers()
        for {
          indexerList <- indexerListResponse
        } yield {
          Ok(indexerList.json)
        }
      }

      case None => {
        Future(Ok("No Versus Service"))
      }
    }
  }

  /**
   * Gets adapter, extractor, measure and indexer value and sends it to VersusPlugin to create index request to Versus.
   * If an index has type and/or name, stores type/name in mongo db.
   */
   def createIndex() = ServerAdminAction.async(parse.json) { implicit request =>
     current.plugin[VersusPlugin] match {
       case Some(plugin) => {
         Logger.trace("Contr.Admin.CreateIndex()")
         val adapter =    (request.body \ "adapter").as[String]
         val extractor =  (request.body \ "extractor").as[String]
         val measure =    (request.body \ "measure").as[String]
         val indexer =    (request.body \ "indexer").as[String]
         val indexType =  (request.body \ "indexType").as[String]
         val indexName =  (request.body \ "name").as[String]
         //create index and get its id
         val indexIdFuture: Future[models.UUID] = plugin.createIndex(adapter, extractor, measure, indexer)
         //save index type (census sections, face sections, etc) to the mongo db
         if (indexType != null && indexType.length !=0){
          indexIdFuture.map(sectionIndexInfo.insertType(_, indexType))
         }
         //save index name to the mongo db
         if (indexName != null && indexName.length !=0){
          indexIdFuture.map(sectionIndexInfo.insertName(_, indexName))
         }
          Future(Ok("Index created successfully"))
       }

       case None => {
         Future(Ok("No Versus Service"))
       }
     }
   }

  /**
   * Gets indexes from Versus, using VersusPlugin. Checks in mongo on clowder side if these indexes
   * have type and/or name. Adds type and/or name to json object and calls view template to display.
   */
  def getIndexes() = ServerAdminAction.async { implicit request =>
    current.plugin[VersusPlugin] match {
      case Some(plugin) => {
        Logger.trace(" Admin.getIndexes()")
        val indexListResponse = plugin.getIndexes()
        for {
          indexList <- indexListResponse
        } yield {
          if(indexList.body.isEmpty())
          {
            Ok(Json.toJson(""))
          }
          else{
            var finalJson: JsValue=null
            val jsArray = indexList.json
            //make sure we got correctly formatted list of values
            jsArray.validate[List[VersusIndexTypeName]].fold(
              // Handle the case for invalid incoming JSON.
              // Note: JSON created in Versus IndexResource.listJson must have the same names as clowder models.VersusIndexTypeName
              error => {
                Logger.error("Admin.getIndexes - validation error")
                InternalServerError("Received invalid JSON response from remote service.")
                },

                // Handle a deserialized array of List[VersusIndexTypeName]
                indexes => {
                  val indexesWithNameType = indexes.map{
                    index=>
                        //check in mongo for name/type of each index
                      val indType = sectionIndexInfo.getType(UUID(index.indexID)).getOrElse("")
                      val indName = sectionIndexInfo.getName(UUID(index.indexID)).getOrElse("")
                      //add type/name to index
                      VersusIndexTypeName.addTypeAndName(index, indType, indName)
                  }
                  indexesWithNameType.map(i=> Logger.debug("Admin.getIndexes index with name = " + i))
                  // Serialize as JSON, requires the implicit `format` defined earlier in VersusIndexTypeName
                  finalJson = Json.toJson(indexesWithNameType)
                }
              ) //end of fold
              Ok(finalJson)
          }
        }
      }

      case None => {
        Future(Ok("No Versus Service"))
      }
    }
  }

  /**
   * Builds a specific index in Versus
   */
  def buildIndex(id: String) = ServerAdminAction.async { implicit request =>
    Logger.trace("Inside Admin.buildIndex(), index = " + id)
    current.plugin[VersusPlugin] match {
      case Some(plugin) => {
      val buildResponse = plugin.buildIndex(UUID(id))
        for {
          buildRes <- buildResponse
        } yield {
          Ok(buildRes.body)
        }
      }

      case None => {
        Future(Ok("No Versus Service"))
      }
    }
  }

  /**
   * Deletes a specific index in Versus
   */
  def deleteIndex(id: String) = ServerAdminAction.async { implicit request =>
    current.plugin[VersusPlugin] match {
      case Some(plugin) => {
        val deleteIndexResponse= plugin.deleteIndex(UUID(id))
        for{
          deleteIndexRes<-deleteIndexResponse
        } yield {
         Ok(deleteIndexRes.body)
        }
      }

      case None => {
        Future(Ok("No Versus Service"))
      }
		}
  }

  /**
   * Deletes all indexes in Versus
   */
  def deleteAllIndexes() = ServerAdminAction.async { implicit request =>
    current.plugin[VersusPlugin] match {
      case Some(plugin) => {
        val deleteAllResponse = plugin.deleteAllIndexes()
        for {
          deleteAllRes <- deleteAllResponse
        } yield {
          Ok(deleteAllRes.body)
        }
      }

      case None => {
        Future(Ok("No Versus Service"))
      }
    }
  }

  private def getPermissionsMap(): scala.collection.immutable.Map[String, Boolean] = {
    var permissionMap = SortedMap.empty[String, Boolean]
    Permission.values.map {
      permission => permissionMap += (permission.toString().replaceAll("(\\p{Ll})(\\p{Lu})", "$1 $2") -> false)
    }
    return permissionMap
  }

  def listRoles() = ServerAdminAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(x) => {
        Ok(views.html.roles.listRoles(userService.listRoles().sortWith(_.name.toLowerCase < _.name.toLowerCase)))
      }
    }
  }

  def getMetadataDefinitions() = ServerAdminAction { implicit request =>
    implicit val user = request.user
    val metadata = metadataService.getDefinitions()
    Ok(views.html.manageMetadataDefinitions(metadata.toList, None, None))
  }

  val roleForm = Form(
    mapping(
      "id" -> optional(Utils.CustomMappings.uuidType),
      "name" -> nonEmptyText,
      "description" -> nonEmptyText,
      "permissions" -> Forms.list(nonEmptyText)
    )(roleFormData.apply)(roleFormData.unapply)
  )

  def createRole() = ServerAdminAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(x) => {
        Ok(views.html.roles.createRole(roleForm, getPermissionsMap()))
      }
    }
  }

  def viewDumpers() = ServerAdminAction { implicit request =>
  	implicit val user = request.user
	  Ok(views.html.viewDumpers())
  }

  def submitCreateRole() = ServerAdminAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(x) => {
        roleForm.bindFromRequest.fold(
          errors =>
            BadRequest(views.html.roles.createRole(errors, getPermissionsMap())),
          formData => {
            Logger.debug("Creating role " + formData.name)
            var permissionsSet: SortedSet[String] = SortedSet.empty
            formData.permissions.map {
              permission => permissionsSet += permission.toString().replace(" ", "")
            }
            val role = Role(name = formData.name, description = formData.description, permissions = permissionsSet)
            userService.addRole(role)
            Redirect(routes.Admin.listRoles()).flashing("success" -> "Role created")
          }
        )
      }
    }
  }

  def removeRole(id: UUID) = ServerAdminAction { implicit request =>
    deleteRoleHelper(id, request)
  }

  def deleteRoleHelper(id: UUID, request: UserRequest[AnyContent]) = {
    userService.findRole(id.stringify) match {
      case Some(role) => {
        userService.deleteRole(id.stringify)
        Redirect(routes.Admin.listRoles()).flashing("success" -> "Role removed")
      }
      case None =>  Redirect(routes.Admin.listRoles()).flashing("success" -> "Role removed")
    }
  }

  def editRole(id: UUID) = ServerAdminAction { implicit request =>
    implicit val user = request.user
    userService.findRole(id.stringify) match {
      case Some(s) => {
        var permissionMap = SortedMap.empty[String, Boolean]
        var permissionsSet: SortedSet[String] = SortedSet.empty
        Permission.values.map{
          permission =>
            if(s.permissions.contains(permission.toString))
            {
              permissionMap += (permission.toString().replaceAll("(\\p{Ll})(\\p{Lu})","$1 $2") -> true)
            }
            else {
              permissionMap += (permission.toString().replaceAll("(\\p{Ll})(\\p{Lu})","$1 $2") -> false)
            }
            permissionsSet += permission.toString().replace(" ", "")
        }
        Ok(views.html.roles.editRole(roleForm.fill(roleFormData(Some(s.id), s.name, s.description, permissionsSet.toList)), permissionMap))
      }
      case None => InternalServerError("Role not found")
    }
  }

  def updateRole() = ServerAdminAction { implicit request =>
    implicit val user = request.user

      roleForm.bindFromRequest.fold(
        errors => BadRequest(views.html.roles.editRole(errors, getPermissionsMap())),
        formData =>
        {
          Logger.debug("Updating role " + formData.name)
          userService.findRole(formData.id.get.toString) match {
            case Some(role) =>
            {
              //The form data is coming in with the permissions containing spaces. Need to collapse all spaces in order for the permissions to be correctly updated.
              val updated_role = role.copy(name = formData.name  , description = formData.description, permissions = formData.permissions.map(_.trim().replaceAll("\\s+", "")).toSet )
              userService.updateRole(updated_role)
              Redirect(routes.Admin.listRoles())
            }
            case None =>
              BadRequest("The role does not exist")
          }
        }
      )
  }

  def users() = ServerAdminAction { implicit request =>
    implicit val user = request.user

    val configAdmins = play.Play.application().configuration().getString("initialAdmins").trim.split("\\s*,\\s*").filter(_ != "").toList
    val users = userService.list.sortWith(_.lastName.toLowerCase() < _.lastName.toLowerCase())
    Ok(views.html.admin.users(configAdmins, users))
  }
}

case class roleFormData(id: Option[UUID], name: String, description: String, permissions: List[String])
