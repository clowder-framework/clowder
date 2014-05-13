package controllers


import play.Logger
import scala.collection.JavaConversions.mapAsScalaMap
//import services.Services

import services._
import play.Logger
import scala.collection.JavaConversions.mapAsScalaMap
import edu.illinois.ncsa.isda.lsva.ImageMeasures
import edu.illinois.ncsa.isda.lsva.ImageDescriptors.FeatureType
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer
import util.DistancePriorityQueue
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current
import scala.collection.mutable.ArrayBuffer
import api.Permission
import javax.inject.Inject
import scala.concurrent.Future
import scala.Some
import api.WithPermission
import util.SearchResult
import models.UUID

/**
 * Text search.
 *
 * @author Luigi Marini
 */
class Search @Inject() (
  datasets: DatasetService,
  files: FileService,
  collections: CollectionService,
  queries: MultimediaQueryService,
  previews: PreviewService) extends SecuredController {

  /**
   * Search results.
   */
  def search(query: String) = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) => {
        Logger.debug("Searching for: " + query)
        var fileList = ListBuffer.empty[models.File]
        var datasetList = ListBuffer.empty[models.Dataset]
        var mapdatasetIds= new scala.collection.mutable.HashMap[String,(String,String)]
        if (query != "") {
          import play.api.Play.current
          val result = current.plugin[ElasticsearchPlugin].map { _.search("data", query) }
          result match {
            case Some(searchResponse) => {
              for (hit <- searchResponse.getHits().getHits()) {
                Logger.debug("Computing search result " + hit.getId())
                Logger.info("Fields: ")
                for ((key, value) <- mapAsScalaMap(hit.getFields())) {
                  Logger.info(value.getName + " = " + value.getValue())
                }
                if (hit.getType() == "file") {
                  files.get(UUID(hit.getId())) match {
                    case Some(file) =>{
                      Logger.debug("FILES:hits.hits._id: Search result found file " + hit.getId());
                      Logger.debug("FILES:hits.hits._source: Search result found dataset " + hit.getSource().get("datasetId"))
                      //Logger.debug("Search result found file " + hit.getId()); files += file
                       mapdatasetIds.put(hit.getId(), (hit.getSource().get("datasetId").toString(),hit.getSource.get("datasetName").toString))
                      fileList += file
                    }
                    case None => Logger.debug("File not found " + hit.getId())
                  }
                } else if (hit.getType() == "dataset") {
                Logger.debug("DATASETS:hits.hits._source: Search result found dataset " + hit.getSource().get("name"))
                  Logger.debug("DATASETS:Dataset.id="+hit.getId());
                  //Dataset.findOneById(new ObjectId(hit.getId())) match {
                   datasets.get(UUID(hit.getId())) match {
                    case Some(dataset) =>
                      Logger.debug("Search result found dataset" + hit.getId()); datasetList += dataset
                    case None => {
                      Logger.debug("Dataset not found " + hit.getId())
                  	Redirect(routes.Datasets.dataset(UUID(hit.getId)))            
                  	
                  }
                  }
                }
                Ok(views.html.searchResults(query, fileList.toArray, datasetList.toArray,mapdatasetIds))
              }
            }
            case None => {
              Logger.debug("Search returned no results")
            }
          }
        }
        Ok(views.html.searchResults(query, fileList.toArray, datasetList.toArray,mapdatasetIds))
      }
      case None => {
        Logger.debug("Search plugin not enabled")
        Ok(views.html.pluginNotEnabled("Text search"))
      }
    }

  }

  def multimediasearch() = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    Logger.debug("Starting multimedia search interface")
    Ok(views.html.multimediasearch())
  }

  /**
   * Search MultimediaFeatures.
   */
  def searchMultimediaIndex(section_id: UUID) = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    Logger.debug("Searching multimedia index")
    // TODO handle multiple previews found
    val preview = previews.findBySectionId(section_id)(0)
    queries.findFeatureBySection(section_id) match {
      case Some(feature) => {
        // setup priority queues
        val queues = new HashMap[String, DistancePriorityQueue]
        val representations = feature.features.map { f =>
          queues(f.representation) = new DistancePriorityQueue(20)
        }
        // push into priority queues
        feature.features.map { f =>
          Logger.debug("Computing " + f.representation)
          queries.listAll().map { mf =>
            Logger.trace("Found multimedia features " + mf.id + " for section " + section_id)
            mf.features.find(_.representation == f.representation) match {
              case Some(fd) => {
                val distance = ImageMeasures.getDistance(FeatureType.valueOf(fd.representation), f.descriptor.toArray, fd.descriptor.toArray)
                if (!distance.isNaN()) {
                  Logger.trace(f.representation + "/" + fd.representation + " Distance between " + feature.section_id + " and " + mf.id + " is " + distance)
                  queues.get(f.representation).map { q =>
                    val popped = q.insertWithOverflow(SearchResult(mf.section_id.get.toString, distance, None))
                    if (popped != null) Logger.trace("Popped distance off the queue " + popped)
                  }
                } else {
                  Logger.debug("Distance NaN " + f.descriptor + " and " + fd.descriptor + " is " + distance)
                }
              }
              case None =>
                Logger.error("Matching descriptor not found")
            }
          }
        }

        val items = new HashMap[String, ListBuffer[SearchResult]]
        queues map {
          case (key, queue) =>
            val list = new ListBuffer[SearchResult]
            while (queue.size > 0) {
              val element = queue.pop()
              val previewsBySection = previews.findBySectionId(UUID(element.section_id))
              if (previewsBySection.size == 1) {
                Logger.trace("Appended search result " + key + " " + element.section_id + " " + element.distance + " " + previewsBySection(0).id.toString)
                list.prepend(SearchResult(element.section_id, element.distance, Some(previewsBySection(0).id.toString)))
              } else {
                Logger.error("Found more/less than one preview " + preview)
              }
            }
            items += key -> list
        }

        Ok(views.html.searchMultimediaIndex(preview, items))
      }
      case None => InternalServerError("feature not found")
    }
  }

  def advanced() = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    Logger.debug("Starting Advanced Search interface")
    Ok(views.html.advancedsearch())
  }

  def SearchByText(query: String) = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    Logger.debug("Searching for" + query)
    Ok("")
  }
  

  /*
   * GET the query file from a URL and compare within the database and show the result   
   * */
  def searchbyURL(queryURL: String)=SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
   	Async{         
   	  current.plugin[VersusPlugin] match {    		
    	case Some(plugin)=>{      	  
    		val futureFutureListResults = for {
    			indexList<-plugin.getIndexesAsFutureList()
    		} yield { 	      				
    			val resultListOfFutures=indexList.map{
    				index=>    
    				  plugin.queryIndexForURL(queryURL, index.id).map{
    				    queryResult=>
    				      (index, queryResult)
    				  }    								  
    			}  	
    			//convert list of futures into a Future[list]
    			scala.concurrent.Future.sequence(resultListOfFutures)
           	}//End yield- outer for    	           
    		for{
    			futureListResults<-futureFutureListResults
    			listOfResults<-futureListResults   			    		
    		} yield {
    		   Ok(views.html.contentbasedSearchResultsVideo3(queryURL, listOfResults))     		
    		}              
        } //case some
                    
        case None => {
          Future(Ok("No Versus Service"))
        }
      } //match            
    } //Async
  }

  /** 
   * Finds similar objects(images, pdfs, etc) in Multiple index for a given file (file is NOT id db, just uploaded by user)
   **/
  def findSimilar(fileID:UUID)=SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request => 	
   	Async{ 
   		//query file will be stored in MultimediaQueryService
   		//in controllers/Files -> uploadSelectQuery
   		var contentTypeStr="";
   		queries.get(fileID) match {
   			//queries.get(UUID("535ff22ee4b09350e18e7a94")) match {
   			case Some((inputStream, filename, contentType, length)) => {                
   				contentTypeStr=contentType;
   				Logger.debug(" 232 file.contentType = " + contentType)
    
   				current.plugin[VersusPlugin] match {    		
   					case Some(plugin)=>{          	
   						val futureFutureListResults = for {    	  
   							//indexList<-plugin.getIndexesAsFutureList()
   							indexList<-plugin.getIndexesForContentTypeAsFutureList(contentTypeStr)
   						} yield { 	      				
   							val resultListOfFutures=indexList.map{
   								index=>    
   									plugin.queryIndexForNewFile(fileID, index.id).map{
   										queryResult=>(index, queryResult)
   									}    								  
   							}  	
   							//convert list of futures into a Future[list]
   							scala.concurrent.Future.sequence(resultListOfFutures)    			
   						}//End yield    		
    		
   						for{
   							futureListResults<-futureFutureListResults
   							listOfResults<-futureListResults      		
   						} yield {  
   							Ok(views.html.contentbasedSearchResultsVideo3(filename, listOfResults))             		
   						}    		            
   					} //end of case Some(plugin)   

   					case None => {
   						Future(Ok("No Versus Service"))
   					}
   				} //current.plugin[VersusPlugin] match  
   			}//case Some((inputStream...
   			
   			case None=>{
   				Logger.debug("235 no file found")
   				Future(Ok("File with id " +fileID +" not found"))
   			}
   		}//end of queries.get(imageID) match 
    } //Async
  }
  
  /**      
  * Finds similar objects(images, pdfs, etc) in Multiple index for a given file (file is already in db)
  *  
  **/
  def findSimilarFile(inputFileId:UUID)=SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
  	//
    //almost exact copy of findSimilar, calls plugin.queryIndexFile instead of plugin.queryIndex
  	//also towards the end, use "files.getFile(imageID)" instead of "queries.getFile(imageID)
  	//
  Async{       	   
  		//file will be stored in FileService
   	   	var contentTypeStr="";
   	   	//files.getBytes(UUID("535ff22ee4b09350e18e7a94")) match {
   	   	files.getBytes(inputFileId) match {
   	   		case Some((inputStream, filename, contentType, length)) => {  
   	   			contentTypeStr = contentType
   	   			current.plugin[VersusPlugin] match {    		
   	   				case Some(plugin)=>{      	  
   	   					val futureFutureListResults = for {
   	   						indexList<-plugin.getIndexesForContentTypeAsFutureList(contentTypeStr)
   	   					} yield { 	      				
   	   						val resultListOfFutures=indexList.map{
   	   							index=>    
   	   								plugin.queryIndexForExistingFile(inputFileId, index.id).map{
   	   									queryResult=>  (index, queryResult)
   	   								}    								  
   	   						}  	
   	   						//convert list of futures into a Future[list]
   	   						scala.concurrent.Future.sequence(resultListOfFutures)
   	   					}//End yield- outer for    	
    		
   	   					for{
   	   						futureListResults<-futureFutureListResults
   	   						listOfResults<-futureListResults      		
   	   					} yield {     			             
   	   						Ok(views.html.contentbasedSearchResultsVideo3(filename, listOfResults))          
   	   					}    		             
   	   				} //end of case Some(plugin)                   

   	   				case None => {
   	   					Future(Ok("No Versus Service"))
   	   				}
   	   			} //current.plugin[VersusPlugin] match  
   			}//case Some((inputStream...
   			
   			case None=>{
   				Logger.debug("235 no file found")
   				Future(Ok("Could not find similar for file id " +inputFileId ))
   			}
   		}//end of files.getBytes(inputFileId) match 
    } //Async
  }
     

//  def Filterby(id: String) = TODO

  def uploadquery() = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    request.body.file("picture").map { picture =>
      import java.io.File
      picture.ref.moveTo(new File("/tmp/picture"))
      Ok("File uploaded")
    }.getOrElse {
      Redirect(routes.Application.index).flashing(
        "error" -> "Missing file")
    }
  }
}
