package controllers
//import play.api.Logger
import play.api.mvc._
import services.ElasticsearchPlugin
import services.VersusPlugin
import play.Logger
import scala.collection.JavaConversions.mapAsScalaMap
import services.Services
import sys.process._
import java.net.URL
import java.io.File
import scala.io.Source
import java.io._
import play.api.libs.ws.WS
import models.SectionDAO
import org.bson.types.ObjectId
import models.PreviewDAO
import models.MultimediaFeatures
import models.MultimediaFeaturesDAO
import com.mongodb.casbah.commons.MongoDBObject
import edu.illinois.ncsa.isda.lsva.ImageMeasures
import edu.illinois.ncsa.isda.lsva.ImageDescriptors.FeatureType
import scala.collection.mutable.LinkedList
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer
import util.DistancePriorityQueue
import util.DistancePriorityQueue
import org.elasticsearch.action.search.SearchRequest
import util.SearchResult
import util.DistancePriorityQueue
import play.api.libs.ws.WS
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import java.io.FileInputStream
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import scala.collection.mutable.ArrayBuffer
import models.Result
import models.SimilarityResult
import play.api.libs.json.Reads
import play.api.libs.json.JsArray
import models.TempFileDAO
import com.mongodb.DBCollection
import play.api.Play.current
import play.api.libs.concurrent._
import scala.concurrent.{ future, blocking, Future, Await }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable.ArrayBuffer
import models.Dataset
import api.WithPermission
import api.Permission

/**
 * Text search.
 *
 * @author Luigi Marini
 */
object Search extends SecuredController {

