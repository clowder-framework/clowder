package api

import securesocial.core.Authorization
import securesocial.core.Identity
import play.api.mvc.WrappedRequest
import play.api.mvc.Request
import models.AppConfiguration
import services.AppConfigurationService
import models.UUID
import services.FileService
import services.DatasetService
import services.CollectionService
import play.api.Logger

 /**
  * A request that adds the User for the current call
  */
case class RequestWithUser[A](user: Option[Identity], request: Request[A]) extends WrappedRequest(request)

/**
 * List of all permissions available in Medici
 * 
 * @author Rob Kooper
 */
object Permission extends Enumeration {
	type Permission = Value
	val Public,					// Page is public accessible, i.e. no login needed 
		Admin,
		CreateCollections,
		DeleteCollections,
		ListCollections,
		ShowCollection,
		CreateDatasets,
		DeleteDatasets,
		ListDatasets,
		ShowDataset,
		SearchDatasets,
		AddDatasetsMetadata,
		ShowDatasetsMetadata,
		CreateTagsDatasets,
		DeleteTagsDatasets,
		CreateComments,
		AddSections,
		GetSections,
		CreateTagsSections,
		DeleteTagsSections,
		CreateFiles,
		DeleteFiles,
		ListFiles,
		AddFilesMetadata,
		ShowFilesMetadata,
		ShowFile,
		SearchFiles,
		CreateTagsFiles,
		DeleteTagsFiles,
		CreateStreams,
		AddDataPoints,
		SearchStreams,
		AddZoomTile,
		Add3DTexture,
		AddIndex,
		CreateSensors,
		ListSensors,
		GetSensors,
		SearchSensors,
		RemoveSensors,
		AddThumbnail,
		DownloadFiles = Value
}

import api.Permission._

/**
 * Specific implementation of an Authorization
 * 
 * @author Rob Kooper
 */
case class WithPermission(permission: Permission) extends Authorization {
  
  val appConfiguration: AppConfigurationService = services.DI.injector.getInstance(classOf[AppConfigurationService])
  val files: FileService = services.DI.injector.getInstance(classOf[FileService])
  val datasets: DatasetService = services.DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService = services.DI.injector.getInstance(classOf[CollectionService])
  
  def isAuthorized(user: Identity): Boolean = {
    isAuthorized(user, None)
  }

	def isAuthorized(user: Identity, resourceId: Option[UUID] = None): Boolean = {
	  
		// order is important
		(user, permission) match {
		  		  
		  // anybody can list/show
		  case (_, Public)               => true
		  case (_, ListCollections)      => true
		  case (_, ShowCollection)       => true
		  case (_, ListDatasets)         => true
		  case (_, ShowDataset)          => true
		  case (_, SearchDatasets)       => true
		  case (_, SearchFiles)	         => true
		  case (_, GetSections)          => true
		  case (_, ListFiles)            => true
		  case (_, ShowFile)             => true
		  case (_, ShowFilesMetadata)    => true
		  case (_, ShowDatasetsMetadata) => true
		  case (_, SearchStreams)        => true
		  case (_, ListSensors)          => true
		  case (_, GetSensors)           => true
		  case (_, SearchSensors)        => true
		  case (_, DownloadFiles)        => true
		  
		  // all other permissions require authenticated user
		  case (null, _)                 => false
		  case(_, Permission.Admin) =>{
		    if(!user.email.isEmpty)
		    	if(appConfiguration.adminExists(user.email.get))
		    	  true
		    	else
		    	  false  
		    else	  
		    	false	  
		  }
		  //Only authors of a resource-or admins-can modify a particular resource from the browser
		  case (_, requestedPermission)  =>{
		    resourceId match{
		      case Some(idOfResource) => {
		        if(requestedPermission == CreateFiles || requestedPermission == DeleteFiles || requestedPermission == AddFilesMetadata){
		          files.get(idOfResource) match{
		            case Some(file)=>{
		              if(file.author.identityId.userId.equals(user.identityId.userId))
		                true
		              else
		                appConfiguration.adminExists(user.email.getOrElse("none"))
		            }
		            case _ =>{
		              Logger.error("File requested to be accessed not found. Denying request.")
		              false
		            }
		          }
		        }
		        else if(requestedPermission == CreateDatasets || requestedPermission == DeleteDatasets || requestedPermission == AddDatasetsMetadata){
		          datasets.get(idOfResource) match{
		            case Some(dataset)=>{
		              if(dataset.author.identityId.userId.equals(user.identityId.userId))
		                true
		              else
		                appConfiguration.adminExists(user.email.getOrElse("none"))
		            }
		            case _ =>{
		              Logger.error("Dataset requested to be accessed not found. Denying request.")
		              false
		            }
		          }
		        }
		        else if(requestedPermission == CreateCollections || requestedPermission == DeleteCollections){
		          collections.get(idOfResource) match{
		            case Some(collection)=>{
		              collection.author match{
		                case Some(collectionAuthor)=>{
		                  if(collectionAuthor.identityId.userId.equals(user.identityId.userId))
		                	  true
		                  else
		                	  appConfiguration.adminExists(user.email.getOrElse("none"))
		                }
		                //Anonymous collections are free-for-all
		                case None=>{
		                  Logger.info("Requested collection is anonymous, anyone can modify, granting modification request.")
		                  true
		                }
		              }		              
		            }
		            case _ =>{
		              Logger.error("Collection requested to be accessed not found. Denying request.")
		              false
		            }
		          }
		        }
		        else{
		          true
		        }
		      }
		      case _ =>
		      	true
		    }
		  }
		}
	}
}

