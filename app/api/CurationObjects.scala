package api

import javax.inject.{Inject, Singleton}


import com.mongodb.BasicDBList
import controllers.Utils
import models.{MatchMakerResponse, mmRule, ResourceRef, UUID}
import play.api.libs.ws.WS
import org.apache.http.client.methods.HttpDelete
import org.apache.http.impl.client.DefaultHttpClient
import services._
import play.api.libs.json._
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.libs.json.JsResult
import com.wordnik.swagger.annotations.{ApiResponse, ApiResponses, Api, ApiOperation}
import play.api.Logger
import controllers.CurationObjects

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import collection.JavaConverters._
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
      curationObjectController: controllers.CurationObjects
      ) extends ApiController {
  @ApiOperation(value = " Get Curation object ORE map",
    httpMethod = "GET")
  def getCurationObjectOre(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      val format = new java.text.SimpleDateFormat("dd-MM-yyyy")
      curations.get(curationId) match {
        case Some(c) => {
          val hostIp = Utils.baseUrl(request)
          val hostUrl = hostIp + "/api/curations/" + curationId + "/ore"
          val filesJson = c.files.map { file =>
            // TODO: Add file.metadata to ORE Map when we change to JSON-LD
//            var fl_md: Map[String, Any] = Map.empty[String,Any]
//            for ( i <- 0 to file.metadata.length -1) {
//              fl_md = fl_md ++ file.metadata(i).asInstanceOf[com.mongodb.BasicDBObject].toMap().asScala.asInstanceOf[scala.collection.mutable.Map[String, Any]].toMap
//            }

//            val file_md_parsed = fl_md.map(
//              it => it._1 -> Json.toJson( it._2.asInstanceOf[com.mongodb.BasicDBObject].toMap().asScala.asInstanceOf[scala.collection.mutable.Map[String, Any]].toMap.map(
//              item => item.asInstanceOf[Tuple2[String, BasicDBList]]._1 -> Json.toJson(item.asInstanceOf[Tuple2[String, BasicDBList]]._2.get(0).toString())
//              )))
            val key = play.api.Play.configuration.getString("commKey").getOrElse("")
            val metadata = file.userMetadata ++ file.xmlMetadata
            val fileMetadata = metadata.map {
              item => item.asInstanceOf[Tuple2[String, BasicDBList]]._1 -> Json.toJson(item.asInstanceOf[Tuple2[String, BasicDBList]]._2.get(0).toString())
            }
            val tempMap =  Map(
              "Identifier" -> Json.toJson("urn:uuid:"+file.id),
              "@id" -> Json.toJson(hostIp+"/files/" +file.id +"?key=" + key),
              "Creation Date" -> Json.toJson(format.format(file.uploadDate)),
              "Label" -> Json.toJson(file.filename),
              "Title" -> Json.toJson(file.filename),
              "Uploaded By" -> Json.toJson(userService.findByIdentity(file.author).map ( usr => Json.toJson(file.author.fullName + ": " + hostIp + "/profile/viewProfile/" + usr.id))),

              "Publication Date" -> Json.toJson(""),
              "External Identifier" -> Json.toJson(""),
              "Keyword" -> Json.toJson(file.tags.map(_.name)),
              "@type" -> Json.toJson(Seq("AggregatedResource", "http://cet.ncsa.uiuc.edu/2015/File")),
              "Is Version Of" -> Json.toJson(hostIp + "/files/" + file.id),
              "similarTo" -> Json.toJson(hostIp + "/api/files/" + file.id + "/blob")
            )
            fileMetadata.toMap ++ tempMap
          }
          val fileIds = c.files.map{file => file.id}
          var commentsByDataset = comments.findCommentsByDatasetId(c.datasets(0).id)
          c.files.map {
            file =>

              commentsByDataset ++= comments.findCommentsByFileId(file.id)
              sections.findByFileId(UUID(file.id.toString)).map { section =>
                commentsByDataset ++= comments.findCommentsBySectionId(section.id)
              }
          }
          commentsByDataset = commentsByDataset.sortBy(_.posted)
          val commentsJson = commentsByDataset.map { comm =>
            Json.toJson(Map(
              "comment_body" -> Json.toJson(comm.text),
              "comment_date" -> Json.toJson(format.format(comm.posted)),
              "Identifier" -> Json.toJson("urn:uuid:"+comm.id),
              "comment_author" -> Json.toJson(userService.findByIdentity(comm.author).map ( usr => Json.toJson(usr.fullName + ": " + hostIp + "/profile/viewProfile/" + usr.id)))
            ))
          }
          val metadata = c.datasets(0).metadata ++ c.datasets(0).datasetXmlMetadata.map(metadata => metadata.xmlMetadata) ++ c.datasets(0).userMetadata
          val metadataJson = metadata.map {
            item => item.asInstanceOf[Tuple2[String, BasicDBList]]._1 -> Json.toJson(item.asInstanceOf[Tuple2[String, BasicDBList]]._2.get(0).toString())
          }
          val parsedValue = Json.toJson(
            Map(
              "@context" -> Json.toJson(Seq(
                Json.toJson("https://w3id.org/ore/context"),
                Json.toJson(
                  Map(
                    "Identifier" -> Json.toJson("http://www.ietf.org/rfc/rfc4122"),
                    "Rights" -> Json.toJson("http://purl.org/dc/terms/rights"),
                    "Date" -> Json.toJson("http://purl.org/dc/elements/1.1/date"),
                    "Creation Date" -> Json.toJson("http://purl.org/dc/terms/created"),
                    "Label" -> Json.toJson("http://www.w3.org/2000/01/rdf-schema#label"),
                    "Location" -> Json.toJson( "http://sead-data.net/terms/generatedAt"),
                    "Description" -> Json.toJson("http://purl.org/dc/elements/1.1/description"),
                    "Keyword"-> Json.toJson("http://www.holygoat.co.uk/owl/redwood/0.1/tags/taggedWithTag"),
                    "Title" -> Json.toJson("http://purl.org/dc/elements/1.1/title"),
                    "Uploaded By" -> Json.toJson("http://purl.org/dc/elements/1.1/creator"),
                    "Abstract" -> Json.toJson("http://purl.org/dc/terms/abstract"),
                    "Contact" -> Json.toJson("http://sead-data.net/terms/contact"),
                    "name" -> Json.toJson("http://purl.org/dc/terms/name"),
                    "email" -> Json.toJson("http://purl.org/dc/terms/email"),
                    "Creator" -> Json.toJson("http://purl.org/dc/terms/creator"),
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
                    "Audience" -> Json.toJson("http://purl.org/dc/terms/audience"),
                    "Bibliographic citation" -> Json.toJson("http://purl.org/dc/terms/bibliographicCitation"),
                    "Published In" -> Json.toJson("http://purl.org/dc/terms/isPartOf"),
                    "Publisher" -> Json.toJson("http://purl.org/dc/terms/publisher"),
                    "Alternative title" -> Json.toJson("http://purl.org/dc/terms/alternative"),
                    "External Identifier" -> Json.toJson("http://purl.org/dc/terms/identifier"),
                    "describes" -> Json.toJson("http://cet.ncsa.uiuc.edu/2007/mmdb/describes"),
                    "references" -> Json.toJson("http://purl.org/dc/terms/references"),
                    "keyword" -> Json.toJson("http://www.holygoat.co.uk/owl/redwood/0.1/tags/taggedWithTag"),
                    "Is Version Of" -> Json.toJson("http://purl.org/dc/terms/isVersionOf"),
                    "Has Part" -> Json.toJson("http://purl.org/dc/terms/hasPart"),
                    "similarTo" -> Json.toJson("http://sead-data.net/terms/similarTo"),
                    "aggregates" -> Json.toJson("http://sead-data.net/terms/aggregates")
                  )
                )

              )),
              "Rights" -> Json.toJson(c.datasets(0).licenseData.m_licenseText),
              "describes" ->
                 Json.toJson( metadataJson.toMap ++ Map(
                  "Identifier" -> Json.toJson("urn:uuid" + c.id),
                  "Creation Date" -> Json.toJson(format.format(c.created)),
                  "Label" -> Json.toJson(c.name),
                  "Title" -> Json.toJson(c.name),
                  "Uploaded By" -> Json.toJson(userService.findByIdentity(c.author).map ( usr => Json.toJson(usr.fullName + ": " + hostIp + "/profile/viewProfile/" + usr.id))),
                  "Creator" -> Json.toJson(userService.findByIdentity(c.author).map(usr => JsArray(Seq(Json.toJson(usr.fullName + ": " + hostIp + "/profile/viewProfile/" + usr.id), Json.toJson(usr.profile.map(prof => prof.orcidID.map(oid=> oid))))))),
                  "Comment" -> Json.toJson(JsArray(commentsJson)),
                  "Publication Date" -> Json.toJson(format.format(c.created)),
                  "Published In" -> Json.toJson(""),
                  "External Identifier" -> Json.toJson(""),
                  "Proposed for publication" -> Json.toJson("true"),
                  "keyword" -> Json.toJson(
                    Json.toJson(c.datasets(0).tags.map(_.name))
                  ),
                  "@id" -> Json.toJson(hostIp + "/api/curations/" + c.id + "/ore#aggregation"),
                  "@type" -> Json.toJson(Seq("Aggregation", "http://cet.ncsa.uiuc.edu/2015/Dataset")),
                  "Is Version of" -> Json.toJson(hostIp + "/datasets/" + c.datasets(0).id ),
                  "similarTo" -> Json.toJson(hostIp + "/datasets/" + c.datasets(0).id ),
                  "aggregates" -> Json.toJson(filesJson),
                  "Has Part" -> Json.toJson(fileIds)

                )),
              "Creation Date" -> Json.toJson(format.format(c.created)),
              "Creator" -> Json.toJson(userService.findByIdentity(c.author).map ( usr => Json.toJson(usr.fullName + ": " + hostIp + "/profile/viewProfile/" + usr.id))),
              "@type" -> Json.toJson("ResourceMap"),
              "@id" -> Json.toJson(hostUrl)
            )

          )

          Ok(Json.toJson(parsedValue))
        }
        case None => InternalServerError("Curation Object not Found");
      }

  }

  @ApiOperation(value = "Update the user repository preferences and call the matchmaker", notes = "",
    responseClass = "None", httpMethod = "POST")
  def findMatchmakingRepositories(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId)))(parse.json) { implicit request =>
    implicit val user = request.user

    curations.get(curationId) match {
      case Some(c) => {
        val values: JsResult[Map[String, String]] = (request.body \ "data").validate[Map[String, String]]
        values match {
          case aMap: JsSuccess[Map[String, String]] => {
            val userPreferences: Map[String, String] = aMap.get
            userService.updateRepositoryPreferences(user.get.id, userPreferences)

            val mmResp = curationObjectController.callMatchmaker(c, Utils.baseUrl(request))
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
  
  @ApiOperation(value = "Retract the curation object from the repository", notes = "",
    responseClass = "None", httpMethod = "DELETE")
  def retractCurationObject(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user
      curations.get(curationId) match {
        case Some(c) => {
          var success = false
          var endpoint =play.Play.application().configuration().getString("stagingarea.uri").replaceAll("/$","")
          val httpDelete = new HttpDelete(endpoint + "/" + curationId.toString())
          var client = new DefaultHttpClient
          val response = client.execute(httpDelete)
          val responseStatus = response.getStatusLine().getStatusCode()
          if(responseStatus >= 200 && responseStatus < 300 || responseStatus == 304) {
            curations.updateStatus(curationId, "In Curation")
            success = true
          }
          if(success) {
            Ok(toJson("Success"))
          } else {
            InternalServerError("Could not retract curation Object")
          }
        }
        case None => InternalServerError("Curation Object Not found")
      }


  }

}
