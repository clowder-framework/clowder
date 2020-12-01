package api

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.User
import play.api.libs.json.{JsValue, Json}
import services._
import services.mongodb.MongoSalatPlugin
import util.silhouette.auth.ClowderEnv

import scala.collection.mutable

/**
 * class that contains all status/version information about clowder.
 */
class Status @Inject()(appConfig: AppConfigurationService,
                       extractors: ExtractorService,
                       mongo: MongoSalatPlugin,
                       searches: SearchService,
                       silhouette: Silhouette[ClowderEnv]) extends ApiController {
  val jsontrue = Json.toJson(true)
  val jsonfalse = Json.toJson(false)

  def version = UserAction(needActive=false) { implicit request =>
    Ok(Json.obj("version" -> getVersionInfo))
  }

  def status = silhouette.SecuredAction { implicit request =>
    Ok(Json.obj("version" -> getVersionInfo,
      "counts" -> getCounts(Some(request.identity: User)),
      "plugins" -> getPlugins(Some(request.identity: User)),
      "extractors" -> Json.toJson(extractors.getExtractorNames(List.empty))))
  }

  def getPlugins(user: Option[User]): JsValue = {
    val result = new mutable.HashMap[String, JsValue]()

    if (searches.isEnabled()) {
      result.put("elasticsearch", if (Permission.checkServerAdmin(user)) {
        searches.getInformation()
      } else Json.obj({ "status" -> "connected"}))
    } else {
      result.put("elasticsearch", Json.obj("status" -> "disconnected"))
    }

    result.put("mongo", if (Permission.checkServerAdmin(user)) {
      Json.obj("uri" -> mongo.mongoURI.toString(),
        "updates" -> appConfig.getProperty[List[String]]("mongodb.updates", List.empty[String]))
    } else {
      jsontrue
    })

    Json.toJson(result.toMap[String, JsValue])
  }

  def getCounts(user: Option[User]): JsValue = {
    val counts = appConfig.getIndexCounts()
    // TODO: Revisit this check as it is currently too slow
    //val fileinfo = if (Permission.checkServerAdmin(user)) {
    //  Json.toJson(files.statusCount().map{x => x._1.toString -> Json.toJson(x._2)})
    //} else {
    //  Json.toJson(counts.numFiles)
    //}
    val fileinfo = counts.numFiles
    Json.obj("spaces" -> counts.numSpaces,
      "collections" -> counts.numCollections,
      "datasets" -> counts.numDatasets,
      "files" -> fileinfo,
      "bytes" -> counts.numBytes,
      "users" -> counts.numUsers)
  }

  def getVersionInfo: JsValue = {
    val sha1 = sys.props.getOrElse("build.gitsha1", default = "unknown")

    // TODO use the following URL to indicate if there updates to clowder.
    // if returned object has an empty values clowder is up to date
    // need to figure out how to pass in the branch
    //val checkurl = "https://opensource.ncsa.illinois.edu/stash/rest/api/1.0/projects/CATS/repos/clowder/commits?since=" + sha1

    Json.obj("number" -> sys.props.getOrElse("build.version", default = "0.0.0").toString,
      "build" -> sys.props.getOrElse("build.bamboo", default = "development").toString,
      "branch" -> sys.props.getOrElse("build.branch", default = "unknown").toString,
      "gitsha1" -> sha1)
  }
}
