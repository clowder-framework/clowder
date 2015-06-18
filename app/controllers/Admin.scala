package controllers

import api.{WithPermission, Permission}
import models.{Role, UUID, VersusIndexTypeName}
import services.{UserService, SectionIndexInfoService, AppConfiguration, VersusPlugin}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{Json, JsValue}
import play.api.Logger
import scala.concurrent.Future
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import scala.collection.{mutable, Map}
import play.api.data.{Forms, Form}
import scala.collection.immutable._
import api._
import play.api.mvc._
/**
 * Administration pages.
 *
 * @author Luigi Marini
 * @author Inna Zharnitsky
 *
 */
@Singleton
class Admin @Inject() (sectionIndexInfo: SectionIndexInfoService, userService: UserService) extends SecuredController {

  def main = SecuredAction(authorization = WithPermission(Permission.Admin)) { request =>
    val theme = AppConfiguration.getTheme
    Logger.debug("Theme id " + theme)
    implicit val user = request.user
    Ok(views.html.admin(theme, AppConfiguration.getDisplayName, AppConfiguration.getWelcomeMessage))
  }
  
  def adminIndex = SecuredAction(authorization = WithPermission(Permission.Admin)) { request =>
    implicit val user = request.user
    Ok(views.html.adminIndex())
  }
  
  def reindexFiles = SecuredAction(parse.json, authorization = WithPermission(Permission.MultimediaIndexDocument)) { request =>
    Ok("Reindexing")
  }

  def test = SecuredAction(parse.json, authorization = WithPermission(Permission.Public)) { request =>
    Ok("""{"message":"test"}""").as(JSON)
  }
  
  def secureTest = SecuredAction(parse.json, authorization = WithPermission(Permission.Admin)) { request =>
    Ok("""{"message":"secure test"}""").as(JSON)
  }
 
