package api

import javax.inject.Inject

import com.wordnik.swagger.annotations.ApiOperation
import play.api.Play._
import play.api.libs.json.{JsValue, Json}
import services._

/**
 * class that contains all status/version information about medici.
 *
 * @author Rob Kooper
 */
class Status @Inject()(collections: CollectionService,
                       datasets: DatasetService,
                       files: FileService,
                       users: UserService) extends ApiController {
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
      "elasticsearch" -> current.plugin[ElasticsearchPlugin].isDefined,
      "geostream" -> current.plugin[PostgresPlugin].isDefined))
  }

  def getCounts: JsValue = {
    Json.obj("collections" -> collections.count(),
      "datasets" -> datasets.count(),
      "files" -> files.count(),
      "users" -> users.count())

  }

  def getVersionInfo: JsValue = {
    Json.obj("number" -> sys.props.getOrElse("build.version", default = "0.0.0").toString,
      "build" -> sys.props.getOrElse("build.bamboo", default = "development").toString,
      "branch" -> sys.props.getOrElse("build.branch", default = "unknown").toString,
      "gitsha1" -> sys.props.getOrElse("build.gitsha1", default = "unknown").toString)
  }
}
