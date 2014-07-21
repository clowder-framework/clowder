package api

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.URL
import java.net.HttpURLConnection
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.File
import java.util.Date
import java.util.ArrayList
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.FileReader
import java.io.ByteArrayInputStream
import scala.collection.mutable.MutableList
import models._
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.libs.json.JsValue
import play.api.libs.json.Json._
import play.api.libs.ws.Response
import javax.inject.Inject
import scala.collection.mutable.ListBuffer
import org.json.JSONObject
import Transformation.LidoToCidocConvertion
import jsonutils.JsonUtil
import services._
import fileutils.FilesUtils
import controllers.Previewers
import play.api.libs.json.JsString
import scala.Some
import services.DumpOfFile
import services.ExtractorMessage
import play.api.mvc.ResponseHeader
import scala.util.parsing.json.JSONArray
import models.Preview
import play.api.mvc.SimpleResult
import models.File
import play.api.libs.json.JsObject
import play.api.Play.configuration
import com.wordnik.swagger.annotations.{ApiOperation, Api}
import services.ExtractorMessage
import scala.concurrent.Future
import scala.util.control._
import javax.activation.MimetypesFileTypeMap
import java.util.Calendar
import api.WithPermission


/**
 * Json API for Extractions for files.
 *
 * @author Smruti Padhy
 *
 */
