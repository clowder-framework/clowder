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
import play.api.Play.configuration
import play.api.{Plugin, Logger, Application}

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
		PublicOpen,				//Page always accessible
		Admin,
		CreateCollections,
		DeleteCollections,
		EditCollection,
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
		ShowTags,
		UpdateDatasetInformation,
		UpdateLicense,
		CreateComments,
		RemoveComments,
		EditComments,
		CreateNotes,
		AddSections,
		GetSections,
		CreateTagsSections,
		DeleteTagsSections,
		CreateFiles,
		DeleteFiles,
		ListFiles,
		ExtractMetadata,
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
	  
	  	val permissionsShow = configuration(play.api.Play.current).getString("permissions").getOrElse("private").equals("public") || user != null
	  
		// order is important
		(user, permission) match {
		  		  
		  // anybody can list/show
		  case (_, PublicOpen)           => true	
		  case (_, Public)               => permissionsShow 
		  case (_, ListCollections)      => permissionsShow
		  case (_, ShowCollection)       => permissionsShow
		  case (_, ListDatasets)         => permissionsShow
		  case (_, ShowDataset)          => permissionsShow
		  case (_, SearchDatasets)       => permissionsShow
		  case (_, SearchFiles)	         => permissionsShow
		  case (_, GetSections)          => permissionsShow
		  case (_, ListFiles)            => permissionsShow
		  case (_, ShowFile)             => permissionsShow
		  case (_, ShowFilesMetadata)    => permissionsShow
		  case (_, ShowDatasetsMetadata) => permissionsShow
		  case (_, SearchStreams)        => permissionsShow
		  case (_, ListSensors)          => permissionsShow
		  case (_, GetSensors)           => permissionsShow
		  case (_, SearchSensors)        => permissionsShow
		  case (_, DownloadFiles)        => permissionsShow
		  
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

