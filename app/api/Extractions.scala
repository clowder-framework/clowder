package api

import java.io.{FileInputStream, InputStream}
import java.net.URL
import java.util.Calendar
import javax.inject.Inject

import controllers.Utils
import fileutils.FilesUtils
import models._
import play.api.Logger
import play.api.Play.{configuration, current}
import play.api.http.ContentTypes
import play.api.libs.{Files, MimeTypes}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.libs.ws.{Response, WS}
import play.api.mvc.MultipartFormData
import services._

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future


/**
 * Json API for information about extractors and extractions.
 */
class Extractions @Inject()(
  files: FileService,
  datasets: DatasetService,
  extractions: ExtractionService,
  dtsrequests: ExtractionRequestsService,
  extractors: ExtractorService,
  previews: PreviewService,
  sqarql: RdfSPARQLService,
  thumbnails: ThumbnailService,
  appConfig: AppConfigurationService) extends ApiController {

  /**
   * Uploads file for extraction and returns a file id ; It does not index the file.
   * This is very similar to upload().
   * Needs to be decided on the semantics of upload for DTS extraction service and its difference to upload file to Clowder for curation and storage.
   * This may change accordingly.
   */
  def uploadExtract(showPreviews: String = "DatasetLevel", extract: Boolean = true) = PermissionAction(Permission.AddFile)(parse.multipartFormData) { implicit request =>
    val uploadedFiles = _root_.util.FileUtils.uploadFilesMultipart(request, key="File", index=false,
      showPreviews=showPreviews, runExtractors=extract, insertDTSRequests = true, apiKey=request.apiKey)
    uploadedFiles.length match {
      case 0 => BadRequest("No files uploaded")
      case 1 => Ok(toJson(Map("id" -> uploadedFiles.head.id)))
      case _ => Ok(toJson(Map("ids" -> uploadedFiles.toList)))
    }
  }

  /**
   * Upload a file based on a url
   *
   */
  def uploadByURL(extract: Boolean = true) = PermissionAction(Permission.AddFile)(parse.json) { implicit request =>
    val uploadedFiles = _root_.util.FileUtils.uploadFilesJSON(request, key="fileurl", index=false, runExtractors=extract,
      insertDTSRequests = true, apiKey=request.apiKey)
    uploadedFiles.length match {
      case 0 => BadRequest("No fileurls uploaded")
      case 1 => Ok(toJson(Map("id" -> uploadedFiles.head.id)))
      case _ => Ok(toJson(Map("ids" -> uploadedFiles.toList)))
    }
  }

  /**
   * Multiple File Upload for a given list of files' URLs using WS API
   *
   */
  def multipleUploadByURL() = PermissionAction(Permission.AddFile).async(parse.json) { implicit request =>
      request.user match {
        case Some(user) => {
          val pageurl = request.body.\("webPageURL").as[String]
          val fileurlsjs = request.body.\("fileurls").asOpt[List[String]]
          Logger.debug("[multipleUploadByURLs] file Urls=" + fileurlsjs)
          val listURLs = fileurlsjs.getOrElse(List())
          val listIds = for {fileurl <- listURLs} yield {
            val urlsplit = fileurl.split("/")
            val filename = urlsplit(urlsplit.length - 1)
            val futureResponse = WS.url(fileurl).get()
            val fid = for {response <- futureResponse} yield {
              if (response.status == 200) {
                val inputStream: InputStream = response.ahcResponse.getResponseBodyAsStream()
                val contentLengthStr = response.header("Content-Length").getOrElse("-1")
                val contentLength = Integer.parseInt(contentLengthStr).toLong
                val file = files.save(inputStream, filename, contentLength, response.header("Content-Type"), user, null)
                file match {
                  case Some(f) => {
                    // Add new file & byte count to appConfig
                    appConfig.incrementCount('files, 1)
                    appConfig.incrementCount('bytes, f.length)
                    // notify extractors
                    current.plugin[RabbitmqPlugin].foreach {
                      // FIXME dataset not available?
                      _.fileCreated(f, None, Utils.baseUrl(request), request.apiKey)
                    }
                    /*--- Insert DTS Requests  ---*/
                    val clientIP = request.remoteAddress
                    val serverIP = request.host
                    dtsrequests.insertRequest(serverIP, clientIP, f.filename, f.id, f.contentType, f.length, f.uploadDate)
                  }
                  case None => {
                    Logger.error("Could not retrieve file that was just saved.")
                    //InternalServerError("Error uploading file")
                  }
                }
                file.map { f => (f.id.toString, fileurl) }.get
              } else {
                ("failed-" + UUID.generate.toString, fileurl)
              }
            }
            fid
          }

          for {x <- scala.concurrent.Future.sequence(listIds)} yield {
            val uuid = UUID.generate
            extractions.save(new WebPageResource(uuid, pageurl, x.toMap))
            var uuidMap = Map("id" -> uuid.toString)
            val y = uuidMap ++ x.toMap
            Ok(toJson(y))
          }
        }
        case None => Future(BadRequest(toJson("Not authorized.")))
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
  def submitExtraction(id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, id)))(parse.json) { implicit request =>
    current.plugin[RabbitmqPlugin] match {
      case Some(plugin) => {
        if (UUID.isValid(id.stringify)) {
          files.get(id) match {
            case Some(file) => {
              current.plugin[RabbitmqPlugin].foreach {
                // FIXME dataset not available?
                _.fileCreated(file, None, Utils.baseUrl(request), request.apiKey)
              }
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
   */
  def checkExtractorsStatus(id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, id))).async { implicit request =>
      current.plugin[RabbitmqPlugin] match {

        case Some(plugin) => {
          files.get(id) match {
            case Some(file) => {
              //Get the list of extractors processing the file 
              val l = extractions.getExtractorList(file.id) map {
                elist =>
                  (elist._1, elist._2)
              }
              //Get the bindings
              var blist = plugin.getBindings
              for {
                rkeyResponse <- blist
              } yield {
                val status = computeStatus(rkeyResponse, file, l)
                l += "Status" -> status
                Logger.debug(" CheckStatus: l.toString : " + l.toString)
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
  }

  /**
   * fetch the extracted metadata for the file
   * REST end-point: GET /api/extractions/:id/value
   * input: file id
   * Returns status of the extraction request and  metadata extracted so far
   *
   */
  def fetch(id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, id))).async { implicit request =>
      current.plugin[RabbitmqPlugin] match {

        case Some(plugin) => {
          if (UUID.isValid(id.stringify)) {
            files.get(id) match {
              case Some(file) => {
                Logger.debug("Getting extract info for file with id " + id)

                val l = extractions.getExtractorList(file.id) map {
                  elist => (elist._1, elist._2)
                }

                var blist = plugin.getBindings

                for {
                  rkeyResponse <- blist
                } yield {

                  val status = computeStatus(rkeyResponse, file, l)
                  val jtags = FileOP.extractTags(file)
                  val jpreviews = FileOP.extractPreviews(id)
                  val vdescriptors = files.getVersusMetadata(id) match {
                    case Some(vd) => api.routes.Files.getVersusMetadataJSON(id).toString
                    case None => ""
                  }

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
  }

  def checkExtractionsStatuses(id: models.UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, id))).async { implicit request =>
      request.user match {
        case Some(user) => {
          current.plugin[RabbitmqPlugin] match {
            case Some(plugin) => {
              Logger.debug("Inside Extraction Checkstatuses")
              val mapIdUrl = extractions.getWebPageResource(id)
              val listStatus = for {
                (fid, url) <- mapIdUrl
              } yield {
                  Logger.debug("[checkExtractionsStatuses]---fid---" + fid)
                  val statuses = files.get(UUID(fid)) match {
                    case Some(file) => {
                      //Get the list of extractors processing the file
                      val l = extractions.getExtractorList(file.id)
                      //Get the bindings
                      val blist = plugin.getBindings
                      val fstatus = for {
                        rkeyResponse <- blist
                      } yield {
                          val status = computeStatus(rkeyResponse, file, l)
                          Logger.debug(" [checkExtractionsStatuses]: l.toString : " + l.toString)
                          Map("id" -> file.id.toString, "status" -> status)
                        } //end of yield
                      fstatus
                    } //end of some file
                    case None => {
                      Future((Map("id" -> id.toString, "status" -> "No File Id found")))
                    }
                  } //end of match file
                  statuses
                } //end of outer yield
              for {
                ls <- scala.concurrent.Future.sequence(listStatus)
              } yield {
                Logger.debug("[checkExtractionsStatuses]: list statuses" + ls)
                Ok(toJson(ls))
              }
            } //rabbitmq plugin
            case None => {
              Future(Ok(toJson(Map("No Rabbitmq Service" -> ""))))
            }
          }
        } //end of match user
        case None => Future(BadRequest(toJson(Map("request" -> "Not authorized."))))
      } //user
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
         * "amq.gen-ik6RuUOEuFxyLIffVCQwSA"
         * "ncsa.cv.face"
         * "ncsa.cv.eyes"
         * "*.file.image.#" (length of array after split of this string is greater than 2)
         * we split each routing key based on period "."
         * if the length of the array after split is greater than two, and it is equal to the file content type and flag is false (not processing)
         * then the queue for the extractor is there, extractor is either busy running other job or it is not currently running.
         **/
        for (s <- rkl) {
          Logger.debug("s===== " + s)
          val x = s.split("\\.")
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

  def getExtractorServersIP() = AuthenticatedAction { implicit request =>
    val listServersIPs = extractors.getExtractorServerIPList()
    val listServersIPsJson = toJson(listServersIPs)
    Ok(Json.obj("Servers" -> listServersIPs))
  }

  def getExtractorNames(categories: List[String]) = AuthenticatedAction { implicit request =>
    val listNames = extractors.getExtractorNames(categories)
    val listNamesJson = toJson(listNames)
    Ok(toJson(Map("Extractors" -> listNamesJson)))
   }
 
  /**
   * Temporary fix for BD-289: Get Details of Extractors' Servers IP, Names and Count
   */
  def getExtractorDetails() = AuthenticatedAction { request =>
    val listNames = extractors.getExtractorDetail()
    val listNamesJson = toJson(listNames)
    Ok(listNamesJson)
  }

  def getExtractorInputTypes() = AuthenticatedAction { implicit request =>
    val listInputTypes = extractors.getExtractorInputTypes()
    val listInputTypesJson = toJson(listInputTypes)
    Ok(Json.obj("InputTypes" -> listInputTypesJson))
  }

  def getDTSRequests() = AuthenticatedAction { implicit request =>
    Logger.debug("---GET DTS Requests---")
    val list_requests = dtsrequests.getDTSRequests()
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

        }

        jarr = jarr :+ (Json.obj("clientIP" -> dtsreq.clientIP, "fileid" -> dtsreq.fileId.stringify, "filename" -> dtsreq.fileName, "fileType" -> dtsreq.fileType, "filesize" -> dtsreq.fileSize, "uploadDate" -> dtsreq.uploadDate, "extractors" -> js, "startTime" -> dtsreq.startTime, "endTime" -> dtsreq.endTime))
    }
    Ok(jarr)
  }

  def listExtractors(categories: List[String]) = AuthenticatedAction  { implicit request =>
    Ok(Json.toJson(extractors.listExtractorsInfo(categories)))
  }

  def getExtractorInfo(extractorName: String) = AuthenticatedAction { implicit request =>
    extractors.getExtractorInfo(extractorName) match {
      case Some(info) => Ok(Json.toJson(info))
      case None => NotFound(Json.obj("status" -> "KO", "message" -> "Extractor info not found"))
    }
  }

  def deleteExtractor(extractorName: String) = ServerAdminAction { implicit request =>
    extractors.deleteExtractor(extractorName)
    Ok(toJson(Map("status" -> "success")))
  }

  def addExtractorInfo() = AuthenticatedAction(parse.json) { implicit request =>

    // If repository is of type object, change it into an array.
    // This is for backward compatibility with requests from existing extractors.
    var requestJson = request.body \ "repository" match {
      case rep: JsObject => request.body.as[JsObject] ++ Json.obj("repository" ->  Json.arr(rep))
      case _ => request.body
    }

    // Validate document
    val extractionInfoResult = requestJson.validate[ExtractorInfo]

    // Update database
    extractionInfoResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toFlatJson(errors)))
      },
      info => {
        extractors.updateExtractorInfo(info) match {
          case Some(u) => Ok(Json.obj("status" -> "OK", "message" -> ("Extractor info updated. ID = " + u.id)))
          case None => BadRequest(Json.obj("status" -> "KO", "message" -> "Error updating extractor info"))
        }
      }
    )
  }

  def submitFileToExtractor(file_id: UUID) = PermissionAction(Permission.EditFile, Some(ResourceRef(ResourceRef.file,
    file_id)))(parse.json) { implicit request =>
    Logger.debug(s"Submitting file for extraction with body $request.body")
    // send file to rabbitmq for processing
    current.plugin[RabbitmqPlugin] match {
      case Some(p) =>
        files.get(file_id) match {
          case Some(file) => {
            val id = file.id
            val fileType = file.contentType
            val idAndFlags = ""

            // check that the file is ready for processing
            if (file.status.equals(models.FileStatus.PROCESSED.toString)) {
              // parameters for execution
              val parameters = (request.body \ "parameters").asOpt[JsObject].getOrElse(JsObject(Seq.empty[(String, JsValue)]))

              // Log request
              val clientIP = request.remoteAddress
              val serverIP = request.host
              dtsrequests.insertRequest(serverIP, clientIP, file.filename, id, fileType, file.length, file.uploadDate)

              val extra = Map("filename" -> file.filename,
                "parameters" -> parameters,
                "action" -> "manual-submission")
              val showPreviews = file.showPreviews

              val newFlags = if (showPreviews.equals("FileLevel"))
                idAndFlags + "+filelevelshowpreviews"
              else if (showPreviews.equals("None"))
                idAndFlags + "+nopreviews"
              else
                idAndFlags

              val originalId = if (!file.isIntermediate) {
                file.id.toString()
              } else {
                idAndFlags
              }

              var datasetId: UUID = null
              // search datasets containning this file, either directly under dataset or indirectly.
              val datasetslists:List[Dataset] = datasets.findByFileIdAllContain(file_id)
              // Note, we assume only at most one dataset will contain a given file.
              if (0 != datasetslists.length) {
                datasetId = datasetslists.head.id
              }
              // if extractor_id is not specified default to execution of all extractors matching mime type
              val key = (request.body \ "extractor").asOpt[String] match {
                case Some(extractorId) =>
                  p.submitFileManually(new UUID(originalId), file, Utils.baseUrl(request), extractorId, extra,
                    datasetId, newFlags, request.apiKey, request.user)
                case None =>
                  p.fileCreated(file, None, Utils.baseUrl(request), request.apiKey)
              }
              Ok(Json.obj("status" -> "OK"))
            } else {
              Conflict(toJson(Map("status" -> "error", "msg" -> "File is not ready. Please wait and try again.")))
            }
          }
          case None =>
            BadRequest(toJson(Map("request" -> "File not found")))
        }
      case None =>
        Ok(Json.obj("status" -> "error", "msg"-> "RabbitmqPlugin disabled"))
    }
  }

  def submitDatasetToExtractor(ds_id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset,
    ds_id)))(parse.json) { implicit request =>
    Logger.debug(s"Submitting dataset for extraction with body $request.body")
    // send file to rabbitmq for processing
    current.plugin[RabbitmqPlugin] match {
      case Some(p) =>
        datasets.get(ds_id) match {
          case Some(ds) => {
            val id = ds.id
            val host = Utils.baseUrl(request)

            // if extractor_id is not specified default to execution of all extractors matching mime type
            val key = (request.body \ "extractor").asOpt[String] match {
              case Some(extractorId) => extractorId
              case None => "unknown." + "dataset"
            }
            // parameters for execution
            val parameters = (request.body \ "parameters").asOpt[JsObject].getOrElse(JsObject(Seq.empty[(String, JsValue)]))

            val extra = Map("datasetname" -> ds.name,
              "parameters" -> parameters.toString,
              "action" -> "manual-submission")

            p.submitDatasetManually(host, key, extra, ds_id, "", request.apiKey, request.user)
            Ok(Json.obj("status" -> "OK"))
          }
          case None =>
            BadRequest(toJson(Map("request" -> "Dataset not found")))
        }
      case None =>
        Ok(Json.obj("status" -> "error", "msg"-> "RabbitmqPlugin disabled"))
    }
  }

  /*convert list of JsObject to JsArray*/
  private def getJsonArray(list: List[JsObject]): JsArray = {
    list.foldLeft(JsArray())((acc, x) => acc ++ Json.arr(x))
  }

  def cancelFileExtractionSubmission(file_id: models.UUID, msg_id: UUID) = PermissionAction(Permission.EditFile, Some(ResourceRef(ResourceRef.file,
    file_id)))(parse.json) { implicit request =>
    Logger.debug(s"Cancel file submitted extraction with body $request.body")
    // send file to rabbitmq for processing
    current.plugin[RabbitmqPlugin] match {
      case Some(p) =>
        files.get(file_id) match {
          case Some(file) => {
            // check that the file is ready for processing
            if (file.status.equals(models.FileStatus.PROCESSED.toString)) {
              (request.body \ "extractor").asOpt[String] match {
                case Some(extractorId) =>
                  p.cancelPendingSubmission(file_id, extractorId, msg_id)
                    Ok(Json.obj("status" -> "OK"))
                case None =>
                  BadRequest(toJson(Map("request" -> "extractor field not found")))
              }
            } else {
              Conflict(toJson(Map("status" -> "error", "msg" -> "File is not ready. Please wait and try again.")))
            }
          }
          case None =>
            BadRequest(toJson(Map("request" -> "File not found")))
        }
      case None =>
        Ok(Json.obj("status" -> "error", "msg"-> "RabbitmqPlugin disabled"))
    }
  }

  def cancelDatasetExtractionSubmission(ds_id: models.UUID, msg_id: UUID)= PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset,
    ds_id)))(parse.json)  { implicit request =>
    Logger.debug(s"Cancel dataset submitted extraction with body $request.body")
    current.plugin[RabbitmqPlugin] match {
      case Some(p) =>
        datasets.get(ds_id) match {
          case Some(ds) => {
            (request.body \ "extractor").asOpt[String] match {
              case Some(extractorId) =>
                p.cancelPendingSubmission(ds_id, extractorId, msg_id)
                Ok(Json.obj("status" -> "OK"))
              case None => BadRequest(toJson(Map("request" -> "extractor field not found")))
            }
          }
          case None =>
            BadRequest(toJson(Map("request" -> "File not found")))
        }
      case None =>
        Ok(Json.obj("status" -> "error", "msg"-> "RabbitmqPlugin disabled"))
    }

  }

  def addNewFilesetEvent(datasetid: String, fileids: List[String]) = AuthenticatedAction {implicit request =>
    datasets.get(UUID(datasetid)) match {
      case Some(ds) => {
        val filelist: ListBuffer[File] = ListBuffer()
        var missingfile = false
        files.get(fileids.map(fid => UUID(fid))).found.foreach(f =>
          filelist += f
        )
        if (missingfile)
          BadRequest(toJson("Not all files found"))
        else
          current.plugin[RabbitmqPlugin].foreach {
            _.fileSetAddedToDataset(ds, filelist.toList, Utils.baseUrl(request), request.apiKey)
          }
      }
      case None => BadRequest(toJson("Dataset "+datasetid+" not found"))
    }

    Ok(toJson("added new event"))
  }

}
