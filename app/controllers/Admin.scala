package controllers

import play.api.mvc.{Results, Controller, Action}
import play.api.Routes
import securesocial.core.SecureSocial._
import securesocial.core.{IdentityProvider, SecureSocial}
import api.{ApiController, WithPermission, Permission}
import services.{AppConfigurationService, AppAppearanceService, SectionIndexInfoService, VersusPlugin}
import securesocial.core.providers.utils.RoutesHelper
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.concurrent.Promise
import play.api.libs.json._
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.libs.ws.{WS, Response}
import models.{AppConfiguration, AppAppearance, VersusIndexTypeName, UUID}
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import javax.inject.{Inject, Singleton}
import scala.concurrent._

/**
 * Administration pages.
 *
 * @author Luigi Marini
 *
 */
@Singleton
class Admin @Inject() (appConfiguration: AppConfigurationService, appAppearance: AppAppearanceService,
                       sectionIndexInfo: SectionIndexInfoService) extends SecuredController {

  private val themes = "bootstrap/bootstrap.css" ::
    "bootstrap-amelia.min.css" ::
    "bootstrap-simplex.min.css" :: Nil

  def main = SecuredAction(authorization = WithPermission(Permission.Admin)) { request =>
    val themeId = themes.indexOf(getTheme)
    Logger.debug("Theme id " + themeId)
    val appAppearanceGet = appAppearance.getDefault.get
    val appConfigGet = appConfiguration.getDefault.get
    implicit val user = request.user
    Ok(views.html.admin(themeId, appAppearanceGet, appConfigGet))
  }
  
  def adminIndex = SecuredAction(authorization = WithPermission(Permission.Admin)) { request =>
    val themeId = themes.indexOf(getTheme)
    val appAppearanceGet = appAppearance.getDefault.get
    implicit val user = request.user
    Ok(views.html.adminIndex(themeId, appAppearanceGet))
  }

  def reindexFiles = SecuredAction(parse.json, authorization = WithPermission(Permission.AddIndex)) { request =>
    Ok("Reindexing")
  }

  def test = SecuredAction(parse.json, authorization = WithPermission(Permission.Public)) { request =>
    Ok("""{"message":"test"}""").as(JSON)
  }

  def secureTest = SecuredAction(parse.json, authorization = WithPermission(Permission.Admin)) { request =>
    Ok("""{"message":"secure test"}""").as(JSON)
  }

  //get the available Adapters from Versus
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

          } //case some

          case None => {
            Future(Ok("No Versus Service"))
          }
        } //match

      } //Async

  }

  // Get available extractors from Versus
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
            //Ok(adapterListResponse)

          } //case some

          case None => {
            Future(Ok("No Versus Service"))
          }
        } //match

      } //Async

  }
  
  //Get available Measures from Versus 
  def getMeasures() = SecuredAction(authorization=WithPermission(Permission.Admin)){
     request =>
      
    Async{  
    	current.plugin[VersusPlugin] match {
     
        case Some(plugin)=>{
        	 
        	var measureListResponse= plugin.getMeasures()
        	 
        	for{
        	  measureList<-measureListResponse
        	}yield{
        	 Ok(measureList.json)
        	}
        	 //Ok(adapterListResponse)
        	         
            }//case some
         
		 case None=>{
		      Future(Ok("No Versus Service"))
		       }     
		 } //match
    
   } //Async
        
  }

  //Get available Indexers from Versus 
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

          } //case some

          case None => {
            Future(Ok("No Versus Service"))
          }
        } //match

      } //Async

  }

  /**
   * Get adapter, extractor,measure and indexer value and send it to VersusPlugin to send a create index request to Versus
   * If an index has type and/or name, store them in mongo db.
   * 
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
           } //end of case some plugin

           case None => {
             Future(Ok("No Versus Service"))
           }
         } //match

       } //Async
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
            		var res :JsValue= Json.toJson("")  
            		Ok(res)
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
                		    	Logger.debug("Admin.getIndexes indexes received = " + indexes)   								  
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
          } //case some

          case None => {
            Future(Ok("No Versus Service"))
          }
        } //match
      } //Async
  }
 

  //build a specific index in Versus
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
          } //case some

          case None => {
            Future(Ok("No Versus Service"))
          }
        } //match

      }

  }
  
  //Delete a specific index in Versus
  def deleteIndex(id: String)=SecuredAction(authorization=WithPermission(Permission.Admin)){
    request =>
    Async{  
      current.plugin[VersusPlugin] match {
     
        case Some(plugin)=>{
        	 
        	var deleteIndexResponse= plugin.deleteIndex(UUID(id))
        	 
        	for{
        	  deleteIndexRes<-deleteIndexResponse
        	}yield{
        	 Ok(deleteIndexRes.body)
        	}
        	 
        	         
            }//case some
         
		 case None=>{
		      Future(Ok("No Versus Service"))
		       }     
		 } //match
    
    }
  }

  //Delete all indexes in Versus

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

          } //case some

          case None => {
            Future(Ok("No Versus Service"))
          }
        } //match

      }
  }
  
  def setTheme() = SecuredAction(parse.json, authorization = WithPermission(Permission.Admin)) { implicit request =>
    request.body.\("theme").asOpt[Int] match {
      case Some(theme) => {
        appConfiguration.setTheme(themes(theme))
        Ok("""{"status":"ok"}""").as(JSON)
      }
      case None => {
        Logger.error("no theme specified")
        BadRequest
      }
    }
  }

def getTheme(): String = appConfiguration.getTheme()
  
  val adminForm = Form(
  single(
    "email" -> email
  )verifying("Admin already exists.", fields => fields match {
     		case adminMail => !appConfiguration.adminExists(adminMail)
     	})
)
  
  def newAdmin()  = SecuredAction(authorization=WithPermission(Permission.UserAdmin)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.newAdmin(adminForm))
  }
  
  def submitNew() = SecuredAction(authorization=WithPermission(Permission.UserAdmin)) { implicit request =>
    implicit val user = request.user

    adminForm.bindFromRequest.fold(
      errors => BadRequest(views.html.newAdmin(errors)),
      newAdmin => {
        appConfiguration.addAdmin(newAdmin)
        Redirect(routes.Admin.listAdmins)
      }
    )
  }
  
  def listAdmins() = SecuredAction(authorization=WithPermission(Permission.UserAdmin)) { implicit request =>
    implicit val user = request.user

    appConfiguration.getDefault match {
      case Some(conf) => Ok(views.html.listAdmins(conf.admins))
      case None => {
        Logger.error("Error getting application configuration!");
        InternalServerError
      }
    }
  }

}
