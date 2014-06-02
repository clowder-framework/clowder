package services

import models.SearchResultFile
import models.SearchResultPreview
import models.PreviewFilesSearchResult


import models.FileMD

import models.File
import models.Dataset
import play.api.{ Plugin, Logger, Application }
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.api.libs.ws.Response
import java.io._
import play.api.Logger
import play.api.Play.current
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Input.Empty
import com.mongodb.casbah.gridfs.GridFS
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import models.{UUID, File}
import play.api.libs.json.Json._
import scala.concurrent.{blocking, Future, Await}
import akka.actor.Actor
import akka.actor.ActorRef
import controllers.Previewers
import controllers.routes
import java.text.DecimalFormat

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.immutable.Map
import scala.collection.mutable.ListBuffer

/** 
 * Versus Plugin
 * 
 * @author Smruti
 * 
 **/
class VersusPlugin(application:Application) extends Plugin{
  
  val files: FileService =  DI.injector.getInstance(classOf[FileService])
  val previews: PreviewService =  DI.injector.getInstance(classOf[PreviewService])
  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val sections: SectionService = DI.injector.getInstance(classOf[SectionService])
  val queries: MultimediaQueryService = DI.injector.getInstance(classOf[MultimediaQueryService])

  override def onStart() {
    Logger.debug("Starting Versus Plugin")
  }
  
/*
 * This method sends the file's url to Versus for the extraction of descriptors from the file
 */


  def extract(fileid: UUID): Future[Response] = {
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
    val fileUrl = client + "/api/files/" + fileid + "?key=" + configuration.getString("commKey").get
    val host = configuration.getString("versus.host").getOrElse("")

    val extractUrl = host + "/extract"

    val extractJobId = WS.url(extractUrl).post(Map("dataset1" -> Seq(fileUrl))).map {
      res =>
        Logger.debug("Extract Job ID=" + res.body)

        val desResponse = WS.url(extractUrl + "/" + res.body).withHeaders("Accept" -> "application/json").get()
        desResponse.map {
          response =>
            files.get(fileid) match {
              case Some(file) => {             
                val list=response.json\("versus_descriptors")                
                files.addVersusMetadata(fileid, list)               
                Logger.debug("GET META DATA:*****")
                files.getMetadata(fileid).map {
                  md =>
                    Logger.debug(":::" + md._2.toString)
                }
              }
              case None => {}
            }
        }
        res
    } //WS map end
    extractJobId
  }

  /*
   * Gets the list of adapters avaliable in Versus web server
   */
  def getAdapters(): Future[Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val adapterUrl = host + "/adapters"
    val adapterList: Future[Response] = WS.url(adapterUrl).withHeaders("Accept" -> "application/json").get()
    adapterList.map {
      response => Logger.debug("GET: AdapterLister: response.body=" + response.body)
    }
    adapterList
  }
  
  /*
   * Gets the list of extractors available in Versus web server
   */
  def getExtractors(): Future[Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val extractorUrl = host + "/extractors"
    val extractorList: Future[Response] = WS.url(extractorUrl).withHeaders("Accept" -> "application/json").get()
    extractorList.map {
      response => Logger.debug("GET: ExtractorList: response.body=" + response.body)
    }
    extractorList
  }
  /*
   * Gets the list of measures available in Versus web server
   */
  def getMeasures(): Future[Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val measureUrl = host + "/measures"
    val measureList: Future[Response] = WS.url(measureUrl).withHeaders("Accept" -> "application/json").get()
    measureList.map {
      response => Logger.debug("GET: measureList: response.body=" + response.body)
    }
    measureList
  }
  
  /*
   * Gets the list of indexers available in Versus web server
   *  
   */
  def getIndexers(): Future[Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexerUrl = host + "/indexers"
    val indexerList: Future[Response] = WS.url(indexerUrl).withHeaders("Accept" -> "application/json").get()
    indexerList.map {
      response => Logger.debug("GET: indexerList: response.body=" + response.body)
    }
    indexerList
  }


  /* 
   * Get all indexes from Versus web server
   * 
   */
  def getIndexes(): Future[Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexurl = host + "/indexes"
    var k = 0
    val indexList: Future[Response] = WS.url(indexurl).withHeaders("Accept" -> "application/json").get()
    indexList.map {
      response => Logger.debug("GETINDEXES: response.body=" + response.body)
    }
    indexList
  }
  
