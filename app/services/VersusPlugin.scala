package services

import models.{SearchResultFile, SearchResultPreview, PreviewFilesSearchResult,  UUID}
import play.api.{Plugin, Logger}
import play.api.libs.json.{Json, JsValue}
import play.api.libs.ws.{WS, Response}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import java.text.DecimalFormat
import scala.collection.mutable.ListBuffer
import scala.collection.immutable.Map

/** 
 * Versus Plugin
 * 
 * @author Smruti Padhy
 * @author Inna Zharnitsky * 
 **/
class VersusPlugin(application:Application) extends Plugin{
  
  val files: FileService =  DI.injector.getInstance(classOf[FileService])
  val previews: PreviewService =  DI.injector.getInstance(classOf[PreviewService])
  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val sections: SectionService = DI.injector.getInstance(classOf[SectionService])
  val queries: MultimediaQueryService = DI.injector.getInstance(classOf[MultimediaQueryService])
  val sectionIndexInfo: SectionIndexInfoService = DI.injector.getInstance(classOf[SectionIndexInfoService])

  override def onStart() {
    Logger.debug("Starting Versus Plugin")
  }
  
/*
 * This method sends the file's url to Versus for the extraction of descriptors from the file
 */
  def extract(fileid: UUID): Future[Response] = {
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
    val fileUrl = client + "/api/files/" + fileid + "/blob?key=" + configuration.getString("commKey").get
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
                Logger.debug("GET META DATA")
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
    Logger.debug("VersusPlugin.getAdapters")
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val adapterUrl = host + "/adapters"
    val adapterList: Future[Response] = WS.url(adapterUrl).withHeaders("Accept" -> "application/json").get()
    adapterList.map {
      response =>// Logger.debug("GET: AdapterLister: response.body=" + response.body)
    }
    adapterList
  }
  
  /*
   * Gets the list of extractors available in Versus web server
   */
  def getExtractors(): Future[Response] = {
    Logger.debug("VersusPlugin.getExtractors")
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val extractorUrl = host + "/extractors"
    val extractorList: Future[Response] = WS.url(extractorUrl).withHeaders("Accept" -> "application/json").get()
    extractorList.map {
      response => //Logger.debug("GET: ExtractorList: response.body=" + response.body)
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
      response => //Logger.debug("GET: measureList: response.body=" + response.body)
      
    }
    measureList
  }
  
  /*
   * Gets the list of indexers available in Versus web server
   *  
   */
  def getIndexers(): Future[Response] = {
    Logger.debug("VersusPlugin.getIndexers")
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexerUrl = host + "/indexers"
    val indexerList: Future[Response] = WS.url(indexerUrl).withHeaders("Accept" -> "application/json").get()
    indexerList.map {
      response => //Logger.debug("GET: indexerList: response.body=" + response.body)
    }
    indexerList
  }


  /* 
   * Get all indexes from Versus web server
   * 
   */
  def getIndexes(): Future[Response] = {
    Logger.debug("VersusPlugin.getIndexes")
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexurl = host + "/indexes"
    var k = 0
    val indexList: Future[Response] = WS.url(indexurl).withHeaders("Accept" -> "application/json").get()
    indexList.map { response => 
      val json: JsValue = Json.parse(response.body)          

      val seqOfIndexes = json.as[Seq[models.VersusIndexTypeName]]   
      //val seqOfIndexes = json.as[Seq[models.VersusIndex]]   

      for (index <- seqOfIndexes) {
    	  Logger.debug("VersusPlugin.getIndexes  index = " + index)
      }
    }
    indexList
  }
  