  /**
   * Gets the available Adapters from Versus
   */
  def getAdapters() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>
      Async {
        current.plugin[VersusPlugin] match {
          case Some(plugin) => {
            var adapterListResponse = plugin.getAdapters()
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
  }
  
  /**
   * Gets all the distinct types of sections that are getting indexes (i.e. 'face', 'census')
   */
  def getSections() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request=>
        val types = sectionIndexInfo.getDistinctTypes
		val json = Json.toJson(types)	    
        Ok(json)
  }
 
  /**
   * Gets available extractors from Versus
   */
  def getExtractors() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>
      Async {
        current.plugin[VersusPlugin] match {
          case Some(plugin) => {
            var extractorListResponse = plugin.getExtractors()
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
  }
  
  /**
   * Gets available Measures from Versus
   */ 
  def getMeasures() = SecuredAction(authorization=WithPermission(Permission.Admin)){
     request =>      
     	Async{  
     	  current.plugin[VersusPlugin] match {     
     		case Some(plugin)=>{        	 
     			var measureListResponse= plugin.getMeasures()        	 
     			for{
     				measureList<-measureListResponse
     			} yield {
     				Ok(measureList.json)
     			}        	         
     		}
         
     		case None=>{
     			Future(Ok("No Versus Service"))
		    }     
     	  }    
     	}         
  	}

  /**
   * Gets available Indexers from Versus
   */ 
  def getIndexers() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>
      Async {
        current.plugin[VersusPlugin] match {
          case Some(plugin) => {
            var indexerListResponse = plugin.getIndexers()
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
  }

  /**
   * Gets adapter, extractor,measure and indexer value and sends it to VersusPlugin to create index request to Versus.
   * If an index has type and/or name, stores type/name in mongo db.
   */ 
   def createIndex() = SecuredAction(parse.json, authorization = WithPermission(Permission.Admin)) {
     implicit request =>
       Async {
         current.plugin[VersusPlugin] match {
           case Some(plugin) => {
             Logger.trace("Contr.Admin.CreateIndex()")
             val adapter = (request.body \ "adapter").as[String]
             val extractor = (request.body \ "extractor").as[String]
             val measure = (request.body \ "measure").as[String]
             val indexer = (request.body \ "indexer").as[String]
             val indexType = (request.body \ "indexType").as[String]      
             val indexName = (request.body \ "name").as[String]
             //create index and get its id
             val indexIdFuture :Future[models.UUID] = plugin.createIndex(adapter, extractor, measure, indexer)            
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
   }
   
  /**
   * Gets indexes from Versus, using VersusPlugin. Checks in mongo on Medici side if these indexes
   * have type and/or name. Adds type and/or name to json object and calls view template to display.
   */
  def getIndexes() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>
      Async {        
        current.plugin[VersusPlugin] match {
          case Some(plugin) => {
           Logger.trace(" Admin.getIndexes()")
            var indexListResponse = plugin.getIndexes()
            for {
              indexList <- indexListResponse
            } yield {
            	if(indexList.body.isEmpty())
            	{ 
            		Ok(Json.toJson(""))
            	}
                else{
                  var finalJson :JsValue=null
                  val jsArray = indexList.json
                  //make sure we got correctly formatted list of values
                  jsArray.validate[List[VersusIndexTypeName]].fold(
                		  // Handle the case for invalid incoming JSON.
                		  // Note: JSON created in Versus IndexResource.listJson must have the same names as Medici models.VersusIndexTypeName 
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
  }

  /**
   * Builds a specific index in Versus
   */
  def buildIndex(id: String) = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>
           Logger.trace("Inside Admin.buildIndex(), index = " + id)
      Async {
        current.plugin[VersusPlugin] match {
          case Some(plugin) => {			
        	var buildResponse = plugin.buildIndex(UUID(id))
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
  }
  
  /**
   * Deletes a specific index in Versus
   */
  def deleteIndex(id: String)=SecuredAction(authorization=WithPermission(Permission.Admin)){
    request =>
      Async{  
        current.plugin[VersusPlugin] match {     
          case Some(plugin)=>{
        	var deleteIndexResponse= plugin.deleteIndex(UUID(id))       	 
        	for{
        	  deleteIndexRes<-deleteIndexResponse
        	} yield {
        	 Ok(deleteIndexRes.body)
        	}        	         
          }
         
		  case None=>{
		    Future(Ok("No Versus Service"))
		  }     
		}     
      }
  }

  /**
   * Deletes all indexes in Versus
   */
  def deleteAllIndexes() = SecuredAction(authorization = WithPermission(Permission.Admin)) {
    request =>
      Async {
        current.plugin[VersusPlugin] match {        	
          case Some(plugin) => {
            var deleteAllResponse = plugin.deleteAllIndexes()
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
  }
  
  def setTheme() = SecuredAction(parse.json, authorization = WithPermission(Permission.Admin)) { implicit request =>
    request.body.\("theme").asOpt[String] match {
      case Some(theme) => {
        AppConfiguration.setTheme(theme)
        Ok("""{"status":"ok"}""").as(JSON)
      }
      case None => {
        Logger.error("no theme specified")
        BadRequest
      }
    }
  }

  val adminForm = Form(
  single(
    "email" -> email
  )verifying("Admin already exists.", fields => fields match {
     		case adminMail => !AppConfiguration.checkAdmin(adminMail)
     	})
)
  
  def newAdmin()  = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.newAdmin(adminForm))
  }
  
  def submitNew() = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(x) => {
        if (x.email.nonEmpty && AppConfiguration.checkAdmin(x.email.get)) {
          adminForm.bindFromRequest.fold(
            errors => BadRequest(views.html.newAdmin(errors)),
            newAdmin => {
              AppConfiguration.addAdmin(newAdmin)
              Redirect(routes.Admin.listAdmins())
            }
          )
        } else {
          Unauthorized("Not authorized")
        }
      }
    }
  }
  
  def listAdmins() = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(x) => {
        if (x.email.nonEmpty && AppConfiguration.checkAdmin(x.email.get)) {
          val admins = AppConfiguration.getAdmins
          Ok(views.html.listAdmins(admins))
        } else {
          Unauthorized("Not authorized")
        }
      }
    }
  }

  def getPermissionsMap() : scala.collection.immutable.Map[String, Boolean] = {
    var permissionMap = SortedMap.empty[String, Boolean]
    Permission.values.map {
      permission => permissionMap += (permission.toString().replaceAll("(\\p{Ll})(\\p{Lu})", "$1 $2") -> false)

    }
    return permissionMap;
  }

  def listRoles() = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(x) => {
        Ok(views.html.roles.listRoles(userService.listRoles().sortWith(_.name.toLowerCase < _.name.toLowerCase)))
      }
    }
  }

  val roleForm = Form(
    mapping(
    "id" -> optional(Utils.CustomMappings.uuidType),
      "name" -> nonEmptyText,
      "description" -> nonEmptyText,
      "permissions" -> Forms.list(nonEmptyText)
    )(roleFormData.apply)(roleFormData.unapply)
  )

  def createRole() = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(x) => {

        Ok(views.html.roles.createRole(roleForm, getPermissionsMap()))
      }
    }
  }

  def submitCreateRole() = SecuredAction(authorization=WithPermission(Permission.Admin)) { implicit request =>
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

  def removeRole(id: UUID) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.Admin), resourceId = Some(id)) {
    implicit request =>
    deleteRoleHelper(id, request)
  }

  def deleteRoleHelper(id: UUID, request: RequestWithUser[AnyContent]) = {
    userService.findRole(id.stringify) match {
      case Some(role) => {
        userService.deleteRole(id.stringify)
        Redirect(routes.Admin.listRoles()).flashing("success" -> "Role removed")
      }
      case None =>  Redirect(routes.Admin.listRoles()).flashing("success" -> "Role removed")
    }
  }

  def editRole(id: UUID) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.Admin), resourceId = Some(id))
  {
    implicit request =>
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

  def updateRole() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.Admin))
  {
    implicit request =>
      implicit val user = request.user

        roleForm.bindFromRequest.fold(
        errors => BadRequest(views.html.roles.editRole(errors, getPermissionsMap())),
        formData =>
        {
          Logger.debug("Updating role " + formData.name)
          userService.findRole(formData.id.get.toString) match {
            case Some(role) =>
            {
              val updated_role = role.copy(name = formData.name  , description = formData.description, permissions = formData.permissions.toSet )
              userService.updateRole(updated_role)
              Redirect(routes.Admin.listRoles())
            }
            case None =>
              BadRequest("The role does not exist")
          }
        })


  }

}

case class roleFormData(id: Option[UUID], name: String, description: String, permissions: List[String])