  /* 
   * Get all indexes from Versus web server as a future list 
   * 
   */
  def getIndexesAsFutureList(): Future[List[models.VersusIndex.VersusIndex]]= {  
    Logger.debug("Getting indexes as a future list")
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexurl = host + "/indexes"
   
    val indexList: Future[Response] = WS.url(indexurl).withHeaders("Accept" -> "application/json").get()
        
    indexList.map {
      response =>       
        val json: JsValue = Json.parse(response.body)          
        val seqOfIndexes = json.as[Seq[models.VersusIndex.VersusIndex]]           
        seqOfIndexes.toList
    } 
  }
  
   /* 
   * Get all indexes from Versus web server THAT MATCH THE FILE CONTENT TYPE as a future list
   * 
   */
  def getIndexesForContentTypeAsFutureList(contentType: String): Future[List[models.VersusIndex.VersusIndex]]= {    
   val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexurl = host + "/indexes"
   
    var matchingIndexes = new ListBuffer[models.VersusIndex.VersusIndex]

    val indexList: Future[Response] = WS.url(indexurl).withHeaders("Accept" -> "application/json").get()
    indexList.map {
      response =>       
        val json: JsValue = Json.parse(response.body)    
        val seqOfIndexes = json.as[Seq[models.VersusIndex.VersusIndex]]  
        
        //go through all the indexes, choose only ones with matching content/MIME type
        seqOfIndexes.map {
          index=>      
            //only choose indexes that have matching content/MIME type.
            val indexMimeTypeStr = index.MIMEtype.split("/")
            val fileTypeStr = contentType.split("/")            
            //indexMimeType = image/* or application/pdf or */*
            //fileType = image/png or image/jpeg or application/pdf
            if (indexMimeTypeStr(0).equals(fileTypeStr(0)) || indexMimeTypeStr(0).equals("*")) {
            	matchingIndexes += index
            }            
        }
      	Logger.debug("Found  matching Indexes = " + matchingIndexes)  
      	matchingIndexes.toList
    }    
  }
  
/*
 * Sends a request to Versus to delete an index based on its id
 */
  
  def deleteIndex(indexId: String): Future[Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val deleteurl = host + "/indexes/" + indexId
    Logger.debug("Deleting IndexId = " + indexId);
    var deleteResponse: Future[Response] = WS.url(deleteurl).delete()
    deleteResponse.map {
      r => Logger.debug("Response from deleteIndex is " + r.body);
    }
    deleteResponse
  }
  
/*
 * Sends a request to delete all indexes in Versus
 */
  def deleteAllIndexes(): Future[Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexurl = host + "/indexes"

    val response: Future[Response] = WS.url(indexurl).delete()
    response
  }

  /*
   * Sends a request Versus to create an index with <adapter,extractor, measure, indexer> selected
   */
  def createIndex(adapter: String, extractor: String, measure: String, indexer: String): Future[Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")

    val createIndexUrl = host + "/indexes";
    Logger.debug("Form Parameters: " + adapter + " " + extractor + " " + measure + " " + indexer);
    Logger.debug("theurl: " + createIndexUrl);
    val response = WS.url(createIndexUrl).post(Map("Adapter" -> Seq(adapter), "Extractor" -> Seq(extractor), "Measure" -> Seq(measure), "Indexer" -> Seq(indexer))).map {
      res =>
        res
    }
    response
  }
  
   /*
   * Sends a request to Versus REST endpoint to index a still image file
   * 
   */
  def  indexFile  (fileId: UUID, fileType: String) {   
    //called from /app/controllers/Files.scala->upload()
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
    val indexId = configuration.getString("versus.index").getOrElse("")
    val fileURL = client + "/api/files/" + fileId + "?key=" + configuration.getString("commKey").get    
    index(fileURL, fileType)
  }
  
