package api

import javax.inject.Inject

import com.wordnik.swagger.annotations.ApiOperation
import models.User
import play.api.Play._
import play.api.libs.json.{JsValue, Json}
import securesocial.core.Identity
import services._
import services.mongodb.MongoSalatPlugin

import scala.collection.mutable

/**
 * class that contains all status/version information about medici.
 *
 * @author Rob Kooper
 */
class Status @Inject()(collections: CollectionService,
                       datasets: DatasetService,
                       files: FileService,
                       users: UserService,
                       appConfig: AppConfigurationService,
                       extractors: ExtractorService) extends ApiController {
  val jsontrue = Json.toJson(true)
  val jsonfalse = Json.toJson(false)

  @ApiOperation(value = "version",
    notes = "returns the version information",
    responseClass = "None", httpMethod = "GET")
  def version = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.Public)) { request =>
    Ok(Json.obj("version" -> getVersionInfo))
  }

  @ApiOperation(value = "status",
    notes = "returns the status information",
    responseClass = "None", httpMethod = "GET")
  def status = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.Public)) { request =>

    Ok(Json.obj("version" -> getVersionInfo,
      "counts" -> getCounts,
      "plugins" -> getPlugins(request.user),
      "extractors" -> Json.toJson(extractors.getExtractorNames())))
  }

  def getPlugins(user: Option[Identity]): JsValue = {
    val result = new mutable.HashMap[String, JsValue]()

    // mongo
    result.put("mongo", current.plugin[MongoSalatPlugin] match {
      case Some(p) => {
        if (WithPermission(Permission.Admin).isAuthorized(user)) {
          Json.obj("uri" -> p.mongoURI.toString(),
            "updates" -> appConfig.getProperty[List[String]]("mongodb.updates", List.empty[String]))
        } else {
          jsontrue
        }
      }
      case None => jsonfalse
    })

    // elasticsearch
    result.put("elasticsearch", current.plugin[ElasticsearchPlugin] match {
      case Some(p) => {
        if (WithPermission(Permission.Admin).isAuthorized(user)) {
          jsontrue
        } else {
          jsontrue
        }
      }
      case None => jsonfalse
    })

    // rabbitmq
    result.put("rabbitmq", current.plugin[RabbitmqPlugin] match {
      case Some(p) => {
        if (WithPermission(Permission.Admin).isAuthorized(user)) {
          Json.obj("uri" -> p.rabbitmquri)
        } else {
          jsontrue
        }
      }
      case None => jsonfalse
    })

    // geostream
    result.put("geostream", current.plugin[PostgresPlugin] match {
      case Some(p) => {
        if (WithPermission(Permission.Admin).isAuthorized(user)) {
          Json.obj("database" -> p.conn.getSchema)
        } else {
          jsontrue
        }
      }
      case None => jsonfalse
    })

    // versus
    result.put("versus", current.plugin[VersusPlugin] match {
      case Some(p) => {
        if (WithPermission(Permission.Admin).isAuthorized(user)) {
          Json.obj("host" -> configuration.getString("versus.host").getOrElse("").toString)
        } else {
          jsontrue
        }
      }
      case None => jsonfalse
    })

    Json.toJson(result.toMap[String, JsValue])
  }

  def getCounts: JsValue = {
    Json.obj("collections" -> collections.count(),
      "datasets" -> datasets.count(),
      "files" -> files.count(),
      "users" -> users.count())
  }

  def getVersionInfo: JsValue = {
    val sha1 = sys.props.getOrElse("build.gitsha1", default = "unknown")

    // TODO use the following URL to indicate if there updates to Medici.
    // if returned object has an empty values medici is up to date
    // need to figure out how to pass in the branch
    //val checkurl = "https://opensource.ncsa.illinois.edu/stash/rest/api/1.0/projects/MMDB/repos/medici-play/commits?since=" + sha1

    Json.obj("number" -> sys.props.getOrElse("build.version", default = "0.0.0").toString,
      "build" -> sys.props.getOrElse("build.bamboo", default = "development").toString,
      "branch" -> sys.props.getOrElse("build.branch", default = "unknown").toString,
      "gitsha1" -> sha1)
  }
}
