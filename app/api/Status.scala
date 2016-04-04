package api

import javax.inject.Inject

import com.wordnik.swagger.annotations.ApiOperation
import play.api.Logger
import models.User
import play.api.Play._
import play.api.libs.json.{JsValue, Json}
import services._
import services.mongodb.MongoSalatPlugin

import scala.collection.mutable

/**
 * class that contains all status/version information about medici.
 */
class Status @Inject()(spaces: SpaceService,
                       collections: CollectionService,
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
  def version = UserAction(needActive=false) { implicit request =>
    Ok(Json.obj("version" -> getVersionInfo))
  }

  @ApiOperation(value = "status",
    notes = "returns the status information",
    responseClass = "None", httpMethod = "GET")
  def status = UserAction(needActive=false) { implicit request =>

    Ok(Json.obj("version" -> getVersionInfo,
      "counts" -> getCounts,
      "plugins" -> getPlugins(request.user),
      "extractors" -> Json.toJson(extractors.getExtractorNames())))
  }

  def getPlugins(user: Option[User]): JsValue = {
    val result = new mutable.HashMap[String, JsValue]()

    current.plugins foreach {
      // mongo
      case p: MongoSalatPlugin => {
        result.put("mongo", if (Permission.checkServerAdmin(user)) {
              Json.obj("uri" -> p.mongoURI.toString(),
                "updates" -> appConfig.getProperty[List[String]]("mongodb.updates", List.empty[String]))
            } else {
              jsontrue
            })
      }

      // elasticsearch
      case p: ElasticsearchPlugin => {
        val status = if (p.connect) {
          "connected"
        } else {
          "disconnected"
        }
        result.put("elasticsearch", if (Permission.checkServerAdmin(user)) {
          Json.obj("server" -> p.serverAddress,
            "clustername" -> p.nameOfCluster,
            "status" -> status)
        } else {
          Json.obj("status" -> status)
        })
      }

      // rabbitmq
      case p: RabbitmqPlugin => {
        val status = if (p.connect) {
          "connected"
        } else {
          "disconnected"
        }
        result.put("rabbitmq", if (Permission.checkServerAdmin(user)) {
          Json.obj("uri" -> p.rabbitmquri,
            "exchange" -> p.exchange,
            "status" -> status)
        } else {
          Json.obj("status" -> status)
        })
      }

      // geostream
      case p: PostgresPlugin => {
        val status = if (p.conn != null) {
          "connected"
        } else {
          "disconnected"
        }
        result.put("rabbitmq", if (Permission.checkServerAdmin(user)) {
          Json.obj("catalog" -> p.conn.getCatalog,
            "schema" -> p.conn.getSchema,
            "updates" -> appConfig.getProperty[List[String]]("postgres.updates", List.empty[String]),
            "status" -> status)
        } else {
          Json.obj("status" -> status)
        })
      }

      // versus
      case p: VersusPlugin => {
        result.put("versus", if (Permission.checkServerAdmin(user)) {
          Json.obj("host" -> configuration.getString("versus.host").getOrElse("").toString)
        } else {
          jsontrue
        })
      }

      case p: ToolManagerPlugin => {
        result.put("toolmanager", if (Permission.checkServerAdmin(user)) {
          Json.obj("host" -> configuration.getString("toolmanagerURI").getOrElse("").toString)
        } else {
          jsontrue
        })
      }

      case p => {
        val name = p.getClass.getName
        if (name.startsWith("services.")) {
          val status = if (p.enabled) {
            "enabled"
          } else {
            "disabled"
          }
          result.put(p.getClass.getName, Json.obj("status" -> status))
        } else {
          Logger.debug(s"Ignoring ${name} plugin")
        }
      }
    }

    Json.toJson(result.toMap[String, JsValue])
  }

  def getCounts: JsValue = {
    Json.obj("spaces" -> spaces.count(),
      "collections" -> collections.count(),
      "datasets" -> datasets.count(),
      "files" -> files.count(),
      "users" -> users.count())
  }

  def getVersionInfo: JsValue = {
    val sha1 = sys.props.getOrElse("build.gitsha1", default = "unknown")

    // TODO use the following URL to indicate if there updates to Medici.
    // if returned object has an empty values medici is up to date
    // need to figure out how to pass in the branch
    //val checkurl = "https://opensource.ncsa.illinois.edu/stash/rest/api/1.0/projects/CATS/repos/clowder/commits?since=" + sha1

    Json.obj("number" -> sys.props.getOrElse("build.version", default = "0.0.0").toString,
      "build" -> sys.props.getOrElse("build.bamboo", default = "development").toString,
      "branch" -> sys.props.getOrElse("build.branch", default = "unknown").toString,
      "gitsha1" -> sha1)
  }
}