  /*
   * Helper method - handles the actual call to the Versus REST endpoint to index a file (document or image or video preview)
   * 
   */
  def index(fileURL:String, fileType: String) {    
	Logger.debug("From index method fileURL = " + fileURL + ", fileType = " +fileType )
    val configuration = play.api.Play.configuration   
    val indexId = configuration.getString("versus.index").getOrElse("")    
    val host = configuration.getString("versus.host").getOrElse("")

    var indexurl = host + "/indexes/" + indexId + "/add"    
	 getIndexesAsFutureList().map{
    	indexList=>
        	indexList.map{
    			index=>    
    				Logger.debug("indexID=" + index.id + " MIMEType=" + index.MIMEtype + " fileType=" + fileType);
    
    				//only add file to indexes that have matching mimetype.
    				val fileTypeStr = fileType.split("/")
    				val mimeTypeStr = index.MIMEtype.split("/")
    				//fileType = image/png or image/jpeg
    				//MIMEtype = image/*
    				if (fileTypeStr(0).equals(mimeTypeStr(0)) || mimeTypeStr(0).equals("*")) {
    					Logger.debug("for index.id = " + index.id + " calling add to index")
    					indexurl = host + "/indexes/" + index.id + "/add"
    					WS.url(indexurl).post(Map("infile" -> Seq(fileURL))).map {
    						res =>
    					} //WS map end
    				} //if fileType end
        	}// indexList.map {
    }
  }
  
  
  /*
   * Removes video or image file from indexes on Versus side.
   * Goes through the list of all indexes and calls Versus REST endpoint to remove the file from each index. 
   * If input file is a still image - removes the file itself from the indexes.
   * If input file is a video - finds all its previews and removes each preview from the indexes.
   * 
   */
  	def removeFromIndexes(fileId: UUID){
  		//
  		//called from app/api/Files->removeFile()
  		//
  		Logger.debug("removeFromIndexes for fileId = " + fileId)
      
  		val configuration = play.api.Play.configuration
  		val host = configuration.getString("versus.host").getOrElse("")   
    
  		files.get(fileId) match {
  			case Some(file) => {                    
  				//
  				//if file is a video, then it might have several still image previews 
  				//(Previews are the first frame of each shot of the video file).
  				//These previews were indexed and are now part of Versus indexes.
  				//Have to remove these previews from the Versus indexes.
  				//
  				if (file.contentType.contains("video/")){
    			
  					//find previews of type IMAGE and delete these from Versus
  					for(preview <- previews.findByFileId(file.id)){
  						if (preview.contentType.contains("image")){
  							for (indexList <-getIndexesAsFutureList()){
  								for (index<- indexList){                			    
  									val queryurl = host + "/indexes/" + index.id + "/remove_from"
  									val resultFuture: Future[Response] = WS.url(queryurl).post(Map("infile" -> Seq(preview.id.stringify)))
  								}  
  							}   
  						}
  					}   // end of for(preview <- previews.findByFileId(file.id)){             
                }                
             
                if(!file.contentType.contains("video/")){
                	for (indexList <- getIndexesAsFutureList()){
                		for (index <-	indexList){  
                			val queryurl = host + "/indexes/" + index.id + "/remove_from"
                			val resultFuture: Future[Response] = WS.url(queryurl).post(Map("infile" -> Seq(fileId.stringify)))
                		}  
                	}                  
                }            
  			}//end case Some(file) 
  			case None => {
                Logger.debug(" Could not remove file - not found.")
  			}
  		}
     
  	} 
  
  /*
   * Sends a request to Versus to index a video preview file (first frame of each shot of a video)
   */
  def indexPreview(previewId: UUID, fileType: String) {
	  //called from /app/api/Indexes.scala->index()
	  //which is called from cinemetrics extractor ->uploadShot
	  Logger.debug("Top of index preview, id = " + previewId + ", fileType = " +fileType )
	  val configuration = play.api.Play.configuration
	  val client = configuration.getString("versus.client").getOrElse("")
	  val indexId = configuration.getString("versus.index").getOrElse("")
	  val prevURL = client + "/api/previews/" + previewId + "?key=" + configuration.getString("commKey").get    
	  index(prevURL, fileType)    
  }
    
  
  /*
   * Sends a request to Versus to build an index based on id
   */
  
  def buildIndex(indexId: String): Future[Response] = {
    val configuration = play.api.Play.configuration
    //val indexId=configuration.getString("versus.index").getOrElse("")

    val host = configuration.getString("versus.host").getOrElse("")
    val buildurl = host + "/indexes/" + indexId + "/build"
    Logger.debug("IndexID=" + indexId);
    var buildResponse: Future[Response] = WS.url(buildurl).post("")
    buildResponse.map {
      r => Logger.debug("r.body" + r.body);
    }
    buildResponse
  }  
  
     
   
