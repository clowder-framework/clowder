package api

import javax.inject.{Inject, Singleton}


import controllers.Utils
import models.{MatchMakerResponse, mmRule, ResourceRef, UUID}
import play.api.libs.ws.WS
import services._
import play.api.libs.json._
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.libs.json.JsResult
import com.wordnik.swagger.annotations.{ApiResponse, ApiResponses, Api, ApiOperation}
import play.api.Logger

import scala.concurrent.Await
import scala.concurrent.duration.Duration

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
      userService: UserService
      ) extends ApiController {
  @ApiOperation(value = " Get Curation object ORE map",
    httpMethod = "GET")
  def getCurationObjectOre(curationId: UUID) = PrivateServerAction {
    implicit request =>
      val format = new java.text.SimpleDateFormat("dd-MM-yyyy")
      curations.get(curationId) match {
        case Some(c) => {
          val hostIp = Utils.baseUrl(request)
          val hostUrl = hostIp + "/api/curations/" + curationId + "/ore"
          val filesJson = c.datasets(0).files.map { file =>
            Json.toJson(Map(
              "Identifier" -> Json.toJson(hostIp+"/files/" +file.id),
              "@id" -> Json.toJson(hostIp+"/files/" +file.id),
              "Creation Date" -> Json.toJson(format.format(file.uploadDate)),
              "Label" -> Json.toJson(file.filename),
              "Title" -> Json.toJson(file.filename),
              "Uploaded By" -> Json.toJson(userService.findByIdentity(file.author).map ( usr => Json.toJson(file.author.fullName + ": " + hostIp + "/profile/viewProfile/" + usr.id))),
              "Abstract" -> Json.toJson(file.userMetadata.get("Abstract").getOrElse("").toString),
              "Creator" -> Json.toJson(userService.findByIdentity(file.author).map ( usr => usr.profile.map(prof => prof.orcidID.map(oid=> oid)))),
              "Publication Date" -> Json.toJson(""),
              "External Identifier" -> Json.toJson(""),
              "Keyword" -> Json.toJson(file.tags.map(_.name)),
              "@type" -> Json.toJson(Seq("AggregatedResource", "http://cet.ncsa.uiuc.edu/2007/File")),
              "Version Of" -> Json.toJson(hostIp + "/files/" + file.id),
              "Has Part" ->Json.toJson("")
            ))
          }

          val filesInDataset = c.datasets(0).files.map(f => files.get(f.id).get) //.sortBy(_.uploadDate)
          var commentsByDataset = comments.findCommentsByDatasetId(c.datasets(0).id)
          filesInDataset.map {
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
              "Identifier" -> Json.toJson(comm.id),
              "comment_author" -> Json.toJson(userService.findByIdentity(comm.author).map ( usr => Json.toJson(usr.fullName + ": " + hostIp + "/profile/viewProfile/" + usr.id)))
            ))
          }


          val parsedValue = Json.toJson(
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
                    "GeoPoint" -> Json.toJson(Seq(
                      Json.toJson(
                        Map(

                          "@id" -> Json.toJson("tag:tupeloproject.org,2006:/2.0/gis/hasGeoPoint"),
                          "long" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#long"),
                          "lat" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#lat")
                        )
                      )
                    )),
                    "Comment" -> Json.toJson(Seq(Json.toJson(Map(

                      "comment_body" -> Json.toJson("http://purl.org/dc/elements/1.1/description"),
                      "comment_date" -> Json.toJson("http://purl.org/dc/elements/1.1/date"),
                      "@id" -> Json.toJson("http://cet.ncsa.uiuc.edu/2007/annotation/hasAnnotation"),
                      "comment_author" -> Json.toJson("http://purl.org/dc/elements/1.1/creator")
                    )))),
                    "Where" -> Json.toJson("http:/sead-data.net/vocab/demo/where"),
                    "Has Description" -> Json.toJson("http://purl.org/dc/terms/description"),
                    "Experiment Site" -> Json.toJson("http://sead-data.net/terms/odm/location"),
                    "Experimental Method" -> Json.toJson("http://sead-data.net/terms/odm/method"),
                    "Primary Source" -> Json.toJson("http://www.w3.org/ns/prov#hadPrimarySource"),
                    "Topic" -> Json.toJson("http://purl.org/dc/terms/subject"),
                    "Audience" -> Json.toJson("http://purl.org/dc/terms/audience"),
                    "Bibliographic citation" -> Json.toJson("http://purl.org/dc/terms/bibliographicCitation"),
                    "http://purl.org/vocab/frbr/core#embodimentOf" -> Json.toJson("http://purl.org/vocab/frbr/core#embodimentOf"),
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
                    "Has Part" -> Json.toJson("http://purl.org/dc/terms/hasPart")
                  )
                )

              )),
              "Rights" -> Json.toJson(c.datasets(0).licenseData.m_licenseText),
              "describes" ->
                Json.toJson(Map(
                  "Identifier" -> Json.toJson(hostIp + "/api/curations/" + c.id),
                  "Creation Date" -> Json.toJson(format.format(c.created)),
                  "Label" -> Json.toJson(c.name),
                  "Title" -> Json.toJson(c.name),
                  "Uploaded By" -> Json.toJson(userService.findByIdentity(c.author).map ( usr => Json.toJson(usr.fullName + ": " + hostIp + "/profile/viewProfile/" + usr.id))),
                  "Abstract" -> Json.toJson(c.datasets(0).userMetadata.get("Abstract").getOrElse("").toString),
                  "Creator" -> Json.toJson(userService.findByIdentity(c.author).map(usr => JsArray(Seq(Json.toJson(usr.fullName + ": " + hostIp + "/profile/viewProfile/"), Json.toJson(usr.profile.map(prof => prof.orcidID.map(oid=> oid))))))),
                  "Publication Date" -> Json.toJson(format.format(c.created)),
                  "Comment" -> Json.toJson(JsArray(commentsJson)),
                  "Published In" -> Json.toJson(""),
                  "Alternative title" -> Json.toJson(c.name),
                  "External Identifier" -> Json.toJson(""),
                  "Proposed for publication" -> Json.toJson("true"),
                  "keyword" -> Json.toJson(
                    Json.toJson(c.datasets(0).tags.map(_.name))
                  ),
                  "@id" -> Json.toJson(hostIp + "/api/curations/" + c.id + "/ore#aggregation"),
                  "@type" -> Json.toJson(Seq("Aggregation", "http://cet.ncsa.uiuc.edu/2007/Dataset")),
                  "Is Version of" -> Json.toJson(hostIp + "/datasets/" + c.datasets(0).id ),
                  "similarTo" -> Json.toJson(hostIp + "/datasets/" + c.datasets(0).id ),
                  "aggregates" -> Json.toJson(JsArray(filesJson)),
                  "Has Part" -> Json.toJson("")

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
            val hostIp = Utils.baseUrl(request)
            val hostUrl = hostIp + "/api/curations/" + curationId + "/ore#aggregation"
            val userPrefMap = userService.findByIdentity(c.author).map(usr => usr.repositoryPreferences.map( pref => pref._1-> Json.toJson(pref._2.toString().split(",").toList))).getOrElse(Map.empty)
            val userPref = userPrefMap + ("Repository" -> Json.toJson(c.repository))
            val maxDataset = if (!c.files.isEmpty)  c.files.map(_.length).max else 0
            val totalSize = if (!c.files.isEmpty) c.files.map(_.length).sum else 0
            val valuetoSend = Json.obj(
              "@context" -> Json.toJson("https://w3id.org/ore/context"),
              "Aggregation" ->
                Map(
                  "Identifier" -> Json.toJson(hostIp +"/api/curations/" + curationId),
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
            val propertiesMap: Map[String, List[String]] = Map( "Access" -> List("Open", "Restricted", "Embargo", "Enclave"),
              "License" -> List("Creative Commons", "GPL") , "Cost" -> List("Free", "$XX Fee"),
              "Organizational Affiliation" -> List("UMich", "IU", "UIUC"))

            var jsonResponse: play.api.libs.json.JsValue = new JsArray()
            val result = futureResponse.map {
              case response =>
                if(response.status >= 200 && response.status < 300 || response.status == 304) {

                  jsonResponse = response.json
                }
            }

            val rs = Await.result(result, Duration.Inf)
            implicit object mmRuleFormat extends Format[mmRule] {
              def reads(json: JsValue): JsResult[mmRule] = JsSuccess(new mmRule(
                (json \ "Rule Name").as[String],
                (json \ "Score").as[Int],
                (json \ "Message").as[String]
              ))

              def writes(mm: mmRule): JsValue = JsObject(Seq(
                "Rule Name" -> JsString(mm.rule_name),
                "Score" -> JsNumber(mm.Score),
                "Message" -> JsString(mm.Message)
              ))
            }
            implicit object MatchMakerResponseFormat extends Format[MatchMakerResponse]{
              def reads(json: JsValue): JsResult[MatchMakerResponse] = JsSuccess(new MatchMakerResponse(
                (json \ "orgidentifier").as[String],
                (json \ "repositoryName").as[String],
                (json \  "Per Rule Scores").as[List[mmRule]],
                (json \ "Total Score").as[Int]
              ))

              def writes(mm: MatchMakerResponse): JsValue = JsObject(Seq(
                "orgidentifier" -> JsString(mm.orgidentifier),
                "repositoryName" -> JsString(mm.repositoryName),
                "Per Rule Scores" -> JsArray(mm.per_rule_score.map(toJson(_))),
                "Total Score" -> JsNumber(mm.total_score)
              ))
            }

            val mmResp = jsonResponse.as[List[MatchMakerResponse]].filter(_.orgidentifier != "null")

            Ok(toJson(mmResp))
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

}