  /* 
   * Get all indexes from Versus web server as a future list 
   * 
   */ 
   def getIndexesAsFutureList(): Future[List[models.VersusIndex]]= {  
    Logger.debug("VersusPlugin: Getting indexes as a future list")

    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexurl = host + "/indexes"   
    val indexList: Future[Response] = WS.url(indexurl).withHeaders("Accept" -> "application/json").get()        
    indexList.map {
      response =>       
        val json: JsValue = Json.parse(response.body)          
        val seqOfIndexes = json.as[Seq[models.VersusIndex]]   
        seqOfIndexes.toList
    } 
  }
   
       
   /**
    * From all existing Versus indexes, select
    * a subset based on typeToSearch and (optionally) sectionsSelected.
    * 
    * If typeToSearch is...
    * 		both - return all the indexes
    * 		sectionsSome - return selected sections
    * 		sectionsAll - return all sections
    * 		images - return indexes of whole images
    */
   def getIndexesForType(typeToSearch:String, sectionsSelected:List[String]):Future[List[models.VersusIndex]]= {	   
	   Logger.debug("VP getIndexesForType")
		   //get all the indexes from versus
	   val configuration = play.api.Play.configuration;
	   val host = configuration.getString("versus.host").getOrElse("");
	   val indexurl = host + "/indexes";
   
	   var matchingIndexes = new ListBuffer[models.VersusIndex];
	   val futureResponse: Future[Response] = WS.url(indexurl).withHeaders("Accept" -> "application/json").get()
	   futureResponse.map {
      response =>     
        val json: JsValue = Json.parse(response.body)         
        //all the indexes from Versus
        val allIndexes = json.as[List[models.VersusIndex]]  
        
        //select indexes subset based on the value of typeToSearch
        val indexesBuf =new ListBuffer[models.VersusIndex]
        
           Logger.debug("VP getIndexesForType 111")
           
        if (typeToSearch.equals("images")){
    	  Logger.debug("images only, no sections")
    	  allIndexes.map{
    	    index =>
    	      //return all indexes that contain whole images. Filter out indexes of sections.
    	      //if index is in the sectionIndexInfo mongodb collection AND it has 'type' - it contains sections, NOT needed here
    	      //if 'type' field is empty (could have name only, or not be in the mongodb collection) - it's index of a whole image, saving it.
    	      sectionIndexInfo.getType(UUID(index.id)) match {
    	      	case Some(t) =>
    	      	case None => indexesBuf += index
    	      }    	       
    	  }     	  
        } else if (typeToSearch.equals("sectionsAll")){
          Logger.debug("typeToSearch.equals sectionsAll")
          //return all indexes that contain sections. These are in the sectionIndexInfo mongodb collection, with
          // 'type' field not-empty
          allIndexes.map{
    	    index =>
    	    	sectionIndexInfo.getType(UUID(index.id)) match {
    	    	  case Some(t) => indexesBuf += index
    	    	  case None =>    	    	  
    	    	}
          }    	 
        } else if (typeToSearch.equals("sectionsSome")){
                    Logger.debug("typeToSearch.equals sectionsSome, sectionsSelected = " + sectionsSelected)

          //return indexes that contain sections of the selected type(s), contained in the sectionsSelected variable
          allIndexes.map{
    	    index =>
    	    	sectionIndexInfo.getType(UUID(index.id)) match {
    	    	  case Some(sectionType) => 
    	    	    if (sectionsSelected.contains(sectionType)) {indexesBuf += index}
    	    	  case None =>    	    	  
    	    	}
          }     	 
        } else if (typeToSearch.equals("both")){
                              Logger.debug("typeToSearch.equals both")

           allIndexes.map{
        	index => 
        	  indexesBuf +=index
          }
               indexesBuf.toList               
        } else {
             Logger.debug("VP getIndexesForType 3333 ")
          Logger.error("Invalid input")  // the default, catch-all
        }
        Logger.debug("VP - for typetosearch = " +typeToSearch + " got list of indexes: " +indexesBuf )
        indexesBuf.toList          
        
	   } // futureResponse.map {     
   }
   
   
   
   
   /* 
   * Get all indexes from Versus web server THAT MATCH THE FILE CONTENT TYPE as a future list
   */
 def getIndexesForContentTypeAsFutureList(contentType: String): Future[List[models.VersusIndex]]= {    
   Logger.debug("VersusPlugin,getIndexesForContentTypeAsFutureList")
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexurl = host + "/indexes"
   
    val futureResponse: Future[Response] = WS.url(indexurl).withHeaders("Accept" -> "application/json").get()
    futureResponse.map {

      response =>     
        val indexes = Json.parse(response.body).as[Seq[models.VersusIndex]]
        val fileType = contentType.split("/")            
               
        //go through all the indexes, choose only ones with matching content/MIME type
        indexes.filter(index=>{
          val indexMimetype = index.MIMEtype.split("/")
            //indexMimeType = image/* or application/pdf or */*
            //fileType = image/png or image/jpeg or application/pdf
            indexMimetype(0).equals(contentType.split("/")(0)) || indexMimetype(0).equals("*")
        }).toList    
    }    
  }
  
