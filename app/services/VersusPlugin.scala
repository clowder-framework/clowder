package services

import play.api.{ Plugin, Logger, Application }
import java.util.Date
import play.api.libs.json.Json
import play.api.Play.current
import play.api.libs.ws.WS
import play.api.libs.ws.Response
import play.api.libs.concurrent.Promise
import java.io._
import models.FileMD
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
import models.File
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import play.api.libs.json.Json._
import scala.concurrent.{future, blocking, Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import play.libs.Akka
import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import models.PreviewDAO
import controllers.Previewers
import controllers.routes
import java.text.DecimalFormat
import scala.collection.mutable.ArrayBuffer
import models.FileDAO
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
            files.get(fileid) match {

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
  
/*
 * sends a query  for an index in Versus
 */
  def queryIndex(id: String, indxId: String): Future[(String, scala.collection.mutable.ArrayBuffer[(String, String, Double, String, Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])])] = {
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")

    val indexId = indxId
    val query = client + "/api/queries/" + id + "?key=" + configuration.getString("commKey").get

    val host = configuration.getString("versus.host").getOrElse("")

    var queryurl = host + "/index/" + indexId + "/query"

    val resultFuture: Future[Response] = WS.url(queryurl).post(Map("infile" -> Seq(query)))

    resultFuture.map {
      response =>
        val json: JsValue = Json.parse(response.body)
        val similarity_value = json.as[Seq[models.Result.Result]]

        val len = similarity_value.length
        // val ar = new Array[String](len)
        val se = new Array[(String, String, Double, String)](len)

       //var resultArray = new ArrayBuffer[(String, String, Double, String)]()
       var resultArray = new ArrayBuffer[(String, String, Double, String, Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])]()
       
        var pre= new HashMap[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]]
        var i = 0
        similarity_value.map {
          result =>
            val end = result.docID.lastIndexOf("?")
            val begin = result.docID.lastIndexOf("/");
            val subStr = result.docID.substring(begin + 1, end);
            Logger.debug("subStr="+ subStr)
            //    val a = result.docID.split("/")
            //  val n = a.length - 2
            
          //-----  
           // This code is for testing query for frames from a video
           //TODO: Improve this code  
          /*  PreviewDAO.getBlob(subStr) match{
              case Some(b)=>{
                Logger.debug("Preview id : "+ subStr+" filename : "+b._2)
                         
                val previews=pre
               
                val formatter = new DecimalFormat("#.###")
                  val proxvalue = formatter.format(result.proximity).toDouble
                resultArray += ((subStr, result.docID, proxvalue, b._2, previews))
                Logger.debug("IndxId=" + indexId + " resultArray=(" + subStr + " , " + result.proximity + ", " + b._2 + ")\n")
                i = i + 1
              }
                
              
                
              case None=>None
            }*/
            
          //-----------------------  
            files.get(subStr) match {
               	  case Some(file)=>{
		        	   // se.update(i,(a(n),result.docID,result.proximity,file.filename))

                //Previews..............
                val previewsFromDB = PreviewDAO.findByFileId(file.id)
                val previewers = Previewers.findPreviewers
                //Logger.info("Number of previews " + previews.length);
                val files = List(file)
                val previewslist = for (f <- files) yield {
                  val pvf = for (p <- previewers; pv <- previewsFromDB; if (p.contentType.contains(pv.contentType))) yield {
                    (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id.toString).toString, pv.contentType, pv.length)
                  }
                  if (pvf.length > 0) {
                    (file -> pvf)
                  } else {
                    val ff = for (p <- previewers; if (p.contentType.contains(file.contentType))) yield {
                      (file.id.toString, p.id, p.path, p.main, routes.Files.file(file.id.toString) + "/blob", file.contentType, file.length)
                    }
                    (file -> ff)
                  }
                }
                val previews = Map(previewslist: _*)
                ///Previews ends.........

                //resultArray += ((subStr, result.docID, result.proximity, file.filename))
                //resultArray += ((subStr, result.docID, result.proximity, file.filename,previews))
                val formatter = new DecimalFormat("#.###")
                // resultArray += ((subStr, result.docID, result.proximity, file.filename,previews))
                val proxvalue = formatter.format(result.proximity).toDouble
                resultArray += ((subStr, result.docID, proxvalue, file.filename, previews))

                //     ar.update(i, file.filename)

                //Logger.debug("i"+i +" name="+ar(i)+"se(i)"+se(i)._3)
                Logger.debug("IndxId=" + indexId + " resultArray=(" + subStr + " , " + result.proximity + ", " + file.filename + ")\n")
                i = i + 1
              }
              case None => None

            }

        } // End of similarity map
        //se
        (indexId, resultArray)
    }
  }

  /*
   * Sends an query (a file from the medici database) to Versus
   */
  
  //Query index for a file from  the database 
  def queryIndexFile(id: String, indxId: String): Future[(String, scala.collection.mutable.ArrayBuffer[(String, String, Double, String, Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])])] = {
    val configuration = play.api.Play.configuration

    val client = configuration.getString("versus.client").getOrElse("")

    val indexId = indxId

    val query = client + "/api/files/" + id + "?key=" + configuration.getString("commKey").get
    val host=configuration.getString("versus.host").getOrElse("")
   
    var queryurl = host + "/index/" + indexId + "/query"
    
        val resultFuture: Future[Response]= WS.url(queryurl).post(Map("infile" -> Seq(query)))
               
        resultFuture.map{
      response =>
        val json: JsValue = Json.parse(response.body)
        val similarity_value = json.as[Seq[models.Result.Result]]

        val len = similarity_value.length
        // val ar = new Array[String](len)
        val se = new Array[(String, String, Double, String)](len)
		        
        //var resultArray = new ArrayBuffer[(String, String, Double, String)]()
        var resultArray = new ArrayBuffer[(String, String, Double, String,Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])]()
		var i=0
        similarity_value.map {
          result =>
            val end = result.docID.lastIndexOf("?")
            val begin = result.docID.lastIndexOf("/");
            val subStr = result.docID.substring(begin + 1, end);
        //    val a = result.docID.split("/")
          //  val n = a.length - 2
            files.get(subStr) match {
		        	  case Some(file)=>{

       
                // se.update(i,(a(n),result.docID,result.proximity,file.filename))
                //Previews..............
                val previewsFromDB = PreviewDAO.findByFileId(file.id)
                val previewers = Previewers.findPreviewers
                //Logger.info("Number of previews " + previews.length);
                val files = List(file)
                val previewslist = for (f <- files) yield {
                  val pvf = for (p <- previewers; pv <- previewsFromDB; if (p.contentType.contains(pv.contentType))) yield {
                    (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id.toString).toString, pv.contentType, pv.length)
                  }
                  if (pvf.length > 0) {
                    (file -> pvf)
                  } else {
                    val ff = for (p <- previewers; if (p.contentType.contains(file.contentType))) yield {
                      (file.id.toString, p.id, p.path, p.main, routes.Files.file(file.id.toString) + "/blob", file.contentType, file.length)
                    }
                    (file -> ff)
                  }
                }
                val previews = Map(previewslist: _*)
                ///Previews ends.........

                //resultArray += ((subStr, result.docID, result.proximity, file.filename))
                val formatter = new DecimalFormat("#.###")
                // resultArray += ((subStr, result.docID, result.proximity, file.filename,previews))
                val proxvalue = formatter.format(result.proximity).toDouble
                resultArray += ((subStr, result.docID, proxvalue, file.filename, previews))

                //     ar.update(i, file.filename)
                //Logger.debug("i"+i +" name="+ar(i)+"se(i)"+se(i)._3)

                Logger.debug("IndxId=" + indexId + " resultArray=(" + subStr + " , " + result.proximity + ", " + file.filename + ")\n")
                i = i + 1
              }
              case None => None

            }
            


        } // End of similarity map
        //se
        (indexId, resultArray)

    }
  }

  /*
   * Sends an url of an image as a query to Versus
   */
  def queryURL(url: String, indxId: String): Future[(String, ArrayBuffer[(String, String, Double, String, Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])])] = {

    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")

    val indexId = indxId

    // val query = client + "/api/queries/" + indxId + "?key=letmein"
    val query = url
    val host = configuration.getString("versus.host").getOrElse("")

    var queryurl = host + "/index/" + indexId + "/query"

    val resultFuture: Future[Response] = WS.url(queryurl).post(Map("infile" -> Seq(query)))

    resultFuture.map {
      response =>
        val json: JsValue = Json.parse(response.body)
        val similarity_value = json.as[Seq[models.Result.Result]]

        val len = similarity_value.length
        // val ar = new Array[String](len)
        val se = new Array[(String, String, Double, String)](len)

        //var resultArray = new ArrayBuffer[(String, String, Double, String)]()
        var resultArray = new ArrayBuffer[(String, String, Double, String, Map[models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]])]()
        var i = 0
        similarity_value.map {
          result =>
            val end = result.docID.lastIndexOf("?")
            val begin = result.docID.lastIndexOf("/");
            val subStr = result.docID.substring(begin + 1, end);

        //    val a = result.docID.split("/")
          //  val n = a.length - 2
            files.get(subStr) match {
              case Some(file) => {
                // se.update(i,(a(n),result.docID,result.proximity,file.filename))
                //Previews..............
                val previewsFromDB = PreviewDAO.findByFileId(file.id)
                val previewers = Previewers.findPreviewers
                //Logger.info("Number of previews " + previews.length);
                val files = List(file)
                val previewslist = for (f <- files) yield {
                  val pvf = for (p <- previewers; pv <- previewsFromDB; if (p.contentType.contains(pv.contentType))) yield {
                    (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id.toString).toString, pv.contentType, pv.length)
                  }
                  if (pvf.length > 0) {
                    (file -> pvf)
                  } else {
                    val ff = for (p <- previewers; if (p.contentType.contains(file.contentType))) yield {
                      (file.id.toString, p.id, p.path, p.main, routes.Files.file(file.id.toString) + "/blob", file.contentType, file.length)
                    }
                    (file -> ff)
                  }
                }
                val previews = Map(previewslist: _*)
                ///Previews ends.........

                //resultArray += ((subStr, result.docID, result.proximity, file.filename))
                //resultArray += ((subStr, result.docID, result.proximity, file.filename,previews))
                val formatter = new DecimalFormat("#.###")
                // resultArray += ((subStr, result.docID, result.proximity, file.filename,previews))
                val proxvalue = formatter.format(result.proximity).toDouble
                resultArray += ((subStr, result.docID, proxvalue, file.filename, previews))

                //     ar.update(i, file.filename)
                //Logger.debug("i"+i +" name="+ar(i)+"se(i)"+se(i)._3)
                Logger.debug("IndxId=" + indexId + " resultArray=(" + subStr + " , " + result.proximity + ", " + file.filename + ")\n")
                i = i + 1
              }
              case None => None

     }
  
        } // End of similarity map
        //se
        (indexId, resultArray)
    }
  }


  override def onStop() {
    Logger.debug("Shutting down Versus Plugin")
  }
}

