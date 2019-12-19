package api

import java.net.URI

import controllers.Utils
import javax.inject.{Inject, Singleton}
import models._
import org.apache.http.client.methods.HttpDelete
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import services._
import play.api.libs.json._
import play.api.libs.json.Json
import play.api.libs.json.JsResult
import play.api.libs.json.Json.toJson
import play.api.Logger
import play.api.Play.current

/**
 * Manipulates publication requests curation objects.
 */
@Singleton
class CurationObjects @Inject()(datasets: DatasetService,
      curations: CurationService,
      files: FileService,
      comments: CommentService,
      sections: SectionService,
      spaces: SpaceService,
      userService: UserService,
      curationObjectController: controllers.CurationObjects,
      metadatas: MetadataService
      ) extends ApiController {
  def getCurationObjectOre(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      val format = new java.text.SimpleDateFormat("dd-MM-yyyy")
      curations.get(curationId) match {
        case Some(c) => {

          val https = controllers.Utils.https(request)
          val key = play.api.Play.configuration.getString("commKey").getOrElse("")
          val filesJson = curations.getCurationFiles(curations.getAllCurationFileIds(c.id)).map { file =>

            var fileMetadata = scala.collection.mutable.Map.empty[String, JsValue]
            metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.curationFile, file.id)).filter(_.creator.typeOfAgent == "cat:user").map {
              item => fileMetadata = fileMetadata ++ curationObjectController.buildMetadataMap(item.content)
            }
            val size = files.get(file.fileId) match {
              case Some(f) => f.length
              case None => 0
            }

            var tempMap =  Map(
              "Identifier" -> Json.toJson("urn:uuid:"+file.id),
              "@id" -> Json.toJson("urn:uuid:"+file.id),
              "Creation Date" -> Json.toJson(format.format(file.uploadDate)),
              "Label" -> Json.toJson(file.filename),
              "Title" -> Json.toJson(file.filename),
              "Uploaded By" -> Json.toJson(userService.findById(file.author.id).map ( usr => Json.toJson(file.author.fullName + ": " +  api.routes.Users.findById(usr.id).absoluteURL(https)))),
              "Size" -> Json.toJson(size),
              "Mimetype" -> Json.toJson(file.contentType),
              "Publication Date" -> Json.toJson(""),
              "External Identifier" -> Json.toJson(""),
              "SHA512 Hash" -> Json.toJson(file.sha512),
              "@type" -> Json.toJson(Seq("AggregatedResource", "http://cet.ncsa.uiuc.edu/2015/File")),
              "Is Version Of" -> Json.toJson(controllers.routes.Files.file(file.fileId).absoluteURL(https) + "?key=" + key),
              "similarTo" -> Json.toJson(api.routes.Files.download(file.fileId).absoluteURL(https)  + "?key=" + key)

            )
            if(file.tags.size > 0 ) {
              tempMap = tempMap ++ Map("Keyword" -> Json.toJson(file.tags.map(_.name)))
            }

            tempMap ++ fileMetadata

          }
          val foldersJson = curations.getCurationFolders(curations.getAllCurationFolderIds(c.id)).map { folder =>

              val hasPart = folder.files.map(file=>"urn:uuid:"+file) ++ folder.folders.map(fd => "urn:uuid:"+fd)
              val tempMap = Map(
                "Creation Date" -> Json.toJson(format.format(folder.created)),
                "Rights" -> Json.toJson(c.datasets(0).licenseData.m_licenseText),
                "Identifier" -> Json.toJson("urn:uuid:"+folder.id),
                "License" -> Json.toJson(c.datasets(0).licenseData.m_licenseText),
                "Label" -> Json.toJson(folder.name),
                "Title" -> Json.toJson(folder.displayName),
                "Uploaded By" -> Json.toJson(folder.author.fullName + ": " +  api.routes.Users.findById(folder.author.id).absoluteURL(https)),
                "@id" -> Json.toJson("urn:uuid:"+folder.id),
                "@type" -> Json.toJson(Seq("AggregatedResource", "http://cet.ncsa.uiuc.edu/2016/Folder")),
                "Is Version Of" -> Json.toJson(controllers.routes.Datasets.dataset(c.datasets(0).id).absoluteURL(https) +"#folderId=" +folder.folderId),
                "Has Part" -> Json.toJson(hasPart)
              )
              tempMap

          }
          val hasPart = c.files.map(file => "urn:uuid:"+file) ++ c.folders.map(folder => "urn:uuid:"+folder)
          var commentsByDataset = comments.findCommentsByDatasetId(c.datasets(0).id)
          curations.getCurationFiles(curations.getAllCurationFileIds(c.id)).map {
            file =>
              commentsByDataset ++= comments.findCommentsByFileId(file.fileId)
              sections.findByFileId(UUID(file.fileId.toString)).map { section =>
                commentsByDataset ++= comments.findCommentsBySectionId(section.id)
              }
          }
          commentsByDataset = commentsByDataset.sortBy(_.posted)
          val commentsJson = commentsByDataset.map { comm =>
            Json.toJson(Map(
              "comment_body" -> Json.toJson(comm.text),
              "comment_date" -> Json.toJson(format.format(comm.posted)),
              "Identifier" -> Json.toJson("urn:uuid:"+comm.id),
              "comment_author" -> Json.toJson(userService.findById(comm.author.id).map ( usr => Json.toJson(usr.fullName + ": " +  api.routes.Users.findById(usr.id).absoluteURL(https))))
            ))
          }
          var metadataList = scala.collection.mutable.ListBuffer.empty[MetadataPair]
          var metadataKeys = Set.empty[String]
          metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.curationObject, c.id)).filter(_.creator.typeOfAgent == "cat:user").map {
            item =>
              for((key, value) <- curationObjectController.buildMetadataMap(item.content)) {
                metadataList += MetadataPair(key, value)
                metadataKeys += key
              }
          }
          var metadataJson = scala.collection.mutable.Map.empty[String, JsValue]
          for(key <- metadataKeys) {
            metadataJson = metadataJson ++ Map(key -> Json.toJson(metadataList.filter(_.label == key).map{item => item.content}toList))
          }
          val metadataDefsMap = scala.collection.mutable.Map.empty[String, JsValue]
          for(md <- metadatas.getDefinitions(Some(c.space))) {
            metadataDefsMap((md.json\ "label").asOpt[String].getOrElse("").toString()) = Json.toJson((md.json \ "uri").asOpt[String].getOrElse(""))
          }
          if(metadataJson.contains("Creator")) {
            val value = c.creators ++ metadataList.filter(_.label == "Creator").map{item => item.content.as[String]}.toList
            metadataJson = metadataJson ++ Map("Creator" -> Json.toJson(value))
          } else {
            metadataJson = metadataJson ++ Map("Creator" -> Json.toJson(c.creators))
          }
          if(!metadataDefsMap.contains("Creator")){
            metadataDefsMap("Creator") = Json.toJson(Map("@id" -> "http://purl.org/dc/terms/creator", "@container" -> "@list"))
          }
          val publicationDate = c.publishedDate match {
            case None => ""
            case Some(p) => format.format(c.created)
          }
          if(metadataJson.contains("Abstract")) {
            val value  = List(c.description) ++ metadataList.filter(_.label == "Abstract").map{item => item.content.as[String]}
            metadataJson = metadataJson ++ Map("Abstract" -> Json.toJson(value))
          } else {
            metadataJson = metadataJson ++ Map("Abstract" -> Json.toJson(c.description))
          }
          if(!metadataDefsMap.contains("Abstract")){
            metadataDefsMap("Abstract") = Json.toJson("http://purl.org/dc/terms/abstract")
          }
          var aggregation = metadataJson
          if(commentsJson.size > 0) {
            aggregation = metadataJson ++ Map( "Comment" -> Json.toJson(JsArray(commentsJson)))
          }
          if(c.datasets(0).tags.size > 0) {
            aggregation = aggregation ++ Map("Keyword" -> Json.toJson(
              Json.toJson(c.datasets(0).tags.map(_.name))
            ))
          }
          var parsedValue =
            Map(
              "@context" -> Json.toJson(Seq(
                Json.toJson("https://w3id.org/ore/context"),
                Json.toJson(
                  metadataDefsMap.toMap ++ Map(
                    "Identifier" -> Json.toJson("http://purl.org/dc/elements/1.1/identifier"),
                    "Rights" -> Json.toJson("http://purl.org/dc/terms/rights"),
                    "Date" -> Json.toJson("http://purl.org/dc/elements/1.1/date"),
                    "Creation Date" -> Json.toJson("http://purl.org/dc/terms/created"),
                    "Label" -> Json.toJson("http://www.w3.org/2000/01/rdf-schema#label"),
                    "Location" -> Json.toJson( "http://sead-data.net/terms/generatedAt"),
                    "Description" -> Json.toJson("http://purl.org/dc/elements/1.1/description"),
                    "Keyword"-> Json.toJson("http://www.holygoat.co.uk/owl/redwood/0.1/tags/taggedWithTag"),
                    "Title" -> Json.toJson("http://purl.org/dc/elements/1.1/title"),
                    "Uploaded By" -> Json.toJson("http://purl.org/dc/elements/1.1/creator"),
                    "Contact" -> Json.toJson("http://sead-data.net/terms/contact"),
                    "name" -> Json.toJson("http://sead-data.net/terms/name"),
                    "email" -> Json.toJson("http://schema.org/Person/email"),
                    "Publication Date" -> Json.toJson("http://purl.org/dc/terms/issued"),
                    "Spatial Reference" ->
                      Json.toJson(
                        Map(

                          "@id" -> Json.toJson("tag:tupeloproject.org,2006:/2.0/gis/hasGeoPoint"),
                          "Longitude" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#long"),
                          "Latitude" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#lat"),
                          "Altitude" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#alt")

                    )),
                    "Comment" -> Json.toJson(Map(
                      "comment_body" -> Json.toJson("http://purl.org/dc/elements/1.1/description"),
                      "comment_date" -> Json.toJson("http://purl.org/dc/elements/1.1/date"),
                      "@id" -> Json.toJson("http://cet.ncsa.uiuc.edu/2007/annotation/hasAnnotation"),
                      "comment_author" -> Json.toJson("http://purl.org/dc/elements/1.1/creator")
                    )),
                    "Has Description" -> Json.toJson("http://purl.org/dc/terms/description"),
                    "Bibliographic citation" -> Json.toJson("http://purl.org/dc/terms/bibliographicCitation"),
                    "Published In" -> Json.toJson("http://purl.org/dc/terms/isPartOf"),
                    "Publisher" -> Json.toJson("http://purl.org/dc/terms/publisher"),
                    "External Identifier" -> Json.toJson("http://purl.org/dc/terms/identifier"),
                    "references" -> Json.toJson("http://purl.org/dc/terms/references"),
                    "Is Version Of" -> Json.toJson("http://purl.org/dc/terms/isVersionOf"),
                    "Has Part" -> Json.toJson("http://purl.org/dc/terms/hasPart"),
                    "Size" -> Json.toJson("tag:tupeloproject.org,2006:/2.0/files/length"),
                    "Mimetype" -> Json.toJson("http://purl.org/dc/elements/1.1/format"),
                    "SHA512 Hash" -> Json.toJson("http://sead-data.net/terms/hasSHA512Digest"),
                    "Dataset Description" -> Json.toJson("http://sead-data.net/terms/datasetdescription"),
                    "Publishing Project" -> Json.toJson("http://sead-data.net/terms/publishingProject"),
                    "License" -> Json.toJson("http://purl.org/dc/terms/license")
                  )
                )

              )),
              "Rights" -> Json.toJson("CC0"),
              "describes" ->
                 Json.toJson( aggregation.toMap ++ Map(
                  "Identifier" -> Json.toJson("urn:uuid:" + c.id),
                  "Creation Date" -> Json.toJson(format.format(c.created)),
                  "Label" -> Json.toJson(c.name),
                  "Title" -> Json.toJson(c.name),
                  "Dataset Description" -> Json.toJson(c.description),
                  "Uploaded By" -> Json.toJson(userService.findById(c.author.id).map ( usr => Json.toJson(usr.fullName + ": " + api.routes.Users.findById(usr.id).absoluteURL(https)))),
                  "Publication Date" -> Json.toJson(publicationDate),
                  "Published In" -> Json.toJson(""),
                  "External Identifier" -> Json.toJson(""),
                  "Proposed for publication" -> Json.toJson("true"),
                  "@id" -> Json.toJson(api.routes.CurationObjects.getCurationObjectOre(c.id).absoluteURL(https) + "#aggregation"),
                  "@type" -> Json.toJson(Seq("Aggregation", "http://cet.ncsa.uiuc.edu/2015/Dataset")),
                  "Is Version Of" -> Json.toJson(controllers.routes.Datasets.dataset(c.datasets(0).id).absoluteURL(https)),
                  "similarTo" -> Json.toJson(controllers.routes.Datasets.dataset(c.datasets(0).id).absoluteURL(https)),
                  "aggregates" -> Json.toJson(filesJson ++ foldersJson.toList),
                  "Has Part" -> Json.toJson(hasPart),
                  "Publishing Project"-> Json.toJson(controllers.routes.Spaces.getSpace(c.space).absoluteURL(https))
                )),
              "Creation Date" -> Json.toJson(format.format(c.created)),
              "Uploaded By" -> Json.toJson(userService.findById(c.author.id).map ( usr => Json.toJson(usr.fullName + ": " +  api.routes.Users.findById(usr.id).absoluteURL(https)))),
              "@type" -> Json.toJson("ResourceMap"),
              "@id" -> Json.toJson(api.routes.CurationObjects.getCurationObjectOre(curationId).absoluteURL(https))
            )

          Ok(Json.toJson(parsedValue))
        }
        case None => InternalServerError("Publication Request not Found");
      }

  }

  def findMatchmakingRepositories(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId)))(parse.json) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(usr) => {
        curations.get(curationId) match {
          case Some(c) => {
            val values: JsResult[Map[String, String]] = (request.body \ "data").validate[Map[String, String]]
            values match {
              case aMap: JsSuccess[Map[String, String]] => {
                val userPreferences: Map[String, String] = aMap.get
                userService.updateRepositoryPreferences(usr.id, userPreferences)
                val mmResp = curationObjectController.callMatchmaker(c, user)
                if ( mmResp.size > 0) {
                  Ok(toJson(mmResp))
                } else {
                  InternalServerError("No response from matchmaker")
                }
              }
              case e: JsError => {
                Logger.error("Errors: " + JsError.toFlatJson(e).toString())
                BadRequest(toJson("The user repository preferences are missing from the find matchmaking repositories call."))
              }
            }
          }
          case None => InternalServerError("Publication Request Not found")
        }
      }
      case None => InternalServerError("User not found")
    }

  }

  def retractCurationObject(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user
      curations.get(curationId) match {
        case Some(c) => {
          val endpoint =play.Play.application().configuration().getString("stagingarea.uri").replaceAll("/$","")
          val httpDelete = new HttpDelete(endpoint + "/urn:uuid:" + curationId.toString())
          val client = new DefaultHttpClient
          val response = client.execute(httpDelete)
          val responseStatus = response.getStatusLine().getStatusCode()

          if(responseStatus >= 200 && responseStatus < 300 || responseStatus == 304 ) {
            curations.updateStatus(curationId, "In Preparation")
            Ok(toJson(Map("status"->"success", "message"-> "Publication Request successfully retracted")))
          } else if (responseStatus == 404 && EntityUtils.toString(response.getEntity, "UTF-8") == s"Publication Request with ID urn:uuid:$curationId does not exist") {
            BadRequest(toJson(Map("status" -> "error", "message" ->"Publication Request not found in external server")))
          } else {
            InternalServerError("Unknown error")
          }
        }
        case None => BadRequest("Publication Request Not found")
      }
  }

  def getCurationFiles(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user
      curations.get(curationId) match {
        case Some(c) => {
          Ok(toJson(Map("cf" -> curations.getCurationFiles(curations.getAllCurationFileIds(c.id)))))
        }
        case None => InternalServerError("Publication Request Not found")
      }
  }

  def deleteCurationFile(curationId:UUID, parentId: UUID, curationFileId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      curations.get(curationId) match {
        case Some(c) => c.status match {
          case "In Preparation" => {
            if(curationId == parentId){
              curations.removeCurationFile("dataset", parentId, curationFileId)
            } else {
              curations.removeCurationFile("folder", parentId, curationFileId)
            }
            curations.deleteCurationFile(curationFileId, Utils.baseUrl(request), request.apiKey, request.user)
            Ok(toJson("Success"))
          }
          case _ => InternalServerError("Cannot modify Publication Request")
        }
        case None => InternalServerError("Publication Request Not found")
      }

  }

  def deleteCurationFolder(curationId:UUID, parentId: UUID, curationFolderId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      curations.get(curationId) match {
        case Some(c) => c.status match {
          case "In Preparation" => {
            if(curationId == parentId){
              curations.removeCurationFolder("dataset", parentId, curationFolderId)
            } else {
              curations.removeCurationFolder("folder", parentId, curationFolderId)
            }
            curations.deleteCurationFolder(curationFolderId, Utils.baseUrl(request), request.apiKey, request.user)
            Ok(toJson("Success"))
          }
          case _ => InternalServerError("Cannot modify Publication Request")
        }
        case None => InternalServerError("Publication Request Not found")
      }
  }

  /**
    * Endpoint for receiving publication success+PID message from publication services.
    */
  def savePublishedObject(id: UUID) = AuthenticatedAction (parse.json) {
    implicit request =>
      Logger.debug("get infomation from SEAD services")

      curations.get(id) match {

        case Some(c) => {
          c.status match {

            case "In Preparation" => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Publication Request hasn't been submitted yet.")))
            //sead2 receives status once from repository,
            case "Published" | "ERROR" | "Reject" => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Final status already received for this Publication Request.")))
            case "Submitted" => {
              //parse status from request's body
              val statusList = (request.body \ "status").asOpt[String]

              statusList.size match {
                case 0 => {
                  if ((request.body \ "uri").asOpt[String].isEmpty) {
                    BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Receive empty request.")))
                  } else {
                    (request.body \ "uri").asOpt[String].map {
                      externalIdentifier => {
                        //set published when uri is provided
                        curations.setPublished(id)
                        if (externalIdentifier.startsWith("doi:") || externalIdentifier.startsWith("10.")) {
                          val DOI_PREFIX = "http://doi.org/"
                          curations.updateExternalIdentifier(id, new URI(DOI_PREFIX + externalIdentifier.replaceAll("^doi:", "")))
                        } else {
                          curations.updateExternalIdentifier(id, new URI(externalIdentifier))
                        }
                      }
                    }
                    Ok(toJson(Map("status" -> "OK")))
                  }
                }
                case 1 => {
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
                      if (externalIdentifier.startsWith("doi:") || externalIdentifier.startsWith("10.")) {
                        val DOI_PREFIX = "http://doi.org/"
                        curations.updateExternalIdentifier(id, new URI(DOI_PREFIX + externalIdentifier.replaceAll("^doi:", "")))
                      } else {
                        curations.updateExternalIdentifier(id, new URI(externalIdentifier))
                      }
                    }
                  }
                  Ok(toJson(Map("status" -> "OK")))
                }
                //multiple status
                case _ => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Publication Request has unrecognized status.")))
              }

            }
          }
        }
        case None => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Publication Request not found.")))
      }
  }

  def getMetadataDefinitions(id: UUID) = PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.curationObject, id))) { implicit request =>
    implicit val user = request.user
    curations.get(id) match {
      case Some(curationObject) => {
        val metadataDefinitions  = metadatas.getDefinitions(Some(curationObject.space))
        Ok(toJson(metadataDefinitions.toList))
      }
      case None => BadRequest(toJson("The publication request does not exist"))
    }
  }

  def getMetadataDefinitionsByFile(id: UUID) = PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.curationFile, id))) { implicit request =>
    implicit val user = request.user
    curations.getCurationByCurationFile(id) match {
      case Some(curationObject) => {
        val metadataDefinitions = metadatas.getDefinitions(Some(curationObject.space))
        Ok(toJson(metadataDefinitions.toList))
      }
      case None => BadRequest(toJson("There is no publication request associated with this File"))
    }
  }

}