  /**
   * Sends a request to Versus to delete an index based on its id
   * If sectionIndex - delete entry from sectionIndexInfo db collection
   */  
    def deleteIndex(indexId: UUID): Future[Response] = {    
      Logger.debug("VersusPlugin.deleteIndex for indexid = " + indexId)     
     
      //also delete from mongo collection
      //will search mongo for given id, if found - will delete, if not found - will do nothing
       val res =  sectionIndexInfo.delete(indexId)
        
      val configuration = play.api.Play.configuration
      val host = configuration.getString("versus.host").getOrElse("")
      val deleteurl = host + "/indexes/" + indexId
      var deleteResponse: Future[Response] = WS.url(deleteurl).delete()
      deleteResponse.map {
        r => Logger.debug("VersusPlugin - Response from deleteIndex is " + r.body);
      }
      deleteResponse
    }
/**
 * Sends a request to delete all indexes in Versus
 */
  def deleteAllIndexes(): Future[Response] = {
		  //Also remove entries from mongo sectionIndexInfo table
	      sectionIndexInfo.deleteAll()
	      
	      val configuration = play.api.Play.configuration
	      val host = configuration.getString("versus.host").getOrElse("")
	      val indexurl = host + "/indexes"
	      val response: Future[Response] = WS.url(indexurl).delete()
	      response
  }

