package api

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
import com.wordnik.swagger.annotations.{Api, ApiOperation}
import play.api.Logger
import play.api.Play.current

/**
 * Manipulates curation objects.
 */
@Api(value="/curations", listingPath= "/api-docs.json/curations", description = "A curation object is a dataset ready for publication")
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
  @ApiOperation(value = " Get Curation object ORE map",
    httpMethod = "GET")
  def getCurationObjectOre(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      val format = new java.text.SimpleDateFormat("dd-MM-yyyy")
      curations.get(curationId) match {
        case Some(c) => {

          val https = controllers.Utils.https(request)
          val key = play.api.Play.configuration.getString("commKey").getOrElse("")
          val filesJson = curations.getCurationFiles(c.files).map { file =>

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
              "Uploaded By" -> Json.toJson(userService.findByIdentity(file.author).map ( usr => Json.toJson(file.author.fullName + ": " +  api.routes.Users.findById(usr.id).absoluteURL(https)))),
              "Size" -> Json.toJson(size),
              "Mimetype" -> Json.toJson(file.contentType),
              "Publication Date" -> Json.toJson(""),
              "External Identifier" -> Json.toJson(""),
              "SHA512 Hash" -> Json.toJson(files.get(file.fileId).map{ f => f.sha512}),
              "Keyword" -> Json.toJson(file.tags.map(_.name)),
              "@type" -> Json.toJson(Seq("AggregatedResource", "http://cet.ncsa.uiuc.edu/2015/File")),
              "Is Version Of" -> Json.toJson(controllers.routes.Files.file(file.fileId).absoluteURL(https) + "?key=" + key),
              "similarTo" -> Json.toJson(api.routes.Files.download(file.fileId).absoluteURL(https)  + "?key=" + key)

            )
            if(file.tags.size > 0 ) {
              tempMap = tempMap ++ Map("Keyword" -> Json.toJson(file.tags.map(_.name)))
            }

            tempMap ++ fileMetadata

          }
          val hasPart = c.files.map(file => "urn:uuid:"+file)
          var commentsByDataset = comments.findCommentsByDatasetId(c.datasets(0).id)
          curations.getCurationFiles(c.files).map {
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
              "comment_author" -> Json.toJson(userService.findByIdentity(comm.author).map ( usr => Json.toJson(usr.fullName + ": " +  api.routes.Users.findById(usr.id).absoluteURL(https))))
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
          for(md <- metadatas.getDefinitions()) {
            metadataDefsMap((md.json\ "label").asOpt[String].getOrElse("").toString()) = Json.toJson((md.json \ "uri").asOpt[String].getOrElse(""))
          }
          val publicationDate = c.publishedDate match {
            case None => ""
            case Some(p) => format.format(c.created)
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
                    "Publishing Project" -> Json.toJson("http://sead-data.net/terms/publishingProject")
                  )
                )

              )),
              "Rights" -> Json.toJson(c.datasets(0).licenseData.m_licenseText),
              "describes" ->
                 Json.toJson( metadataJson.toMap ++ Map(
                  "Identifier" -> Json.toJson("urn:uuid:" + c.id),
                  "Creation Date" -> Json.toJson(format.format(c.created)),
                  "Label" -> Json.toJson(c.name),
                  "Title" -> Json.toJson(c.name),
                  "Dataset Description" -> Json.toJson(c.description),
                  "Uploaded By" -> Json.toJson(userService.findByIdentity(c.author).map ( usr => Json.toJson(usr.fullName + ": " + api.routes.Users.findById(usr.id).absoluteURL(https)))),
                  "Publication Date" -> Json.toJson(publicationDate),
                  "Published In" -> Json.toJson(""),
                  "External Identifier" -> Json.toJson(""),
                  "Proposed for publication" -> Json.toJson("true"),

                  "@id" -> Json.toJson(api.routes.CurationObjects.getCurationObjectOre(c.id).absoluteURL(https) + "#aggregation"),
                  "@type" -> Json.toJson(Seq("Aggregation", "http://cet.ncsa.uiuc.edu/2015/Dataset")),
                  "Is Version Of" -> Json.toJson(controllers.routes.Datasets.dataset(c.datasets(0).id).absoluteURL(https)),
                  "similarTo" -> Json.toJson(controllers.routes.Datasets.dataset(c.datasets(0).id).absoluteURL(https)),
                  "aggregates" -> Json.toJson(filesJson),
                  "Has Part" -> Json.toJson(hasPart),
                  "Publishing Project"-> Json.toJson(controllers.routes.Spaces.getSpace(c.space).absoluteURL(https))
                )),
              "Creation Date" -> Json.toJson(format.format(c.created)),
              "Uploaded By" -> Json.toJson(userService.findByIdentity(c.author).map ( usr => Json.toJson(usr.fullName + ": " +  api.routes.Users.findById(usr.id).absoluteURL(https)))),
              "@type" -> Json.toJson("ResourceMap"),
              "@id" -> Json.toJson(api.routes.CurationObjects.getCurationObjectOre(curationId).absoluteURL(https))
            )


          if(c.datasets(0).tags.size > 0) {
            parsedValue = parsedValue ++ Map("Keyword" -> Json.toJson(
              Json.toJson(c.datasets(0).tags.map(_.name))
            ))
          }
          if(commentsJson.size > 0) {
            parsedValue = parsedValue ++ Map( "Comment" -> Json.toJson(JsArray(commentsJson)))
          }

          Ok(Json.toJson(parsedValue))
        }
        case None => InternalServerError("Curation Object not Found");
      }

  }

  @ApiOperation(value = "Update the user repository preferences and call the matchmaker", notes = "",
    responseClass = "None", httpMethod = "POST")
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
          case None => InternalServerError("Curation Object Not found")
        }
      }
      case None => InternalServerError("User not found")
    }

  }

  @ApiOperation(value = "Retract the curation object from the repository", notes = "",
    responseClass = "None", httpMethod = "DELETE")
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
            curations.updateStatus(curationId, "In Curation")
            Ok(toJson(Map("status"->"success", "message"-> "Curation object retracted successfully")))
          } else if (responseStatus == 404 && EntityUtils.toString(response.getEntity, "UTF-8") == s"RO with ID urn:uuid:$curationId does not exist") {
            BadRequest(toJson(Map("status" -> "error", "message" ->"Curation object not found in external server")))
          } else {
            InternalServerError("Unknown error")
          }
        }
        case None => BadRequest("Curation Object Not found")
      }
  }

  @ApiOperation(value = "Get files in curation", notes = "",
    responseClass = "None", httpMethod = "GET")
  def getCurationFiles(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user
      curations.get(curationId) match {
        case Some(c) => {
          Ok(toJson(Map("cf" -> curations.getCurationFiles(c.files))))
        }
        case None => InternalServerError("Curation Object Not found")
      }
  }

  @ApiOperation(value = "Delete a file in curation object", notes = "",
    responseClass = "None", httpMethod = "POST")
  def deleteCurationFiles(curationId: UUID, curationFileId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user
      curations.get(curationId) match {
        case Some(c) => {
          curations.deleteCurationFiles(curationId, curationFileId)
          Ok(toJson("Success"))
        }
        case None => InternalServerError("Curation Object Not found")
      }
  }
}