  /**
   * Search results.
   */
  def search(query: String) = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) => {
        Logger.debug("Searching for: " + query)
        var files = ListBuffer.empty[models.File]
        var datasets = ListBuffer.empty[models.Dataset]
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
                  Services.files.getFile(hit.getId()) match {
                    case Some(file) =>{
                      Logger.debug("FILES:hits.hits._id: Search result found file " + hit.getId());
                      Logger.debug("FILES:hits.hits._source: Search result found dataset " + hit.getSource().get("datasetId"))
                      //Logger.debug("Search result found file " + hit.getId()); files += file
                       mapdatasetIds.put(hit.getId(), (hit.getSource().get("datasetId").toString(),hit.getSource.get("datasetName").toString))
                      files += file
                    }
                    case None => Logger.debug("File not found " + hit.getId())
                  }
                } else if (hit.getType() == "dataset") {
                Logger.debug("DATASETS:hits.hits._source: Search result found dataset " + hit.getSource().get("name"))
                  Logger.debug("DATASETS:Dataset.id="+hit.getId());
                  //Dataset.findOneById(new ObjectId(hit.getId())) match {
                   Services.datasets.get(hit.getId()) match {
                    case Some(dataset) =>
                      Logger.debug("Search result found dataset" + hit.getId()); datasets += dataset
                    case None => {Logger.debug("Dataset not found " + hit.getId())
                  	Redirect(routes.Datasets.dataset(hit.getId)) 
                  }
                  }
                }
                Ok(views.html.searchResults(query, files.toArray, datasets.toArray,mapdatasetIds))
              }
            }
            case None => {
              Logger.debug("Search returned no results")
            }
          }
        }
        Ok(views.html.searchResults(query, files.toArray, datasets.toArray,mapdatasetIds))
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
    //Ok("Sucessful")
  }

  /**
   * Search MultimediaFeatures.
   */
  def searchMultimediaIndex(section_id: String) = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    Logger.debug("Searching multimedia index")
    // TODO handle multiple previews found
    val preview = PreviewDAO.findBySectionId(new ObjectId(section_id))(0)
    MultimediaFeaturesDAO.findOne(MongoDBObject("section_id" -> new ObjectId(section_id))) match {
      case Some(feature) => {
        // setup priority queues
        val queues = new HashMap[String, DistancePriorityQueue]
        val representations = feature.features.map { f =>
          queues(f.representation) = new DistancePriorityQueue(20)
        }
        // push into priority queues
        feature.features.map { f =>
          Logger.debug("Computing " + f.representation)
          MultimediaFeaturesDAO.find(MongoDBObject()).toList.map { mf =>
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
        //        // grab first 10 items from priority queues
        //        val items = new HashMap[String, ListBuffer[SearchResult]]
        //        for(i <- 1 to 10) {
        //          Logger.debug("test")
        //          queues.foreach { case (k,v) =>
        //            
        //            if (items.contains(k)) {
        //              if (v.size > 0) items(k) += v.pop
        //            } else {
        //              items += k -> (new ListBuffer() += v.pop)
        //            }: Unit // TODO fixed in scala 2.10 https://issues.scala-lang.org/browse/SI-4938
        //          }
        //           Logger.debug("test")
        //        }

        //        val queuesWithPreviews = queues map {case (key, queue) =>
        //          val newQueue = new DistancePriorityQueue(queue.size())
        //          while (queue.size > 0) {
        //            val element = queue.pop()
        //            val previews = PreviewDAO.findBySectionId(new ObjectId(element.id))
        //            if (previews.size == 1) {
        //              newQueue.add(SearchResult(element.id, element.distance, Some(previews(0).id.toString)))
        //            } else {
        //              Logger.error("Found more/less than one preview " + preview)
        //            }
        //          }
        //        }

        val items = new HashMap[String, ListBuffer[SearchResult]]
        queues map {
          case (key, queue) =>
            val list = new ListBuffer[SearchResult]
            while (queue.size > 0) {
              val element = queue.pop()
              val previews = PreviewDAO.findBySectionId(new ObjectId(element.section_id))
              if (previews.size == 1) {
                Logger.trace("Appended search result " + key + " " + element.section_id + " " + element.distance + " " + previews(0).id.toString)
                list.prepend(SearchResult(element.section_id, element.distance, Some(previews(0).id.toString)))
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

    //Ok(views.html.searchTextResults(query))
    Ok("")
  }

  //GET the query image from the URL and compare within the database and show the result
  def searchbyURL(query: String) = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    Logger.debug("Searching for" + query)
    var slashindex = query.lastIndexOf('/')

    Async {
      current.plugin[VersusPlugin] match {

        case Some(plugin) => {
          plugin.queryURL(query).map { result =>
            val l = result.size
            Ok(views.html.searchTextResults(query.substring(slashindex + 1), l, result))

          }
        } // case some
        case None => {
          Future(Ok("No Versus Service"))
        }
      } //match
    } //Async
  }

  
  /*Find similar images/objects in Multiple index*/
 def findSimilar(id:String)=SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
 
   var results =new HashMap[String,Array[(String,String,Double,String)]]
     
 // var file1=new models.TempFile( new ObjectId,None,"",new Date , "image",0)
 // var test=new ArrayBuffer[(String,String,Double,String)]
  var s:String=""
   Async{ 
      
    	current.plugin[VersusPlugin] match {
     
        case Some(plugin)=>{
        	 
        	 var indexListResponse=plugin.getIndexes()
        	 
        	 var indexSeqFuture= for {
        		 list<-indexListResponse
        		
        		 listIn=list.json.as[Seq[models.IndexList.IndexList]]
        		 
        		 indexSeqT=listIn.map{
        		  		    ind=>(ind.indexID,ind.MIMEtype,ind.ex,ind.me,ind.indxr)
        		  		 }
        		} yield {
        	       indexSeqT
        		 }
        	         
           import scala.concurrent.Future
           var finalR=for{
            		indexSeq<-indexSeqFuture
            		} yield {
            				var resultSeqFuture=indexSeq.map{
            						index=>
            						//val indexResultFuture=plugin.queryIndex(id, index._1)
            						val u= for{
            							indexResult<-plugin.queryIndex(id, index._1)
            							} yield {
            									(indexResult,index._3,index._4,index._5)
            								//hm.put(indexResult._1,indexResult._2)
            							}
            						//(u,index._5)
            							u
            					  }//end of indexSeq.map
              
            			
                        var hashResult=for{
                        	   				result<-scala.concurrent.Future.sequence(resultSeqFuture)
                        	   		   } yield {
                        	   				var t=result.toArray
                                            var indexid="" 
                                            var a=0  
                        	   				//var hm= new scala.collection.mutable.HashMap[String, ArrayBuffer[(String,String,Double,String,Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])]]()
                        	   				var hm= new scala.collection.mutable.HashMap[String, (String,String,String,ArrayBuffer[(String,String,Double,String,Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])])]()
                                            for(k<-0 to t.length-1){
                        	   				   hm.put(t(k)._1._1,(t(k)._2,t(k)._3,t(k)._4,t(k)._1._2))
                        	   				}
                        	   				
                        	   				hm
                        	   		   }
            	   			
                        	   		
                        hashResult	   		
            		}//End yield- outer for 
            		
		                			for{
		                				xFinal<-finalR
		                				yFinal<-xFinal
		                				}
		                			yield {
		                			  val keys=yFinal.keySet
		                			  var keysArray=new ArrayBuffer[String]
		                			  keys.copyToBuffer(keysArray)
		                			  
		                			  Services.queries.getFile(id)match{
		                			  	case Some(file)=>{ 
		                			  		//Ok(views.html.multimediaIndexResults(keysArray,file.filename,id,yFinal.size,yFinal))
		                			  	  //Ok(views.html.imageSearchpage(keysArray,file.filename,id,yFinal.size,yFinal))
		                			  	  
		                			  	  Ok(views.html.contentbasedSearchResults(keysArray,file.filename,id,yFinal.size,yFinal))
		                			  	}
		                			  	case None=>{
		                			  			Ok(id +" not found")
		                			  			}
		                			  }
		                			}
              
                    
            
                   
         // import scala.collection.mutable.HashMap
         
         /*  var hm= new scala.collection.mutable.HashMap[String, ArrayBuffer[(String,String,Double,String)]]() 
             val index1="19e7a185-7590-4ee9-9a42-d4e04daec525"
             val index2="4f3dfccf-8fc8-4d75-8a0d-b5d6368626e7"
          for{
            
            index1Result<-plugin.queryIndex(id, index1)
            index2Result<-plugin.queryIndex(id, index2)
           } yield{
             var hm= new scala.collection.mutable.HashMap[String, ArrayBuffer[(String,String,Double,String)]]() 
                 hm.put(index1Result._1,index1Result._2)
                 hm.put(index2Result._1,index2Result._2)
                 Ok(views.html.multimediaIndexResults("abc","123",hm.size,hm))
           }*/
          	  
        	
        
        }//case some
         
		 case None=>{
		        Future(Ok("No Versus Service"))
		       }     
		 } //match
    
   } //Async
  }
  
 
  /*Find similar images/objects in Multiple index*/
 def findSimilarFile(id:String)=SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
  var results =new HashMap[String,Array[(String,String,Double,String)]]
     
 // var file1=new models.TempFile( new ObjectId,None,"",new Date , "image",0)
 // var test=new ArrayBuffer[(String,String,Double,String)]
  var s:String=""
   Async{ 
      
    	current.plugin[VersusPlugin] match {
     
        case Some(plugin)=>{
        	 
        	 var indexListResponse=plugin.getIndexes()
        	 
        	 var indexSeqFuture= for {
        		 list<-indexListResponse
        		
        		 listIn=list.json.as[Seq[models.IndexList.IndexList]]
        		 
        		 indexSeqT=listIn.map{
        		  		    ind=>(ind.indexID,ind.MIMEtype,ind.ex,ind.me,ind.indxr)
        		  		 }
        		} yield {
        	       indexSeqT
        		 }
        	         
           import scala.concurrent.Future
           var finalR=for{
            		indexSeq<-indexSeqFuture
            		} yield {
            				var resultSeqFuture=indexSeq.map{
            						index=>
            						//val indexResultFuture=plugin.queryIndex(id, index._1)
            						val u= for{
            							indexResult<-plugin.queryIndexFile(id, index._1)
            							} yield {
            									(indexResult,index._3,index._4,index._5)
            								//hm.put(indexResult._1,indexResult._2)
            							}
            						//(u,index._5)
            							u
            					  }//end of indexSeq.map
              
            			
                        var hashResult=for{
                        	   				result<-scala.concurrent.Future.sequence(resultSeqFuture)
                        	   		   } yield {
                        	   				var t=result.toArray
                                            var indexid="" 
                                            var a=0  
                        	   				//var hm= new scala.collection.mutable.HashMap[String, ArrayBuffer[(String,String,Double,String,Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])]]()
                        	   				var hm= new scala.collection.mutable.HashMap[String, (String,String,String,ArrayBuffer[(String,String,Double,String,Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])])]()
                                            for(k<-0 to t.length-1){
                        	   				   hm.put(t(k)._1._1,(t(k)._2,t(k)._3,t(k)._4,t(k)._1._2))
                        	   				}
                        	   				
                        	   				hm
                        	   		   }
            	   			
                        	   		
                        hashResult	   		
            		}//End yield- outer for 
            		
		                			for{
		                				xFinal<-finalR
		                				yFinal<-xFinal
		                				}
		                			yield {
		                			  val keys=yFinal.keySet
		                			  var keysArray=new ArrayBuffer[String]
		                			  keys.copyToBuffer(keysArray)
		                			  
		                			  Services.files.getFile(id)match{
		                			  	case Some(file)=>{ 
		                			  		//Ok(views.html.multimediaIndexResults(keysArray,file.filename,id,yFinal.size,yFinal))
		                			  	  //Ok(views.html.imageSearchpage(keysArray,file.filename,id,yFinal.size,yFinal))
		                			  	  
		                			  	  Ok(views.html.contentbasedSearchResults(keysArray,file.filename,id,yFinal.size,yFinal))
		                			  	}
		                			  	case None=>{
		                			  			Ok(id +" not found")
		                			  			}
		                			  }
		                			}
              
                    
            
                   
         // import scala.collection.mutable.HashMap
         
         /*  var hm= new scala.collection.mutable.HashMap[String, ArrayBuffer[(String,String,Double,String)]]() 
             val index1="19e7a185-7590-4ee9-9a42-d4e04daec525"
             val index2="4f3dfccf-8fc8-4d75-8a0d-b5d6368626e7"
          for{
            
            index1Result<-plugin.queryIndex(id, index1)
            index2Result<-plugin.queryIndex(id, index2)
           } yield{
             var hm= new scala.collection.mutable.HashMap[String, ArrayBuffer[(String,String,Double,String)]]() 
                 hm.put(index1Result._1,index1Result._2)
                 hm.put(index2Result._1,index2Result._2)
                 Ok(views.html.multimediaIndexResults("abc","123",hm.size,hm))
           }*/
          	  
        	
        
        }//case some
         
		 case None=>{
		        Future(Ok("No Versus Service"))
		       }     
		 } //match
    
   } //Async
   
  }
 
 
 
 
 
 
 
 
 
 
 
  /* Find Similar files*/
/*  def findSimilar(id: String) = Action {
    Async {
      current.plugin[VersusPlugin] match {

        case Some(plugin) => {
          plugin.query(id).map { result =>
            Services.queries.getFile(id) match {
              case Some(file) => {
                Logger.debug("file id=" + file.id.toString())
                val l = result.size
                Ok(views.html.searchImgResults(file, id, l, result))
              }
              case None => {
                Ok(id + " not found")
              }
            }
          }

        } // case some
        case None => {
          Future(Ok("No Versus Service"))
        }
      } //match
    } //Async
  }*/

  def Filterby(id: String) = TODO

  def uploadquery() = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    request.body.file("picture").map { picture =>
      import java.io.File
      val filename = picture.filename
      val contentType = picture.contentType
      picture.ref.moveTo(new File("/tmp/picture"))
      Ok("File uploaded")
    }.getOrElse {
      Redirect(routes.Application.index).flashing(
        "error" -> "Missing file")
    }
  }
  /*Action(parse.multipartFormData) { request =>
  request.body.file("picture").map { picture =>
    import java.io.File
    val filename = picture.filename 
    val contentType = picture.contentType
    picture.ref.moveTo(new File("/tmp/picture"))
    Ok("File uploaded")
  }.getOrElse {
    Redirect(routes.Application.index).flashing(
      "error" -> "Missing file"
    )
  }
}*/
}
