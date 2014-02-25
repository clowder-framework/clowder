package services

import models.PreviewFilesSearchResult
import models.PreviewDAO
import models.FileMD
import models.FileDAO
import models.File
import models.Dataset
import play.api.{ Plugin, Logger, Application }
import java.util.Date
import play.api.libs.json.Json
import play.api.Play.current
import play.api.libs.ws.WS
import play.api.libs.ws.Response
import play.api.libs.concurrent.Promise
import java.io._
import play.api.Logger
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._
import services._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Input.{El, EOF, Empty}
import com.mongodb.casbah.gridfs.GridFS
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import java.util.Date
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import play.api.libs.json.Json._
import scala.concurrent.{future, blocking, Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import play.libs.Akka
import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import controllers.Previewers
import controllers.routes
import java.text.DecimalFormat
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.HashMap
import scala.collection.immutable.Map
/** 
 * Versus Plugin
 * 
 * @author Smruti
 * 
 **/
class VersusPlugin(application:Application) extends Plugin{
  
  val files: FileService =  DI.injector.getInstance(classOf[FileService])
  
  override def onStart() {
    Logger.debug("Starting Versus Plugin")
  }
  
/*
 * This method sends the file's url to Versus for the extraction of descriptors from the file
 */

  def extract(fileid: String): Future[Response] = {

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
            // Logger.debug("RESPONSE FROM EXTRACT****:" +response.body)
            files.getFile(fileid) match {

              case Some(file) => {
              //  FileDAO.addVersusMetadata(fileid, response.json.toString)
               
                val list=response.json\("versus_descriptors")
                
                FileDAO.addVersusMetadata(fileid,list)
               
                Logger.debug("GET META DATA:*****")
                FileDAO.getMetadata(fileid).map {
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
    val indexurl = host + "/index"
    var k = 0
    val indexList: Future[Response] = WS.url(indexurl).withHeaders("Accept" -> "application/json").get()
    indexList.map {
      response => Logger.debug("GETINDEXES: response.body=" + response.body)
    }
    indexList
  }
  
  /* 
   * Get all indexes from Versus web server as an array of Index
   * 
   */
  def getIndexesAsFutureList(): Future[List[models.Index.Index]]= {    
   val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexurl = host + "/index"
   
    val indexList: Future[Response] = WS.url(indexurl).withHeaders("Accept" -> "application/json").get()
        
    indexList.map {
      response =>       
        val json: JsValue = Json.parse(response.body)       
        val seqOfIndexes = json.as[Seq[models.Index.Index]]           
        seqOfIndexes.toList
    } 
  }
  
/*
 * Sends a request to Versus to delete an index based on its id
 */
  
  def deleteIndex(indexId: String): Future[Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val deleteurl = host + "/index/" + indexId
    Logger.debug("IndexID=" + indexId);
    var deleteResponse: Future[Response] = WS.url(deleteurl).delete()
    deleteResponse.map {
      r => Logger.debug("r.body" + r.body);

    }
    deleteResponse
  }
  
/*
 * Sends a request to delete all indexes in Versus
 */
  def deleteAllIndexes(): Future[Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")
    val indexurl = host + "/index"

    val response: Future[Response] = WS.url(indexurl).delete()
    response
  }

  /*
   * Sends a request Versus to create an index with <adapter,extractor, measure, indexer> selected
   */
  def createIndex(adapter: String, extractor: String, measure: String, indexer: String): Future[Response] = {
    val configuration = play.api.Play.configuration
    val host = configuration.getString("versus.host").getOrElse("")

    val createIndexUrl = host + "/index";
    Logger.debug("Form Parameters: " + adapter + " " + extractor + " " + measure + " " + indexer);
    Logger.debug("theurl: " + createIndexUrl);
    val response = WS.url(createIndexUrl).post(Map("Adapter" -> Seq(adapter), "Extractor" -> Seq(extractor), "Measure" -> Seq(measure), "Indexer" -> Seq(indexer))).map {
      res =>
        //Logger.debug("res.body="+res.body)
        res
    }
    response
  }
  
  
        
  /*
   * Sends a request to index a file based on its type
   * Currently, it only supports images
   */
  def index(id: String, fileType: String) {

    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
    val indexId = configuration.getString("versus.index").getOrElse("")
    val urlf = client + "/api/files/" + id + "?key=" + configuration.getString("commKey").get
    val host = configuration.getString("versus.host").getOrElse("")

    var indexurl = host + "/index/" + indexId + "/add"
    Logger.debug("VersusPlugin: index method indexurl: " + indexurl);
    val indexList = getIndexes()
    var k = 0
    indexList.map {
      response =>
        //Logger.debug("response.body=" + response.body)
        val json: JsValue = Json.parse(response.body)
        Logger.debug("index(): json=" + json);
        val list = json.as[Seq[models.IndexList.IndexList]]
        val len = list.length
        val indexes = new Array[(String, String)](len)

        list.map {
          index =>

            Logger.debug("indexID=" + index.indexID + " MIMEType=" + index.MIMEtype + " fileType=" + fileType);
            indexes.update(k, (index.indexID, index.MIMEtype))
            val typeA = fileType.split("/")
            val typeB = index.MIMEtype.split("/")
            // if (fileType.equals(index.MIMEtype)) {
            if (typeA(0).equals(typeB(0)) || typeB(0).equals("*")) {
              indexurl = host + "/index/" + index.indexID + "/add"
              WS.url(indexurl).post(Map("infile" -> Seq(urlf))).map {
                res =>
                  Logger.debug("response from Adding file to Index " + index.indexID + "= " + res.body)

              } //WS map end
            } //if fileType end
        }

    }

  }
  
  /*
   * Sends a request to Versus index a frame from a shot of a video 
   */
  def indexPreview(id: String, fileType: String) {

    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
    val indexId = configuration.getString("versus.index").getOrElse("")

    val urlf = client + "/api/previews/" + id + "?key=" + configuration.getString("commKey").get
    val host = configuration.getString("versus.host").getOrElse("")

    var indexurl = host + "/index/" + indexId + "/add"
    Logger.debug("VersusPlugin: index method indexurl: " + indexurl);
    val indexList = getIndexes()
    var k = 0
    indexList.map {
      response =>
        //Logger.debug("response.body=" + response.body)
        val json: JsValue = Json.parse(response.body)
        Logger.debug("index(): json=" + json);
        val list = json.as[Seq[models.IndexList.IndexList]]
        val len = list.length
        val indexes = new Array[(String, String)](len)

        list.map {
          index =>
            Logger.debug("indexID=" + index.indexID + " MIMEType=" + index.MIMEtype + " fileType=" + fileType);
            indexes.update(k, (index.indexID, index.MIMEtype))
            val typeA = fileType.split("/")
            val typeB = index.MIMEtype.split("/")
            // if (fileType.equals(index.MIMEtype)) {
            if (typeA(0).equals(typeB(0)) || typeB(0).equals("*")) {
              indexurl = host + "/index/" + index.indexID + "/add"
              WS.url(indexurl).post(Map("infile" -> Seq(urlf))).map {
                res =>
                  Logger.debug("response from Adding file to Index " + index.indexID + "= " + res.body)

              } //WS map end
            } //if fileType end
        }

    }

  }
  
  /*
   * Sends a request to Versus to build an index based on id
   */
  
  def buildIndex(indexId: String): Future[Response] = {
    val configuration = play.api.Play.configuration
    //val indexId=configuration.getString("versus.index").getOrElse("")

    val host = configuration.getString("versus.host").getOrElse("")
    val buildurl = host + "/index/" + indexId + "/build"
    Logger.debug("IndexID=" + indexId);
    var buildResponse: Future[Response] = WS.url(buildurl).post("")
    buildResponse.map {
      r => Logger.debug("r.body" + r.body);

    }

    buildResponse
  }
  
  
  
  //def queryIndexForURL(imageURL: String, indexId: String): Future[(String,  List[PreviewFilesSearchResult])] = {
def queryIndexForURL(imageURL: String, indexId: String): Future[  List[PreviewFilesSearchResult]] = {

    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")

    val host = configuration.getString("versus.host").getOrElse("")

    var queryurl = host + "/index/" + indexId + "/query"

    //val resultFuture: Future[Response] = WS.url(queryurl).post(Map("infile" -> Seq(query)))
    //queryIndex(imageURL, queryurl, imageURL)
    queryIndex(queryurl, imageURL) 
  }
  
  
  //def queryIndexForFile(inputFileId: String, indexId: String): Future[(String,  List[PreviewFilesSearchResult])] = {
  def queryIndexForFile(inputFileId: String, indexId: String): Future[ List[PreviewFilesSearchResult]] = {
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")    
    val imageFileURL = client + "/api/queries/" + inputFileId + "?key=" + configuration.getString("commKey").get
    //
    //imageFileURL = http://localhost:9000/api/queries/52f3da24e4b0f9ce279eb9d6?key=r1ek3rs
    // calls @api.Files.downloadquery(id)
    //      
    val host = configuration.getString("versus.host").getOrElse("")
    var queryIndexUrl = host + "/index/" + indexId + "/query"   
    //
    //queryIndexUrl = http://localhost:8080/api/v1/index/b9a4b265-f517-423a-bcc2-27e23a8eab13/query
    //    
    val queryStr = client + "/api/files/" + inputFileId + "?key=" + configuration.getString("commKey").get
    
    queryIndex(queryIndexUrl, queryStr)   
    //queryIndex(inputFileId, queryIndexUrl, queryStr)   
   }
  
  
   /*
 * sends a query to an index in Versus
 * input: imageId - id of the image file (on Medici) to query against an existing index(on Versus)
 * indexId - id of the indexer against which to query the image
 * 
 * returns: String - the same imageId 
 * list of preview search results: search results will be sorted by proximity to the given image.
 * 
 * Note the the index might contain both "previews", i.e. video file previews(image files extracted 
 * from a video file) 
 * and "files", i.e. files that were uploaded as images originally
 * THe list of results will contain both "previews" and "files" sorted by their proximity to the
 * given image. 
 */    
  def queryIndexForImage(imageId: String, indexId: String): Future[  List[PreviewFilesSearchResult]] = {
  //def queryIndexForImage(imageId: String, indexId: String): Future[(String,  List[PreviewFilesSearchResult])] = {
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")    
    val imageFileURL = client + "/api/queries/" + imageId + "?key=" + configuration.getString("commKey").get
    //
    //imageFileURL = http://localhost:9000/api/queries/52f3da24e4b0f9ce279eb9d6?key=r1ek3rs
    //         
    val host = configuration.getString("versus.host").getOrElse("")
    var queryIndexUrl = host + "/index/" + indexId + "/query"
    
    queryIndex( queryIndexUrl, imageFileURL)       
  }
  
  
  /*
 * Sends a query to an index in Versus
 * input: 
 * 		imageId - id of the image file to query against an indexer
 * 		indexId - id of the indexer against which to query the image
 * 
 * returns: 
 * 		list of preview search results: search results will be sorted by proximity to the given image.
 * 
 * Note the the indexer might contain both "previews", i.e. video file previews(image files extracted from a video file) 
 * and "files", i.e. files that were uploaded as images originally
 * the list will contain both previews 
 * 
 * THe list of results will contain both "previews" and "files" sorted by their proximity to the
 * given image.
 *  */     
   
  def queryIndex( queryIndexUrl: String, inputFileURL: String): Future[ List[PreviewFilesSearchResult]] = {
	    
     //
     //queryIndexUrl = http://localhost:8080/api/v1/index/a885bad2-f463-496f-a881-c01ebd4c31f1/query
     //will call IndexResource -> queryindex on Versus side
     //
     
    //
	//When querying for an image - download it from uploadquery.files collection on medici side:
	//inputFileURL = http://localhost:9000/api/queries/52f3da24e4b0f9ce279eb9d6?key=r1ek3rs
	//
	//When querying for a file from the db - download it from uploads.files coll on medici side:
	//inputFileURL = http://localhost:9000/api/files/52fd26fbe4b02ac3e30280db?key=r1ek3rs
	//  
     
    val responseFuture: Future[Response] = WS.url(queryIndexUrl).post(Map("infile"->Seq(inputFileURL)))   
    //mapping Future[Response] to Future[List[PreviewFilesSearchResult]]    
    //val listOfResults: Future[List[PreviewFilesSearchResult]] = responseFuture.map {
    responseFuture.map {      
      response =>
    
        Logger.debug("500 response.body = " + response.body)        
        val json: JsValue = Json.parse(response.body)
        val similarity_value = json.as[Seq[models.Result.Result]]
        //        //case class Result( val docID :String, val proximity :Double)	
        //result.docID = http://localhost:9000/api/files/52fd26fbe4b02ac3e30280db?key=r1ek3rs
        //or
        //result.docID = http://localhost:9000/api/previews/52fd1970e4b02ac3e30280a5?key=r1ek3rs    
        //        
        var resultList = new ListBuffer[PreviewFilesSearchResult]
                            
        similarity_value.map {          
          result =>
                  Logger.debug("452 result = " + result)
            
            //result.docID = http://localhost:9000/api/files/52fd26fbe4b02ac3e30280db?key=r1ek3rs
            //or
            //result.docID = http://localhost:9000/api/previews/52fd1970e4b02ac3e30280a5?key=r1ek3rs
            //        
            //parse docID to get preivew id or file id - string between '/' and '?'
            val end = result.docID.lastIndexOf("?")
            val begin = result.docID.lastIndexOf("/");                       
            val result_id = result.docID.substring(begin + 1, end);
          
            //
            //check if this is a file or a preview
            //
            val isFile = result.docID.contains("files")
            val isPreview = result.docID.contains("previews")
           // Logger.debug("478 isFile = " + isFile + "... isPreview = " + isPreview)           
            
                      
            if (isPreview){ 
              PreviewDAO.getBlob(result_id) match{                            	
                case Some (blob)=>{   
              
              	//  val (fis, filename, cont, len) = blob
              	  val filename =blob._2
              	   //use helper method to get the results
              		val oneResult = getPrevSearchResult(result_id, filename, result)           
              		resultList += oneResult                        	
              		}                                   	
              case None=>None              
              }
            }//end of if (isPreview){    
            
        if (isFile) {
              files.getFile(result_id) match {               	  
                case Some (file)=>{  
              
               	    //val thid = file.thumbnail_id
               	   
               	    //use helper method to get the results
               	    val oneResult = getFileSeachResult(result_id, file, result)
               	    resultList += oneResult                    	                  	  
               	    }               	  
              case None => {
               		  //None                
               		  Logger.debug("515 could not find file with this id " + result_id)
               	  }            	
              }
              }//end of if (isFile)        
        } // End of similarity map            
        
       
        resultList.toList
    } 
   
    }
    
  
    /*
    * Helper method. Called from queryIndex    
    */
   def getFileSeachResult(result_id:String, file:models.File, result:models.Result.Result):PreviewFilesSearchResult={    	         
		        	   //=== find list of datasets ids
         	
		  		//this file can belong to none or one or more  datsets
		  					var dataset_id_list = Dataset.findByFileId(file.id).map{
               		  		  dataset=>dataset.id.toString()               		  		  
               		  		}
  
                //Previews..............
               //
                //TODO: do we need previews value??
                //
                val pre= new HashMap[models.File, 
                             Array[(java.lang.String, String, String, String, 
                                 java.lang.String, String, Long)]]      
                val previews=pre.toMap
                ///Previews ends.........
              
                val formatter = new DecimalFormat("#.###")
                // resultArray += ((subStr, result.docID, result.proximity, file.filename,previews))
                val proxvalue = formatter.format(result.proximity).toDouble               
                  
                var thumbn_id =""
                  file.thumbnail_id match {               
                	case Some(thumb_id) => {
                	  Logger.debug("615 file.thumbnail_id = " + thumb_id)
                	  thumbn_id = thumb_id.toString
                		//thumbnail_id = id.toString()
                	}
                	case None=>{}                  
                }
                val oneResult = new PreviewFilesSearchResult("file", result_id, result.docID, 
                		proxvalue, file.filename, previews, dataset_id_list.toList, thumbn_id)
                
                oneResult                
   }
   
   /*
    * Helper method. Called from queryIndex
    */
   def getPrevSearchResult(result_id:String, filename:String, result:models.Result.Result):PreviewFilesSearchResult={
    
                Logger.debug("479 Found a preview with id = " + result_id)
                                
                //
                //TODO: do we need previews value??
                //
                val pre= new HashMap[models.File, 
                             Array[(java.lang.String, String, String, String, 
                                 java.lang.String, String, Long)]]      
                val previews=pre.toMap
               
                Logger.debug("previews = " + previews)
                //val formatter = new DecimalFormat("#.###")
                val formatter = new DecimalFormat("0.##########E0")
                
                val proxvalue = formatter.format(result.proximity).toDouble
                
                Logger.debug("411 proximity = " + result.proximity + ",proxvalue = " + proxvalue )
               
                //=== find dataset id
                val file_id = PreviewDAO.findFileId(result_id)//.getOrElse("0")		                     
                val file_id_str = file_id.getOrElse("0")		          		            
                  
                Logger.debug("626 file_id = " + file_id)
                Logger.debug("626 file_id_str = " + file_id_str)
                
                //find list of datasets ids. This preview belongs to a file
                //(video file has several previews),
                //and this file can belong to 0 or 1 or more datasets
                var dataset_id_list=new ListBuffer[String]
                for(dataset <- Dataset.findByFileId(new ObjectId(file_id_str))){		               
                	dataset_id_list+= dataset.id.toString()		             
            	}                         
               Logger.debug("746 previews datast_id_list = " +dataset_id_list )
                //thumbnail_id is only used for files. this needs to be improved.!!
                val thumbnail_id=""
                  val file = models.File
               var oneResult = new PreviewFilesSearchResult("preview", result_id,result.docID, 
                   proxvalue, filename, previews, dataset_id_list.toList, "")   
                
               oneResult   
   }


  override def onStop() {
    Logger.debug("Shutting down Versus Plugin")
  }
}