  def queryIndexForURL(fileURL: String, indexId: String): Future[  List[PreviewFilesSearchResult]] = {
		  Logger.debug("queryIndexForURL")
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
    val host = configuration.getString("versus.host").getOrElse("")

    var queryurl = host + "/indexes/" + indexId + "/query"
    //TODO  where does the url get uploaded?
     queryIndex(queryurl, indexId)
  }
  
  /*
   * Searches for entries similar to the input file, which is already in the db.
   */
  	def queryIndexForExistingFile(inputFileId: UUID, indexId: String): Future[ List[PreviewFilesSearchResult]] = {
		  //called when multimedia search -> find similar is clicked    
		  Logger.debug("queryIndexForExistingFile  - file id = " + inputFileId )   		  
    		  
		  val configuration = play.api.Play.configuration
		  val client = configuration.getString("versus.client").getOrElse("") 
		  val host = configuration.getString("versus.host").getOrElse("")		  
		  val queryStr = client + "/api/files/" + inputFileId + "?key=" + configuration.getString("commKey").get
		  
		  queryIndex(queryStr, indexId) 
   	}
  
  
   /*
   * Searches for entries similar to the new query file (NOT in the db, just uploaded by the user)
   */
  	def queryIndexForNewFile(newFileId: UUID, indexId: String): Future[  List[PreviewFilesSearchResult]] = {
		  Logger.debug("queryIndexForNewFile" )
		  val configuration = play.api.Play.configuration
		  val client = configuration.getString("versus.client").getOrElse("")    
		  val queryStr = client + "/api/queries/" + newFileId + "?key=" + configuration.getString("commKey").get
          
		  queryIndex(queryStr, indexId)   
  	}
  
 
  /**
 * Sends a search query to an index in Versus
 * input: 
 * 		inputFileURL - URL string of the file to query against an index
 * 		indexId - id of the index to search within
 * 
 * returns: 
 * 		list of previews and files search results: search results will be sorted by proximity to the given image.
 * 
 * Note the the image index might contain both "previews", i.e. video file previews(images extracted from a video file) 
 * and "files", i.e. in case of image searching - files that were "born as still images"
 * 
 * The list of results will contain both "previews" and "files" sorted by their proximity to the
 * given image.
 * 
 * In case of non-image file types (e.g. pdf), only list of files will be returned.
 *  */     
   