  /**
   * Sends a request Versus to create an index.
   * Returns id of the created index. 
   */  
  def createIndex(adapter: String, extractor: String, measure: String, indexer: String): Future[UUID] = {
	Logger.debug("VersusPlugin: top of createIndex")   
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexId = WS.url(host + "/indexes").post(
        Map("Adapter" -> Seq(adapter), 
        		"Extractor" -> Seq(extractor), 
        		"Measure" -> Seq(measure), 
        		"Indexer" -> Seq(indexer))
            ).map {
      res =>   
        //Logger.debug("versusPlugin - createdIndex with id " + res.body)
        UUID(res.body)
    }  
    indexId
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
   * Sends a request to Versus REST endpoint to index a still image file
   * 
   */
  def  indexFile  (fileId: UUID, fileType: String) {   
    //called from /app/controllers/Files.scala->upload()
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
    val indexId = configuration.getString("versus.index").getOrElse("")
    val fileURL = client + "/api/files/" + fileId + "/blob?key=" + configuration.getString("commKey").get
    index(fileURL, fileType)
  }
  
  /**
   * Goes through all exisitng indexes, only adds file to indexes of matching mime type.
   *   url - the url of the file/section/preview being indexed, where it can be downloaded from.
   * 
   */
  def index(url:String, fileType: String) {    
	  //go through all existing indexes
	  getIndexesAsFutureList().map(_.map(	ind=>  addToIndex(url, ind, fileType)))
  }
  
  /**
   * Adds this file/section/preview to this index ONLY if mimetypes match.
   * Calls Versus REST endpoint.
   *   url - url where the file/section/preview can be found and downloaded from
   */
  def addToIndex(url:String, index:models.VersusIndex, fileType:String){
    Logger.debug("add to index , url = " + url)
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    
    val mimetypeStr = index.MIMEtype.split("/")(0)
    //fileType = image/png or image/jpeg; MIMEtype = image/* or */*
    if (mimetypeStr.equals( fileType.split("/")(0)) || mimetypeStr.equals("*")) {	
    	//mimetypes match, send to versus to be added to index
    	WS.url(host + "/indexes/" + index.id + "/add").post(Map("infile" -> Seq(url)))
    } else {
      //mimetypes do not match, do nothing.
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
  		Logger.debug("VersusPlugin: removeFromIndexes for fileId = " + fileId)
      
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
   * Sends a request to Versus to build an index based on id
   */
  
  def buildIndex(indexId: UUID): Future[Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val buildurl = host + "/indexes/" + indexId.stringify + "/build"
    var buildResponse: Future[Response] = WS.url(buildurl).post("")
    buildResponse.map {
      r => Logger.debug("r.body = " + r.body);
    }
    buildResponse
  }  
 

  def queryIndexForURL(fileURL: String, indexId: String): Future[  List[PreviewFilesSearchResult]] = {
	Logger.debug("VersusPlugin - queryIndexForURL")
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
  	def queryIndexForExistingFile(inputFileId: UUID, indexId: String): Future[List[PreviewFilesSearchResult]] = {
		  //called when multimedia search -> find similar is clicked    
		  Logger.debug("VersusPlugin - queryIndexForExistingFile  - file id = " + inputFileId )   		  
    		  
		  val configuration = play.api.Play.configuration
		  val client = configuration.getString("versus.client").getOrElse("") 
		  val host = configuration.getString("versus.host").getOrElse("")		  
		  val queryStr = client + "/api/files/" + inputFileId + "/blob?key=" + configuration.getString("commKey").get
		  
		  queryIndex(queryStr, indexId) 
   	}
  
  
   /*
   * Searches for entries similar to the new query file (NOT in the db, just uploaded by the user)
   */
  	def queryIndexForNewFile(newFileId: UUID, indexId: String): Future[ List[PreviewFilesSearchResult]] = {
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
   
  def queryIndex( inputFileURL: String, indexId: String ): Future[List[PreviewFilesSearchResult]] = {       
    Logger.debug("VersusPlugin.queryIndex")
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")    
    val queryIndexUrl = host + "/indexes/" + indexId + "/query"
    //example: queryIndexUrl = http://localhost:8080/api/v1/indexes/a885bad2-f463-496f-a881-c01ebd4c31f1/query
    //will call IndexResource.queryindex on Versus side
   
    val responseFuture: Future[Response] = WS.url(queryIndexUrl).post(Map("infile"->Seq(inputFileURL)))   
    //mapping Future[Response] to Future[List[PreviewFilesSearchResult]]    
    
    responseFuture.map {      
      response =>
      	Logger.debug("VP queryIndex resp body = " + response.body)
        val similarityResults = Json.parse(response.body).as[Seq[models.VersusSimilarityResult.VersusSimilarityResult]]  
            	
        var resultList = new ListBuffer[PreviewFilesSearchResult]    

        similarityResults.map {      

          result =>            
            //example: result.docID = http://localhost:9000/api/files/52fd26fbe4b02ac3e30280db/blob?key=r1ek3rs
            //or
            //result.docID = http://localhost:9000/api/previews/52fd1970e4b02ac3e30280a5/blob?key=r1ek3rs
            //        
            //parse docID to get preivew id or file id - string between '/' and '?'
            val end = result.docID.lastIndexOf("?")
            val begin = result.docID.lastIndexOf("/");                       
            val resultId = UUID(result.docID.substring(begin + 1, end));
          
            //
            //check if this is a file or a video preview
            //
            val isFile = result.docID.contains("files")
            val isPreview = result.docID.contains("previews")

            //when searching for videos - might get previews search results
            if (isPreview){ 
               previews.getBlob(resultId) match{                            	
                case Some (blob)=>{   
              	  val previewName =blob._2
              	   //use helper method to get the results
              		getPrevSearchResult(resultId, previewName, result)  match {
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
              files.get(resultId) match {               	  
                case Some (file)=>{  
                	//use helper method to get the results
               	    val oneFileResult = getFileSeachResult(resultId, file, result)
               	    resultList += new PreviewFilesSearchResult("file", oneFileResult, null  )                  	                  	  
               	    }               	  
                case None => {}                 }
          }//end of if (isFile)        
        } // End of similarity map      
        resultList.toList
    	}   
    }
  

 /**
   *Used to display resutls with weighted combined indexes. 
   * For a given index and file, will query index and return results
   * the results are reorganized as a hash map of (file url, proximity) touples.
   * Note: might be reorganized in the future to use queryIndex method
   */
  def queryIndexSorted( inputFileId: String, indexId: String ): Future[ scala.collection.immutable.HashMap[String, Double]] = {       
    Logger.debug("VersusPlugin.queryIndexSorted, indexId = " + indexId )
  
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")  
    val client = configuration.getString("versus.client").getOrElse("")    

    //if searching for file already uploaded previously, use api/files
    // val queryStr = client + "/api/files/" + inputFileId + "/blob?key=" + configuration.getString("commKey").get
    //if searching for a new file, i.e.  uploaded just now, use api/queries
    val queryStr = client + "/api/queries/" + inputFileId + "?key=" + configuration.getString("commKey").get  
    val responseFuture: Future[Response] = WS.url(host + "/indexes/" + indexId + "/query").post(Map("infile"->Seq(queryStr))) 

    //example: queryIndexUrl = http://localhost:8080/api/v1/indexes/a885bad2-f463-496f-a881-c01ebd4c31f1/query
    //will call IndexResource.queryindex on Versus side
        
    //mapping Future[Response] to Future[HashMap[index URL as String, proximity]]    
    responseFuture.map {      
      response =>      
        val similarityResults = Json.parse(response.body).as[Seq[models.VersusSimilarityResult.VersusSimilarityResult]]  
      	//max prox is the same for every result              	     
      	//a list of touples (id, normalized proximity)
        val toupleList = similarityResults.map{res =>       	  
      	   //"if(c) p else q" equivalent to java "c ? p : q"     	   
      	   (res.docID, if(res.maxProximity==0)  res.proximity else (res.proximity / res.maxProximity))
      	 }.toList
         
       val resultsHM = new scala.collection.mutable.HashMap[String, Double]
       toupleList foreach {
       		case (key, value) =>
       				resultsHM.put(key, value)    
       				Logger.debug(key + "  ==>>   " + value)       
       }       
       var result = collection.immutable.HashMap(resultsHM.toSeq:_*)
       result
    }   //end of responseFuture.map {     
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
              
		  val formatter = new DecimalFormat("#.#####")
		  // resultArray += ((subStr, result.docID, result.proximity, file.filename,previews))
		  val proxvalue = formatter.format(result.proximity).toDouble     
		  val normalizedProxvalue = formatter.format(result.proximity/result.maxProximity).toDouble  
           
		  var thumb_id = file.thumbnail_id.getOrElse("")		  
		  
		  val oneFileResult = new SearchResultFile(result_id, result.docID, normalizedProxvalue, file.filename, dataset_id_list.toList, thumb_id)
		  return oneFileResult			            
   }
   
   /*
    * Helper method. Called from queryIndex
    */
  	def getPrevSearchResult(preview_id:UUID, prevName:String, result:models.VersusSimilarityResult.VersusSimilarityResult):Option[SearchResultPreview]=
  	{                      
		  val formatter = new DecimalFormat("#.#####")                
		  val proxvalue = formatter.format(result.proximity).toDouble
		  val normalizedProxvalue =    formatter.format(result.proximity/result.maxProximity).toDouble    
		  previews.get(preview_id) match {
		  case Some(preview)=>{

			  var sectionStartTime=0                   
			  var fileName = ""
   			  var fileIdString = ""
   			  var datasetIDs=new ListBuffer[String]
                  
			  //===Get section and file info, dataset(s) info. Preview is associated with one section, section is associated with one file, file can be associated with 0 or more datasets             
			  preview.section_id match {
			  	case Some(section_id)=>{
			  		sections.get(section_id)match {
			  			case Some(section)=>{
			  				sectionStartTime = section.startTime.getOrElse(0)
                            //get file id and file name for this preview. Preview belongs to a section, and section belongs to a file.
                            var file_id = section.file_id 
                            fileIdString = file_id.stringify
                            
                           
                            files.get(file_id) match {
                            	case Some(file)=>{
                            		fileName = file.filename        
                            		for(dataset <- datasets.findByFileId(file_id)){                                    
                            			datasetIDs+= dataset.id.stringify              
                            		} 
                            	}
                            	case None =>{}
                            }// end of files.get(file_id) match
			  				
                        }//end of     case Some(section)=>{
                        case None =>{}                          
                    }
			  	} 
			  	case None =>{}                      

			  }//end of preview.section_id match
			                      
			  var onePreviewResult = new SearchResultPreview(preview_id, result.docID, normalizedProxvalue, 
					  prevName, datasetIDs.toList, fileIdString, fileName, sectionStartTime)                               
                  		
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
