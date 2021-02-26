package api

import javax.inject.Inject

import play.api.Logger
import models.User
import play.api.Play._
import play.api.libs.json.{JsValue, Json}
import services._
import services.mongodb.MongoSalatPlugin

import scala.collection.mutable

/**
 * class that contains all status/version information about clowder.
 */
class Status @Inject()(spaces: SpaceService,
                       collections: CollectionService,
                       datasets: DatasetService,
                       files: FileService,
                       users: UserService,
                       appConfig: AppConfigurationService,
                       extractors: ExtractorService,
                       elasticqueue: ElasticsearchQueue) extends ApiController {
  val jsontrue = Json.toJson(true)
  val jsonfalse = Json.toJson(false)

  def version = UserAction(needActive=false) { implicit request =>
    Ok(Json.obj("version" -> getVersionInfo))
  }

  def status = UserAction(needActive=false) { implicit request =>

    Ok(Json.obj("version" -> getVersionInfo,
      "counts" -> getCounts(request.user),
      "plugins" -> getPlugins(request.user),
      "extractors" -> Json.toJson(extractors.getExtractorNames(List.empty))))
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
        val status = if (p.isEnabled()) {
          "connected"
        } else {
          "disconnected"
        }

        result.put("elasticsearch", if (Permission.checkServerAdmin(user)) {
          Json.obj("server" -> p.serverAddress,
            "clustername" -> p.nameOfCluster,
            "queue" -> elasticqueue.status(),
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
        result.put("postgres", if (Permission.checkServerAdmin(user)) {
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
        val status = if (p.enabled) {
          "enabled"
        } else {
          "disabled"
        }
        result.put("toolmanager", if (Permission.checkServerAdmin(user)) {
          Json.obj("host" -> configuration.getString("toolmanagerURI").getOrElse("").toString,
            "tools" -> p.getLaunchableTools(),
            "status" -> status)
        } else {
          Json.obj("status" -> status)
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

    // messageService
    // FIXME change key `rabbitmq` to something more generic like `messageService`. Keeping as is for backward compatibility.
    val messageService: MessageService = DI.injector.getInstance(classOf[MessageService])
    if (Permission.checkServerAdmin(user)) {
      result.put("rabbitmq", messageService.getInfo(true))
    } else {
      result.put("rabbitmq", messageService.getInfo(false))
    }

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