  def queryIndex( inputFileURL: String, indexId: String ): Future[ List[PreviewFilesSearchResult]] = {       
    Logger.debug("queryIndex")
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")    
    val queryIndexUrl = host + "/indexes/" + indexId + "/query"
    //example: queryIndexUrl = http://localhost:8080/api/v1/indexes/a885bad2-f463-496f-a881-c01ebd4c31f1/query
    //will call IndexResource -> queryindex on Versus side
   
    val responseFuture: Future[Response] = WS.url(queryIndexUrl).post(Map("infile"->Seq(inputFileURL)))   
    //mapping Future[Response] to Future[List[PreviewFilesSearchResult]]    
    responseFuture.map {      
      response =>
    
        val json: JsValue = Json.parse(response.body)
        val similarity_value = json.as[Seq[models.VersusSimilarityResult.VersusSimilarityResult]]     
        var resultList = new ListBuffer[PreviewFilesSearchResult]                            
        similarity_value.map {      
          result =>            
            //example: result.docID = http://localhost:9000/api/files/52fd26fbe4b02ac3e30280db?key=r1ek3rs
            //or
            //result.docID = http://localhost:9000/api/previews/52fd1970e4b02ac3e30280a5?key=r1ek3rs
            //        
            //parse docID to get preivew id or file id - string between '/' and '?'
            val end = result.docID.lastIndexOf("?")
            val begin = result.docID.lastIndexOf("/");                       
            val result_id_str = result.docID.substring(begin + 1, end);
            val result_id = UUID(result_id_str);
          
            //
            //check if this is a file or a preview
            //
            val isFile = result.docID.contains("files")
            val isPreview = result.docID.contains("previews")
             
            //when searching for videos - might get previews search results
            if (isPreview){ 
               previews.getBlob(result_id) match{                            	
                case Some (blob)=>{   
              	  val previewName =blob._2
              	   //use helper method to get the results
              		getPrevSearchResult(result_id, previewName, result)  match {
              	    	case Some (previewResult) => {
              	    		resultList += new PreviewFilesSearchResult("preview", null, previewResult) 
              	    	}
              	    	case None =>{
              	    		//no PreviewSearchResult - do nothing
              	    	}              	    
              		}     	
                } 
                case None=>{
                  //no blob found - do nothing
                }
              }//end of previews.getBlob(result_id) match{ 
            }//end of if (isPreview){    
            
            //in case of still images AND non-image file formats
            if (isFile) {
              files.get(result_id) match {               	  
                case Some (file)=>{  
                	//use helper method to get the results
               	    val oneFileResult = getFileSeachResult(result_id, file, result)
               	    resultList += new PreviewFilesSearchResult("file", oneFileResult, null  )                  	                  	  
               	    }               	  
                case None => {}                 }
          }//end of if (isFile)        
        } // End of similarity map      
        resultList.toList
    	}   
    }
  
  
    /*
    * Helper method. Called from queryIndex    
    */
   def getFileSeachResult(result_id:UUID, file:models.File, result:models.VersusSimilarityResult.VersusSimilarityResult):SearchResultFile=
   {    	     
		  //=== find list of datasets ids
		  //this file can belong to 0 or 1 or more  datasets
		  var dataset_id_list = datasets.findByFileId(file.id).map{
			  dataset=>dataset.id.stringify             		  		  
		  }           
              
		  val formatter = new DecimalFormat("#.###")
		  // resultArray += ((subStr, result.docID, result.proximity, file.filename,previews))
		  val proxvalue = formatter.format(result.proximity).toDouble                 
                
		  file.thumbnail_id match {               
		  	case Some(thumb_id) => {
		  		//val oneFileResult = new SearchResultFile(result_id, result.docID, 
                	//	proxvalue, file.filename, dataset_id_list.toList, thumb_id.stringify)
		  		val oneFileResult = new SearchResultFile(result_id, result.docID, 
                		proxvalue, file.filename, dataset_id_list.toList, thumb_id)
		  		return oneFileResult
		  	}
		  	case None=>{
		  		val oneFileResult = new SearchResultFile(result_id, result.docID, 
                		proxvalue, file.filename, dataset_id_list.toList, "")
		  		return oneFileResult		  				
		  	} 	                 
		  }                        
   }
   
   /*
    * Helper method. Called from queryIndex
    */
  	def getPrevSearchResult(preview_id:UUID, prevName:String, result:models.VersusSimilarityResult.VersusSimilarityResult):Option[SearchResultPreview]=
  	{                           
		  val formatter = new DecimalFormat("0.##########E0")                
		  val proxvalue = formatter.format(result.proximity).toDouble
		         
		  previews.get(preview_id) match {
		  case Some(preview)=>{
			  var startTime=0                   
			  var fileName = ""
			  var datasetIdList=new ListBuffer[String]
			  var fileIdString = ""                    
                  
			  //===get section for this preview and its start/end time                    
			  preview.section_id match {
			  	case Some(section_id)=>{
			  		sections.get(section_id)match {
			  			case Some(section)=>{
                            startTime = section.startTime.getOrElse(0)
                        }
                        case None =>{}                          
                     }
			  	} 
			  	case None =>{}                      
			  }
			  //===done: finding section
                    
			  //=== get file for this preview
			  // get file's id, name, and all datasets it belongs to.
			  preview.file_id match{
			  	case Some(file_id)=>{
			  		fileIdString = file_id.stringify                   
			  		files.get(file_id) match {
			  			case Some(file)=>{
			  				fileName = file.filename			  				
			  				for(dataset <- datasets.findByFileId(file_id)){                  				               
			  					datasetIdList+= dataset.id.stringify	             
			  				} 
			  			}
			  			case None =>{}
			  		}// end of files.get(file_id) match                        
			  	} //end of case Some(file_id)=>{
			  	
			  	case None=>{}
			  } //== done: file for this preview
			                      
			  var onePreviewResult = new SearchResultPreview(preview_id, result.docID, proxvalue, 
					  prevName, datasetIdList.toList, fileIdString, fileName, startTime)                               
                  		
			  return Some(onePreviewResult )               
                    
		  }//END OF: case Some(preview)=>{
                  
		  //No preview found
		  case None=>{return None}
                  
		}//END OF : previews.get(preview_id) match        
   } 
 
 
  override def onStop() {
    Logger.debug("Shutting down Versus Plugin")
  }
 
}