@Api(value = "/extractions", listingPath = "/api-docs.json/extractions", description = "Extractions for Files.")
class Extractions @Inject() (
  files: FileService,
  extractions: ExtractionService,
  dtsrequests: ExtractionRequestsService,
  extractors: ExtractorService,
  previews: PreviewService,
  sqarql: RdfSPARQLService,
  thumbnails: ThumbnailService) extends ApiController {

  /**
   * Uploads file for extraction and returns a file id ; It does not index the file.
   * This is very similar to upload().
   * Needs to be decided on the semantics of upload for DTS extraction service and its difference to upload file to Medici for curation and storage.
   * This may change accordingly.
   */
  @ApiOperation(value = "Uploads a file for extraction of metadata and returns file id",
    notes = "Saves the uploaded file and sends it for extraction to Rabbitmq. Does not index the file. Same as upload() except for upload()",
    responseClass = "None", httpMethod = "POST")
  def uploadExtract(showPreviews: String = "DatasetLevel") = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreateFiles)) {
    implicit request =>

      request.user match {
        case Some(user) => {
          request.body.file("File").map {
            f =>
              var nameOfFile = f.filename
              var flags = ""
              if (nameOfFile.toLowerCase().endsWith(".ptm")) {
                var thirdSeparatorIndex = nameOfFile.indexOf("__")
                if (thirdSeparatorIndex >= 0) {
                  var firstSeparatorIndex = nameOfFile.indexOf("_")
                  var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
                  flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
                  nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
                }
              }

              Logger.debug("Uploading file " + nameOfFile)
              // store file
              val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, user, showPreviews)
              val uploadedFile = f
              file match {
                case Some(f) => {

                  val id = f.id
                  if (showPreviews.equals("FileLevel"))
                    flags = flags + "+filelevelshowpreviews"
                  else if (showPreviews.equals("None"))
                    flags = flags + "+nopreviews"
                  var fileType = f.contentType
                  if (fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")) {
                    fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")
                    if (fileType.startsWith("ERROR: ")) {
                      Logger.error(fileType.substring(7))
                      InternalServerError(fileType.substring(7))
                    }
                    if (fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped")) {
                      var thirdSeparatorIndex = nameOfFile.indexOf("__")
                      if (thirdSeparatorIndex >= 0) {
                        var firstSeparatorIndex = nameOfFile.indexOf("_")
                        var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
                        flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
                        nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
                        files.renameFile(f.id, nameOfFile)
                      }
                      files.setContentType(f.id, fileType)
                    }
                  } else if (nameOfFile.toLowerCase().endsWith(".mov")) {
                    fileType = "ambiguous/mov";
                  }

                  current.plugin[FileDumpService].foreach {
                    _.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))
                  }

                  val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")
                  // TODO RK : need figure out if we can use https
                  //val host = "http://" + request.host + request.path.replaceAll("api/files$", "")
                  val host = "http://" + request.host

                  /** Insert DTS Requests   **/

                  val clientIP = request.remoteAddress

                  val serverIP = request.host
                  dtsrequests.insertRequest(serverIP, clientIP, f.filename, id, fileType, f.length, f.uploadDate)

                  /*------------------*/

                  current.plugin[RabbitmqPlugin].foreach {
                    // TODO replace null with None
                    _.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, null, flags))
                  }

                  //for metadata files
                  if (fileType.equals("application/xml") || fileType.equals("text/xml")) {
                    val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
                    files.addXMLMetadata(id, xmlToJSON)

                    Logger.debug("xmlmd=" + xmlToJSON)

                    //add file to RDF triple store if triple store is used
                    configuration.getString("userdfSPARQLStore").getOrElse("no") match {
                      case "yes" => sqarql.addFileToGraph(f.id)
                      case _ => {}
                    }
                  }
                  Ok(toJson(Map("id" -> id.stringify)))
                }
                case None => {
                  Logger.error("Could not retrieve file that was just saved.")
                  InternalServerError("Error uploading file")
                }
              }
          }.getOrElse {
            BadRequest(toJson("File not attached."))
          }
        }

        case None => BadRequest(toJson("Not authorized."))
      }
  }

  /**
   * Upload a file based on a url
   *
   */
  @ApiOperation(value = "Uploads a file for extraction using the file's URL",
    notes = "Saves the uploaded file and sends it for extraction. Does not index the file.  ",
    responseClass = "None", httpMethod = "POST")
  def uploadByURL() = SecuredAction(authorization = WithPermission(Permission.CreateFiles)) { implicit request =>
    request.user match {
      case Some(user) => {
        val configuration = play.api.Play.configuration
        val tmpdir = configuration.getString("tmpdir").getOrElse("")

        val fileurljs = request.body.\("fileurl").asOpt[String]
        Logger.debug("[uploadURL] file Url=" + fileurljs)

        fileurljs match {

          case Some(fileurl) => {
            var fout: OutputStream = null;
            var in: BufferedInputStream = null;
            var buff = new Array[Byte](10240)
            try {
              /*
               * Downloads the file using 'fileurl' as specified in the request body
               * 
               * Gets the filename by spliting the 'fileurl' and last string being the filename
               * e.g: if fileurl is : http://isda.ncsa.illinois.edu/drupal/sites/default/files/pictures/picture.jpg, then picture.jpg is the filename
               * Opens a HTTPConnection, opens an inputstream to download the file using the given url
               * Saves the inputstream to a temporary file and names it as (tmpdir+filename) 
               * Sends it to mongodb to save the file in the database   
               * 
               */
              val urlsplit = fileurl.split("/")
              val filename = urlsplit(urlsplit.length - 1) 
              val url = new URL(fileurl)
              var len = 0
              Logger.debug("filename: " + filename)
              //Open a Httpconnection to download the file 
              val connection = url.openConnection().asInstanceOf[HttpURLConnection]
              connection.setRequestMethod("GET")
              val ct = connection.getContentType()
              val clen = connection.getContentLength()

              val name = connection.getHeaderField("Name")

              Logger.debug("content-type " + ct + " content length-" + clen)

              in = new BufferedInputStream(url.openStream())

              var fout: OutputStream = new FileOutputStream(tmpdir + filename)

              var buf = new Array[Byte](clen)
              var count = 0
              //Start reading the file from the stream and write to a 'localfile' with 'localfilename'
              while(count != -1) { 
                  Logger.trace("Inside while: before: count= " + count)
                  count = in.read(buf)
                  Logger.trace("Inside while: after: count= " + count)
                  if (count == -1) {
                    Logger.trace("Inside While even if count is -1")
                  } else {
                    Logger.trace("Trying to write into file")
                    fout.write(buf, 0, count)
                  }
                }
              
              fout.flush()
              Logger.debug("After read")

              val localfilename = tmpdir + filename

              val localfile = new java.io.File(localfilename)

              val mimetype = new MimetypesFileTypeMap().getContentType(localfile)
              var fileType = mimetype
              Logger.debug("fileType= " + fileType)
              val file = files.save(new FileInputStream(localfile), localfile.getName(), Some(ct), user, null)
              val uploadedFile = localfile

              file match {
                case Some(f) => {

                  val id = f.id
                  fileType = f.contentType
                  val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")
                  // TODO RK : need figure out if we can use https
                  Logger.debug("request.hosts=" + request.host)
                  Logger.debug("request.path=" + request.path)
                  val host = "http://" + request.host
                  Logger.debug("hosts=" + request.host)
                  current.plugin[RabbitmqPlugin].foreach { _.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, null, "")) }
                  Logger.debug("After RabbitmqPlugin")

                  /*--- Insert DTS Requests  ---*/

                  val clientIP = request.remoteAddress
                  val serverIP = request.host
                  dtsrequests.insertRequest(serverIP, clientIP, f.filename, id, fileType, f.length, f.uploadDate)

                  /*----------------------*/

                  /*file remove from temporary directory*/

                  localfile.delete()

                  Ok(toJson(Map("id" -> id.toString)))
                }
                case None => {
                  Logger.error("Could not retrieve file that was just saved.")
                  InternalServerError("Error uploading file")
                }
              } //end of file match
            } catch {
              case e: Exception =>
                println(e.printStackTrace())
                BadRequest(toJson("File not attached"))
            } finally {
              if (fout != null)
                fout.close

              if (in != null)
                in.close

            } //end of finally
          } //end of match some file url
          case None => {
            Ok("NO Url specified")
          }
        } //end of match fielurl 
      } //end of match user
      case None => BadRequest(toJson("Not authorized."))
    }
  }

  /**
   * *
   * For DTS service use case: suppose a user posts a file to the extractions API, no extractors and its corresponding queues in the Rabbitmq are available. Now she checks the status
   * for extractors, i.e., if any new extractor has subscribed to the Rabbitmq. If yes, she may again wants to submit the file for extraction again. Since she has already uploaded
   * it, this time will just uses the file id to submit the request again.
   * This API takes file id and notifies the user that the request has been sent for processing.
   * This may change depending on our our design on DTS extraction service.
   *
   */
  @ApiOperation(value = "Submites a previously uploaded file's id for extraction",
    notes = "Notifies the user that the file is sent for extraction. check the status  ",
    responseClass = "None", httpMethod = "POST")
  def submitExtraction(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) { implicit request =>
    current.plugin[RabbitmqPlugin] match {
      case Some(plugin) => {
        if (UUID.isValid(id.stringify)) {
          files.get(id) match {
            case Some(file) => {
              val fileType = file.contentType
              val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")
              val host = "http://" + request.host
              current.plugin[RabbitmqPlugin].foreach { _.extract(ExtractorMessage(id, id, host, key, Map.empty, file.length.toString, null, "")) }
              Ok("Sent for Extraction. check the status")
            }
            case None =>
              Logger.error("Could not retrieve file that was just saved.")
              InternalServerError("Error uploading file")
          } //file match
        } // if Object id
        else {
          BadRequest("Not valid id")
        }
      } //case plugin  
      case None => {
        BadRequest("No Service")
      }
    } //plugin match         
  }

  /**
   * For a given file id, checks for the status of all extractors processing that file.
   * REST endpoint  GET /api/extractions/:id/status
   * input: file id
   * returns: a list of status of all extractors responsible for extractions on the file and the final status of extraction job
   * Async is going to deprecreate in subsequent version of Play Framework. So need to change SecuredAction class to be able use the Action.async
   */
  @ApiOperation(value = "Checks for the status of all extractors processing the file with id",
    notes = " A list of status of all extractors responsible for extractions on the file and the final status of extraction job",
    responseClass = "None", httpMethod = "GET")
  def checkExtractorsStatus(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) { implicit request =>
    Async {
      current.plugin[RabbitmqPlugin] match {

        case Some(plugin) => {
          files.get(id) match {
            case Some(file) => {

              //Get the list of extractors processing the file 
              val l = extractions.getExtractorList(file.id) map {
                elist => (elist._1, elist._2)
              }

              //Get the bindings
              var blist = plugin.getBindings()

              for {
                rkeyResponse <- blist
              } yield {
                val status = computeStatus(rkeyResponse, file, l)
                l += "Status" -> status
                Logger.debug("l.toString : " + l.toString)
                Ok(toJson(l.toMap))
              } //end of yield

            } //end of some file
            case None => {
              Future(Ok("no file"))
            }
          } //end of match file
        }

        case None => {
          Future(Ok("No Rabbitmq Service"))
        }
      }
    } //Async ends
  }

  /**
   * fetch the extracted metadata for the file
   * REST end-point: GET /api/extractions/:id/value
   * input: file id
   * Returns status of the extraction request and  metadata extracted so far
   *
   */
  @ApiOperation(value = "Provides the metadata extracted from the file",
    notes = " Retruns Status of extractions and metadata extracted so far ",
    responseClass = "None", httpMethod = "GET")
  def fetch(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowFile)) { implicit request =>
    Async {
      current.plugin[RabbitmqPlugin] match {

        case Some(plugin) => {
          if (UUID.isValid(id.stringify)) {
            files.get(id) match {
              case Some(file) => {
                Logger.info("Getting extract info for file with id " + id)

                val l = extractions.getExtractorList(file.id) map {
                  elist => (elist._1, elist._2)
                }

                var blist = plugin.getBindings()

                for {
                  rkeyResponse <- blist
                } yield {

                  val status = computeStatus(rkeyResponse, file, l)

                  val jtags = FileOP.extractTags(file)

                  val jpreviews = FileOP.extractPreviews(id)

                  //val vdescriptors = FileOP.extractVersusDescriptors(id)
                  val vdescriptors=api.routes.Files.getVersusMetadataJSON(id).toString
                  Logger.debug("jtags: " + jtags.toString)
                  Logger.debug("jpreviews: " + jpreviews.toString)

                  Ok(Json.obj("file_id" -> id.stringify, "filename" -> file.filename, "Status" -> status, "tags" -> jtags, "previews" -> jpreviews, "versus descriptors url" -> vdescriptors))
                } //end of yield

              } //end of some file
              case None => {
                val error_str = "The file with id " + id + " is not found."
                Logger.error(error_str)
                Future(NotFound(toJson(error_str)))
              }
            } //end of match file
          } else {
            val error_str = "The given id " + id + " is not a valid ObjectId."
            Logger.error(error_str)
            Future(BadRequest(Json.toJson(error_str)))
          }

        }

        case None => {
          Future(Ok("No Rabbitmq Service"))
        }
      }
    } //Async 
  }

  def computeStatus(response: Response, file: models.File, l: scala.collection.mutable.Map[String, String]): String = {

    var isActivity = "false"
    extractions.findIfBeingProcessed(file.id) match {
      case false =>
      case true => isActivity = "true"
    }
    val rkeyjson = response.json
    val rkeyjsonlist = rkeyjson.as[List[JsObject]]
    val rkeylist = rkeyjsonlist.map {
      rk =>
        Logger.debug("Routing Key : " + rk \ "routing_key")
        (rk \ "routing_key").toString
    }
    var status = ""
    if (isActivity.equals("true")) {
      status = "Processing"

    } else {
      val ct = file.contentType
      val mt = ct.split("/")
      for (m <- mt)
        Logger.debug("m= " + m)

      var flag = false
      if (l.size == 0) {
        Logger.debug("Inside If")
        val rkl = rkeylist.toArray
       /**
        * Routing key lists obtained from rabbitmq binding api looks this:
        *  "amq.gen-ik6RuUOEuFxyLIffVCQwSA"
        *  "ncsa.cv.face"
        *  "ncsa.cv.eyes"
        *  "*.file.image.#" (length of array after split of this string is greater than 2)
        *  we split each routing key based on period "."
        *  if the length of the array after split is greater than two, and it is equal to the file content type and flag is false (not processing)
        *  then the queue for the extractor is there, extractor is either busy running other job or it is not currently running.
        *   */
        for (s <- rkl) {
          Logger.debug("s===== "+s)
          var x = s.split("\\.")
          if (x.length > 2) {
            if (x(2).equals(mt(0)) && !flag) {
              Logger.debug("x(2)" + x(2) + "  mt(0): " + mt(0))
              status = "Required Extractor is either busy or is not currently running. Try after some time."
              flag = true

            }
          }
        }

        if (flag == false)
          status = "No Extractor Available. Request is not queued."

      } else {
        status = "Done"

      }

    } //end of outer else
    status
  }
  
 @ApiOperation(value = "Lists servers IPs running the extractors",
    notes = "  ",
    responseClass = "None", httpMethod = "GET") 
 def getExtractorServersIP() = SecuredAction(parse.anyContent,authorization = WithPermission(Permission.Public)) {  request =>
	 
     val listServersIPs = extractors.getExtractorServerIPList()
	 val listServersIPsJson=toJson(listServersIPs)
	 Ok(Json.obj("Servers" -> listServersIPs))
      
  }
 @ApiOperation(value = "Lists the currenlty running extractors",
    notes = "  ",
    responseClass = "None", httpMethod = "GET") 
 def getExtractorNames() = SecuredAction(parse.anyContent,authorization = WithPermission(Permission.Public)) { request =>

    val listNames = extractors.getExtractorNames()
    val listNamesJson= toJson(listNames)
    Ok(toJson(Map("Extractors" -> listNamesJson)))
   }

 @ApiOperation(value = "Lists the input file format supported by currenlty running extractors",
    notes = "  ",
    responseClass = "None", httpMethod = "GET") 
 def getExtractorInputTypes() = SecuredAction(parse.anyContent,authorization = WithPermission(Permission.Public)) {  request =>

    val listInputTypes = extractors.getExtractorInputTypes()
    val listInputTypesJson= toJson(listInputTypes)
    Ok(Json.obj("InputTypes" -> listInputTypesJson))
   }
  
 @ApiOperation(value = "Lists dts extraction requests information",
    notes = "  ",
    responseClass = "None", httpMethod = "GET") 
  def getDTSRequests() = SecuredAction(parse.anyContent,authorization = WithPermission(Permission.Public)) { request =>
    Logger.debug("---GET DTS Requests---")
    var list_requests = dtsrequests.getDTSRequests()
    var startTime = models.ServerStartTime.startTime
    var currentTime = Calendar.getInstance().getTime()

    var jarr = new JsArray()
    var jsarrEx = new JsArray()

    list_requests.map {
      dtsreq =>
        var extractors1: JsValue = null
        var extractors2: List[String] = null
        var js = Json.arr()

        if (dtsreq.extractors != None) {
          Logger.debug("----Inside dts requests----")
          extractors1 = Json.parse(com.mongodb.util.JSON.serialize(dtsreq.extractors.get))
          extractors2 = extractors1.as[List[String]]
          Logger.debug("Extractors2:" + extractors2)
          extractors2.map {
            ex =>
              js = js :+ toJson(ex)
          }

        } else {
          Logger.debug("----Else block")
        }

        jarr = jarr :+ (Json.obj("clientIP" -> dtsreq.clientIP, "fileid" -> dtsreq.fileId.stringify, "filename" -> dtsreq.fileName, "fileType" -> dtsreq.fileType, "filesize" -> dtsreq.fileSize, "uploadDate" -> dtsreq.uploadDate, "extractors" -> js, "startTime" -> dtsreq.startTime, "endTime" -> dtsreq.endTime))
    }

    Ok(jarr)
   
  }
  /*convert list of JsObject to JsArray*/
def getJsonArray(list: List[JsObject]): JsArray = {
    list.foldLeft(JsArray())((acc, x) => acc ++ Json.arr(x))
  } 
  

}