package controllers

import java.io.InputStreamReader
import java.io.BufferedReader
import java.util.Date
import javax.inject.Inject
import api.{UserRequest, Permission}
import models._
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import org.json.JSONArray
import play.api.Logger
import play.api.data.{Forms, Form}
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.json.JsArray
import play.libs.F.Promise
import services._
import _root_.util.RequiredFieldsConfig
import play.api.Play._
import org.apache.http.client.methods.HttpPost
import scala.concurrent.Future
import scala.concurrent.Await
import play.api.mvc.{Action, AnyContent, Results}
import play.api.libs.ws._
import play.api.libs.ws.WS._
import play.api.libs.functional.syntax._


import scala.concurrent.duration._
import play.api.libs.json.Reads._
import play.api.libs.json.JsPath.readNullable
import java.net.URI

/**
 * Methods for interacting with the curation objects in the staging area.
 */
class CurationObjects @Inject()(
  curations: CurationService,
  datasets: DatasetService,
  collections: CollectionService,
  spaces: SpaceService,
  files: FileService,
  comments: CommentService,
  sections: SectionService,
  events: EventService,
  userService: UserService
  ) extends SecuredController {

  def newCO(datasetId:UUID, spaceId:String) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    implicit val user = request.user
    val (name, desc, spaceByDataset) = datasets.get(datasetId) match {
      case Some(dataset) => (dataset.name, dataset.description, dataset.spaces map( id => spaces.get(id)) filter(_ != None)
        filter (space => Permission.checkPermission(Permission.EditStagingArea, ResourceRef(ResourceRef.space, space.get.id)))map(_.get))
      case None => ("", "", List.empty)
    }
    //default space is the space from which user access to the dataset
    val defaultspace = spaceId match {
      case "" => {
        if(spaceByDataset.length ==1) {
          spaceByDataset.lift(0)
        } else {
          None
        }
      }
      case _ => spaces.get(UUID(spaceId))
    }

    Ok(views.html.curations.newCuration(datasetId, name, desc, defaultspace, spaceByDataset, RequiredFieldsConfig.isNameRequired,
      RequiredFieldsConfig.isDescriptionRequired))
  }

  /**
   * Controller flow to create a new curation object. On success,
   * the browser is redirected to the new Curation page.
   */
  def submit(datasetId:UUID, spaceId:UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId))) (parse.multipartFormData)  { implicit request =>

    //get name, des, space from request
    var COName = request.body.asFormUrlEncoded.getOrElse("name", null)
    var CODesc = request.body.asFormUrlEncoded.getOrElse("description", null)

    implicit val user = request.user
    user match {
      case Some(identity) => {

        datasets.get(datasetId) match {
          case Some(dataset) => {
            // val spaceId = UUID(COSpace(0))
            if (spaces.get(spaceId) != None) {

              //copy file list from FileDAO.
              var newFiles: List[File]= List.empty
              for ( file <- dataset.files) {
                files.get(file.id) match{
                  case Some(f) => {
                    newFiles =  f :: newFiles
                  }
                }
              }
              //this line can actually be removed since we are not using dataset.files to get file's info.
              //Just to keep consistency
              var newDataset = dataset.copy(files = newFiles)

              //the model of CO have multiple datasets and collections, here we insert a list containing one dataset
              val newCuration = CurationObject(
                name = COName(0),
                author = identity,
                description = CODesc(0),
                created = new Date,
                submittedDate = None,
                publishedDate= None,
                space = spaceId,
                datasets = List(newDataset),
                files = newFiles,
                repository = None,
                status = "In Curation"
              )

              // insert curation
              Logger.debug("create curation object: " + newCuration.id)
              curations.insert(newCuration)

              Redirect(routes.CurationObjects.getCurationObject(newCuration.id))
            }
            else {
              InternalServerError("Space not found")
            }
          }
          case None => InternalServerError("Dataset Not found")
        }
      }
      case None => InternalServerError("User Not found")
    }
  }

  /**
   * Delete curation object.
   */
  def deleteCuration(id: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, id))) {
    implicit request =>
      implicit val user = request.user

      curations.get(id) match {
        case Some(c) => {
          Logger.debug("delete Curation object: " + c.id)
          val spaceId = c.space
          curations.remove(id)
          //spaces.get(spaceId) is checked in Space.stagingArea
          Redirect(routes.Spaces.stagingArea(spaceId))
        }
        case None => InternalServerError("Curation Object Not found")
      }
  }

  // This function is actually "updateDatasetUserMetadata", it can rewrite the metadata in curation.dataset and add/ modify/ delte
  // is all done in this function. We use addDatasetUserMetadata to keep consistency with live objects
  def addDatasetUserMetadata(id: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, id))) (parse.json) { implicit request =>
    implicit val user = request.user
    Logger.debug(s"Adding user metadata to curation's dataset $id")



    curations.get(id) match {
      case Some(c) => {
        if (c.status == "In Curation") {
          // write metadata to the collection "curationObjects"
          curations.addDatasetUserMetaData(id, Json.stringify(request.body))
          //add event
          events.addObjectEvent(user, id, c.name, "addMetadata_curation")
        } else {
          InternalServerError("Curation Object already submitted")
        }
      }
    }

    //datasets.index(id)
    //    configuration.getString("userdfSPARQLStore").getOrElse("no") match {
    //      case "yes" => datasets.setUserMetadataWasModified(id, true)
    //      case _ => Logger.debug("userdfSPARQLStore not enabled")
    //    }
    Ok(toJson(Map("status" -> "success")))
  }


  def getFiles(curation: CurationObject, dataset: Dataset): List[File] ={
    curation.files filter (f => (dataset.files map (_.id)) contains  (f.id))
  }

  def addFileUserMetadata(curationId:UUID, fileId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId)))  (parse.json) { implicit request =>
    implicit val user = request.user

    curations.get(curationId) match {
      case Some(c) => {
        if (c.status == "In Curation") {
          val newFiles: List[File]= c.files
          val index = newFiles.indexWhere(_.id.equals(fileId))
          Logger.debug(s"Adding user metadata to curation's file No." + index )
          // write metadata to curationObjects
          curations.addFileUserMetaData(curationId, index, Json.stringify(request.body))
          //add event
          events.addObjectEvent(user, curationId, c.name, "addMetadata_curation")
        } else {
          InternalServerError("Curation Object already submitted")
        }}
      case None => InternalServerError("Curation Object Not found")
    }

    Ok(toJson(Map("status" -> "success")))
  }


  def getCurationObject(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {    implicit request =>
    implicit val user = request.user
    curations.get(curationId) match {
      case Some(c) => {
        val ds: Dataset = c.datasets(0)
        //dsmetadata is immutable but dsUsrMetadata is mutable
        val dsmetadata = ds.metadata
        val dsUsrMetadata = collection.mutable.Map(ds.userMetadata.toSeq: _*)
        val isRDFExportEnabled = current.plugin[RDFExportService].isDefined
        val fileByDataset = getFiles(c, ds)
        if (c.status != "In Curation") {
          Ok(views.html.spaces.submittedCurationObject(c, ds, fileByDataset))
        } else {
          Ok(views.html.spaces.curationObject(c, dsmetadata, dsUsrMetadata, isRDFExportEnabled, fileByDataset))
        }
      }
      case None => InternalServerError("Curation Object Not found")
    }
  }

  def findMatchingRepositories(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user
          curations.get(curationId) match {
            case Some(c) => {
              val propertiesMap: Map[String, List[String]] = Map( "Access" -> List("Open", "Restricted", "Embargo", "Enclave"),
                "License" -> List("Creative Commons", "GPL") , "Cost" -> List("Free", "$XX Fee"),
                "Organizational Affiliation" -> List("UMich", "IU", "UIUC"))
              val mmResp = callMatchmaker(c, Utils.baseUrl(request))
              user match {
                case Some(usr) => {
                  val repPreferences = usr.repositoryPreferences.map{ value => value._1 -> value._2.toString().split(",").toList}
                  Ok(views.html.spaces.matchmakerResult(c, propertiesMap, repPreferences, mmResp))
                }
                case None =>Results.Redirect(routes.RedirectUtility.authenticationRequiredMessage("You must be logged in to perform that action.", request.uri ))
              }
            }
            case None => InternalServerError("Curation Object not found")
          }
  }

  def callMatchmaker(c: CurationObject, hostIp: String ): List[MatchMakerResponse] = {
    val hostUrl = hostIp + "/api/curations/" + c.id + "/ore#aggregation"
    val userPrefMap = userService.findByIdentity(c.author).map(usr => usr.repositoryPreferences.map( pref => pref._1-> Json.toJson(pref._2.toString().split(",").toList))).getOrElse(Map.empty)
    val userPreferences = userPrefMap + ("Repository" -> Json.toJson(c.repository))
    val maxDataset = if (!c.files.isEmpty)  c.files.map(_.length).max else 0
    val totalSize = if (!c.files.isEmpty) c.files.map(_.length).sum else 0
    val valuetoSend = Json.obj(
      "@context" -> Json.toJson("https://w3id.org/ore/context"),
      "Aggregation" ->
        Map(
          "Identifier" -> Json.toJson(hostIp +"/api/curations/" + c.id),
          "@id" -> Json.toJson(hostUrl),
          "Title" -> Json.toJson(c.name),

          "Creator" -> Json.toJson(userService.findByIdentity(c.author).map ( usr => usr.profile.map(prof => prof.orcidID.map(oid=> oid)))),
          "similarTo" -> Json.toJson(hostIp + "/datasets/" + c.datasets(0).id)

        ),
      "Preferences" -> userPreferences ,
      "Aggregation Statistics" ->
        Map(
          "Max Collection Depth" -> Json.toJson("1"),
          "Data Mimetypes" -> Json.toJson(c.files.map(_.contentType).toSet),
          "Max Dataset Size" -> Json.toJson(maxDataset.toString),
          "Total Size" -> Json.toJson(totalSize.toString)
        )
    )
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val endpoint = play.Play.application().configuration().getString("matchmaker.uri").replaceAll("/$","")
    val futureResponse = WS.url(endpoint).post(valuetoSend)


    var jsonResponse: play.api.libs.json.JsValue = new JsArray()
    val result = futureResponse.map {
      case response =>
        if(response.status >= 200 && response.status < 300 || response.status == 304) {
          jsonResponse = response.json
        }
        else {
          Logger.error("Error Calling Matchmaker: " + response.getAHCResponse.getResponseBody())
        }
    }

    val rs = Await.result(result, Duration.Inf)

    jsonResponse.as[List[MatchMakerResponse]]
  }

  def compareToRepository(curationId: UUID, repository: String) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user

       curations.get(curationId) match {
         case Some(c) => {
           curations.updateRepository(c.id, repository)
           //TODO: Make some call to C3-PR?
           //  Ok(views.html.spaces.matchmakerReport())
           val propertiesMap: Map[String, List[String]] = Map("Content Types" -> List("Images", "Video"),
             "Dissemination Control" -> List("Restricted Use", "Ability to Embargo"),"License" -> List("Creative Commons", "GPL") ,
             "Organizational Affiliation" -> List("UMich", "IU", "UIUC"))

           Ok(views.html.spaces.curationDetailReport( c, propertiesMap, repository))
        }
        case None => InternalServerError("Space not found")
      }
  }

  def sendToRepository(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user

      curations.get(curationId) match {
        case Some(c) =>
          var success = false
          var repository: String = ""
          c.repository match {
            case Some (s) => repository = s
            case None => Ok(views.html.spaces.curationSubmitted( c, "No Repository Provided", success))
          }
          val hostIp = Utils.baseUrl(request)
          val hostUrl = hostIp + "/api/curations/" + curationId + "/ore#aggregation"
          val userPrefMap = userService.findByIdentity(c.author).map(usr => usr.repositoryPreferences.map( pref => pref._1-> Json.toJson(pref._2.toString().split(",").toList))).getOrElse(Map.empty)
          val userPreferences = userPrefMap + ("Repository" -> Json.toJson(repository))
          val maxDataset = if (!c.files.isEmpty)  c.files.map(_.length).max else 0
          val totalSize = if (!c.files.isEmpty) c.files.map(_.length).sum else 0
          val valuetoSend = Json.toJson(
            Map(
              "@context" -> Json.toJson(Seq(
                Json.toJson("https://w3id.org/ore/context"),
                Json.toJson(
                  Map(
                    "Identifier" -> Json.toJson("http://purl.org/dc/elements/1.1/identifier"),
                    "License" -> Json.toJson("http://purl.org/dc/terms/license"),
                    "Rights Holder" -> Json.toJson("http://purl.org/dc/terms/rightsHolder"),
                    "Rights" -> Json.toJson("http://purl.org/dc/terms/rights"),
                    "Date" -> Json.toJson("http://purl.org/dc/elements/1.1/date"),
                    "Creation Date" -> Json.toJson("http://purl.org/dc/terms/created"),
                    "Size" -> Json.toJson("tag:tupeloproject.org,2006:/2.0/files/length"),
                    "Label" -> Json.toJson("http://www.w3.org/2000/01/rdf-schema#label"),
                    "Mimetype" -> Json.toJson("http://purl.org/dc/elements/1.1/format"),
                    "Description" -> Json.toJson("http://purl.org/dc/elements/1.1/description"),
                    "Title" -> Json.toJson("http://purl.org/dc/elements/1.1/title"),
                    "Uploaded By" -> Json.toJson("http://purl.org/dc/elements/1.1/creator"),
                    "Abstract" -> Json.toJson("http://purl.org/dc/terms/abstract"),
                    "Contact" -> Json.toJson("http://sead-data.net/terms/contact"),
                    "Creator" -> Json.toJson("http://purl.org/dc/terms/creator"),
                    "Publication Date" -> Json.toJson("http://purl.org/dc/terms/issued"),
                    "GeoPoint" ->
                      Json.toJson(
                        Map(

                          "@id" -> Json.toJson("tag:tupeloproject.org,2006:/2.0/gis/hasGeoPoint"),
                          "long" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#long"),
                          "lat" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#lat")
                    )),
                    "Comment" -> Json.toJson(Map(

                      "comment_body" -> Json.toJson("http://purl.org/dc/elements/1.1/description"),
                      "comment_date" -> Json.toJson("http://purl.org/dc/elements/1.1/date"),
                      "@id" -> Json.toJson("http://cet.ncsa.uiuc.edu/2007/annotation/hasAnnotation"),
                      "comment_author" -> Json.toJson("http://purl.org/dc/elements/1.1/creator")
                    )),
                    "Where" -> Json.toJson("http://sead-data.net/vocab/demo/where"),
                    "Has Description" -> Json.toJson("http://purl.org/dc/terms/description"),
                    "Experiment Site" -> Json.toJson("http://sead-data.net/terms/odm/location"),
                    "Experimental Method" -> Json.toJson("http://sead-data.net/terms/odm/method"),
                    "Primary Source" -> Json.toJson("http://www.w3.org/ns/prov#hadPrimarySource"),
                    "Topic" -> Json.toJson("http://purl.org/dc/terms/subject"),
                    "Audience" -> Json.toJson("http://purl.org/dc/terms/audience"),
                    "Bibliographic citation" -> Json.toJson("http://purl.org/dc/terms/bibliographicCitation"),
                    "Coverage" -> Json.toJson("http://purl.org/dc/terms/coverage"),
                    "Published In" -> Json.toJson("http://purl.org/dc/terms/isPartOf"),
                    "Publisher" -> Json.toJson("http://purl.org/dc/terms/publisher"),
                    "Quality Control Level" -> Json.toJson("http://sead-data.net/terms/odm/QualityControlLevel"),
                    "Who" -> Json.toJson("http://sead-data.net/vocab/demo/creator"),
                    "Alternative title" -> Json.toJson("http://purl.org/dc/terms/alternative"),
                    "External Identifier" -> Json.toJson("http://purl.org/dc/terms/identifier"),
                    "Proposed for Publication " -> Json.toJson("http://sead-data.net/terms/ProposedForPublication"),
                    "Start/End Date" -> Json.toJson("http://purl.org/dc/terms/temporal"),
                    "duplicates" -> Json.toJson("http://cet.ncsa.uiuc.edu/2007/mmdb/duplicates"),
                    "is derived from" -> Json.toJson("http://www.w3.org/ns/prov#wasDerivedFrom"),
                    "describes" -> Json.toJson("http://cet.ncsa.uiuc.edu/2007/mmdb/describes"),
                    "has newer version" -> Json.toJson("http://www.w3.org/ns/prov/#hadRevision"),
                    "relates to" -> Json.toJson("http://cet.ncsa.uiuc.edu/2007/mmdb/relatesTo"),
                    "references" -> Json.toJson("http://purl.org/dc/terms/references"),
                    "is referenced by" -> Json.toJson("http://purl.org/dc/terms/isReferencedBy"),
                    "has derivative" -> Json.toJson("http://www.w3.org/ns/prov/#hadDerivation"),
                    "has prior version" -> Json.toJson("http://www.w3.org/ns/prov#wasRevisionOf"),
                    "keyword" -> Json.toJson("http://www.holygoat.co.uk/owl/redwood/0.1/tags/taggedWithTag"),
                    "Is Version Of" -> Json.toJson("http://purl.org/dc/terms/isVersionOf"),
                    "Has Part" -> Json.toJson("http://purl.org/dc/terms/hasPart"),
                    "Aggregation Statistics" -> Json.toJson("http://purl.org/dc/terms/hasPart"),
                    "Repository" -> Json.toJson("http://purl.org/dc/terms/hasPart"),
                    "Preferences" -> Json.toJson("http://purl.org/dc/terms/hasPart"),
                    "Aggregation" -> Json.toJson("http://purl.org/dc/terms/hasPart")

                  )
                )

              )),

                "Repository" -> Json.toJson(repository.toLowerCase()),
                "Preferences" -> Json.toJson(
                  userPreferences
                ),
                "Aggregation" -> Json.toJson(
                  Map(
                    "Identifier" -> Json.toJson(curationId),
                    "@id" -> Json.toJson(hostUrl),
                    "Title" -> Json.toJson(c.name),
                    "Abstract" -> Json.toJson(c.datasets(0).userMetadata.get("Abstract").getOrElse("").toString),
                    "Creator" -> Json.toJson(userService.findByIdentity(c.author).map(usr => JsArray(Seq(Json.toJson(usr.fullName + ": " + hostIp + "/profile/viewProfile/" + usr.id), Json.toJson(usr.profile.map(prof => prof.orcidID.map(oid=> oid)))))))
                  )
                ),
                "Aggregation Statistics" -> Json.toJson(
                  Map(
                    "Max Collection Depth" -> Json.toJson("1"),
                    "Data Mimetypes" -> Json.toJson(c.files.map(_.contentType).toSet),
                    "Max Dataset Size" -> Json.toJson(maxDataset.toString),
                    "Total Size" -> Json.toJson(totalSize.toString)
                  )),
                "Publication Callback" -> Json.toJson(hostIp + "/spaces/curations/" + c.id + "/status")
              )
            )
          Logger.debug("Submitting request for publication: " + valuetoSend)

          implicit val context = scala.concurrent.ExecutionContext.Implicits.global
          val endpoint =play.Play.application().configuration().getString("stagingarea.uri").replaceAll("/$","")
          val futureResponse = WS.url(endpoint).post(valuetoSend)
          var jsonResponse: play.api.libs.json.JsValue = new JsArray()
          val result = futureResponse.map {
            case response =>
              if(response.status >= 200 && response.status < 300 || response.status == 304) {
                curations.setSubmitted(c.id)
                jsonResponse = response.json
                success = true
              }
              else {

                Logger.error("Error Submitting to Repository: " + response.getAHCResponse.getResponseBody())
              }
          }

          val rs = Await.result(result, Duration.Inf)

          Ok(views.html.spaces.curationSubmitted( c, repository, success))
      }
  }

  /**
   * Endpoint for receiving status/ uri from repository.
   */
  def savePublishedObject(id: UUID) = UserAction (parse.json) {
    implicit request =>
      Logger.debug("get infomation from repository")

      curations.get(id) match {

        case Some(c) => {
          c.status match {

            case "In Curation" => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Curation object hasn't been submitted yet.")))
            //sead2 receives status once from repository,
            case "Published" | "ERROR" | "Reject" => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Curation object already received status from repository.")))
            case "Submitted" => {
              //parse status from request's body
              val statusList = (request.body \ "status").asOpt[String]
              if (statusList.size == 1) {

                statusList.map {
                  status =>
                    if (status.compareToIgnoreCase("Published") == 0 || status.compareToIgnoreCase("Publish") == 0) {
                      curations.setPublished(id)
                    } else {
                      //other status except Published, such as ERROR, Rejected
                      curations.updateStatus(id, status)
                    }

                }
                (request.body \ "uri").asOpt[String].map {
                  externalIdentifier => {
                    if (externalIdentifier.startsWith("doi:") || externalIdentifier.startsWith("10.")){
                      val DOI_PREFIX = "http://dx.doi.org/"
                      curations.updateExternalIdentifier(id, new URI(DOI_PREFIX + externalIdentifier.replaceAll("^doi:", "")))
                    } else {
                      curations.updateExternalIdentifier(id, new URI(externalIdentifier))
                    }
                  }
                }

                Ok(toJson(Map("status" -> "OK")))
              } else {
                BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Receive none / multiple statuses from request.")))
              }
            }
            case _ => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Curation object has unrecognized status .")))

          }
        }
        case None => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Curation object not found.")))
      }
  }


  /**
   * Endpoint for getting status from repository.
   */
  def getStatusFromRepository (id: UUID)  = Action.async { implicit request =>
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    curations.get(id) match {

      case Some(c) => {

        val endpoint = play.Play.application().configuration().getString("stagingarea.uri").replaceAll("/$", "") + "/" +id.toString()
        Logger.debug(endpoint)
        val futureResponse = WS.url(endpoint).get()

        futureResponse.map{
          case response =>
            if(response.status >= 200 && response.status < 300 || response.status == 304) {
              (response.json \ "Status").asOpt[JsValue]
              Ok(response.json)
            } else {
              Logger.error("Error Getting Status: " + response.getAHCResponse.getResponseBody)
              InternalServerError(toJson("Status object not found."))
            }
        }
      }
      case None => Future(InternalServerError(toJson("Curation object not found.")))
    }
  }

}