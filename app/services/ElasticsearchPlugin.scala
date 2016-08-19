package services

import play.api.{Plugin, Logger, Application}
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.client.transport.NoNodeAvailableException
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.QueryBuilders
import models.{UUID, Collection, Dataset, File}
import scala.collection.mutable.ListBuffer
import scala.util.parsing.json.JSONArray
import java.text.SimpleDateFormat
import play.api.Play.current
import play.api.libs.json.JsValue
import _root_.util.SearchUtils


/**
 * Elasticsearch plugin.
 *
 */
class ElasticsearchPlugin(application: Application) extends Plugin {
  val comments: CommentService = DI.injector.getInstance(classOf[CommentService])
  val files: FileService = DI.injector.getInstance(classOf[FileService])
  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])
  var client: Option[TransportClient] = None
  val nameOfCluster = play.api.Play.configuration.getString("elasticsearchSettings.clusterName").getOrElse("clowder")
  val serverAddress = play.api.Play.configuration.getString("elasticsearchSettings.serverAddress").getOrElse("localhost")
  val serverPort = play.api.Play.configuration.getInt("elasticsearchSettings.serverPort").getOrElse(9300)

  override def onStart() {
    Logger.debug("Elasticsearchplugin started but not yet connected to Elasticsearch")
  }

  def connect(): Boolean = {
    if (client.isDefined) {
      Logger.debug("Already Connected to Elasticsearch")
      return true
    }
    try {
      val settings = ImmutableSettings.settingsBuilder().put("cluster.name", nameOfCluster).build()
      client = Some(new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress(serverAddress, serverPort)))
      Logger.debug("--- ElasticSearch Client is being created----")
      client match {
        case Some(x) => {
          Logger.debug("Index \"data\"  is being created if it does not exist ---")
          val indexSettings = ImmutableSettings.settingsBuilder().loadFromSource(jsonBuilder()
            .startObject()
            .startObject("analysis")
            .startObject("analyzer")
            .startObject("default")
            .field("type", "snowball")
            .endObject()
            .endObject()
            .endObject()
            .endObject().string())
          val indexExists = x.admin().indices().prepareExists("data").execute().actionGet().isExists()
          if (!indexExists) {
            x.admin().indices().prepareCreate("data").setSettings(indexSettings).execute().actionGet()
          }
          Logger.info("Connected to Elasticsearch")
          true
        }
        case None => {
          Logger.error("Error connecting to elasticsearch: No Client Created")
          false
        }
      }

    } catch {
      case nn: NoNodeAvailableException => {
        Logger.error("Error connecting to elasticsearch: " + nn)
        client.map(_.close())
        client = None
        false
      }
      case _: Throwable => {
        Logger.error("Unknown exception connecting to elasticsearch")
        client.map(_.close())
        client = None
        false
      }
    }
  }

  def search(index: String, query: String): SearchResponse = {
    connect
    Logger.info("Searching ElasticSearch for " + query)

    client match {
      case Some(x) => {
        Logger.info("Searching ElasticSearch for " + query)
        val response = x.prepareSearch(index)
          .setTypes("file", "dataset","collection")
          .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
          .setQuery(QueryBuilders.queryString(query).analyzer("snowball").analyzeWildcard(true))
          .setFrom(0).setSize(60).setExplain(true)
          .execute()
          .actionGet()
        Logger.info("Search hits: " + response.getHits().getTotalHits())
        response
      }
      case None => {
        Logger.error("Could not call search because we are not connected.")
        new SearchResponse()
      }
    }
  }

  def search(index: String, fields: Array[String], query: String): SearchResponse = {
    connect
    Logger.info("Searching ElasticSearch for " + query)
    client match {
      case Some(x) => {
        Logger.info("Searching ElasticSearch for " + query)
        var qbqs = QueryBuilders.queryString(query)
        for (f <- fields) {
          qbqs.field(f.trim())
        }
        val response = x.prepareSearch(index)
          .setTypes("file", "dataset","collection")
          .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
          .setQuery(qbqs.analyzer("snowball").analyzeWildcard(true))
          .setFrom(0).setSize(60).setExplain(true)
          .execute()
          .actionGet()
        Logger.info("Search hits: " + response.getHits().getTotalHits())
        response
      }
      case None => {
        Logger.error("Could not call search because we are not connected.")
        new SearchResponse()
      }
    }
  }

  def searchComplex(index: String, fields: Array[String], query: List[JsValue]): SearchResponse = {
    connect
    client match {
      case Some(x) => {
        Logger.info("Searching complex ElasticSearch for " + query)
        //var qbqs = QueryBuilders.queryString(query)
        //for (f <- fields) {
        //  qbqs.field(f.trim())
        //}

        val qb = QueryBuilders.boolQuery()
        query.foreach(jsq => {
          val key = (jsq \ "field_key").toString
          val operator = (jsq \ "operator").toString
          val value = (jsq \ "field_value").toString
          // Parse out key if it contains dot notation - of the format extractorName.{...nested fields...}.fieldName
          var extractor = ""
          var fieldKey = key
          if (key contains '.') {
            val keyTerms = key.split('.')
            extractor = keyTerms.head + " AND " // "extractorName AND "
            fieldKey = keyTerms.last // "fieldName"
          }

          operator match {
            case ":" => qb.must(QueryBuilders.termQuery(fieldKey, value))
            case "==" => qb.must(QueryBuilders.matchQuery(fieldKey, value))
            case "!=" => qb.mustNot(QueryBuilders.termQuery(fieldKey, value))
            case _ => {}
          }
        })

        Logger.debug(qb.toString)

        new SearchResponse()
      }
      case None => {
        Logger.error("Could not call search because we are not connected.")
        new SearchResponse()
      }
    }
  }

  /**
    * Index document using an arbitrary map of fields.
    */
  def index(index: String, docType: String, id: UUID, fields: List[(String, JsValue)]) {
    connect
    client match {
      case Some(x) => {
        val builder = jsonBuilder()
          .startObject()
        fields.map(fv => builder.field(fv._1, fv._2))
        builder.endObject()
        val response = x.prepareIndex(index, docType, id.toString())
          .setSource(builder)
          .execute()
          .actionGet()
        Logger.info("Indexing document: " + response.getId)
      }
      case None => Logger.error("Could not call index because we are not connected.")
    }
  }

  /**
    * Index document using an arbitrary map of fields.
    */
  def index(index: String, id: UUID, esObj: Option[models.ElasticSearchObject]) {
    esObj match {
      case Some(eso) => {
        connect
        client match {
          case Some(x) => {
            val builder = jsonBuilder()
            .startObject()

            builder.field("creator", eso.creator)
            builder.field("created", eso.created)
            builder.field("parent_of", eso.parent_of)
            builder.field("child_of", eso.child_of)
            builder.field("tags", eso.tags)
            builder.field("comments", eso.comments)
            builder.field("metadata", eso.metadata)

            builder.endObject()
            val response = x.prepareIndex(index, eso.doctype.resourceType.name, id.toString())
            .setSource(builder)
            .execute()
            .actionGet()
            Logger.info("Indexing document: " + response.getId)
          }
          case None => Logger.error("Could not call index because we are not connected.")
        }
      }
      case None => Logger.error("No ElasticSearchObject given; could not index "+id.toString)
    }

  }

  /** delete all indices */
  def deleteAll {
    connect
    client match {
      case Some(x) => {
        val response = x.admin().indices().prepareDelete("_all").get()
        if (!response.isAcknowledged())
          Logger.error("Did not delete all data from elasticsearch.")
      }
      case None => Logger.error("Could not call index because we are not connected.")
    }
  }

  def delete(index: String, docType: String, id: String) {
    connect
    client match {
      case Some(x) => {
        val response = x.prepareDelete(index, docType, id).execute().actionGet()
        Logger.info("Deleting document: " + response.getId)

      }
      case None => Logger.error("Could not call index because we are not connected.")
    }

  }

  /**
   * Reindex the given collection, if recursive is set to true it will
   * also reindex all datasets and files.
   */
  def index(collection: Collection, recursive: Boolean) {
    connect
    // Perform recursion first if necessary
    for (dataset <- datasets.listCollection(collection.id.toString)) {
      if (recursive) {
        index(dataset, recursive)
      }
    }
    index("data", collection.id, SearchUtils.getElasticSearchObject(collection))
  }

  /**
   * Reindex the given dataset, if recursive is set to true it will
   * also reindex all files.
   */
  def index(dataset: Dataset, recursive: Boolean) {
    connect
    // Perform recursion first if necessary
    for (fileId <- dataset.files) {
      files.get(fileId) match {
        case Some(f) => {
          if (recursive) {
            index(f)
          }
        }
        case None => Logger.error(s"Error getting file $fileId for recursive indexing")
      }
    }
    index("data", dataset.id, SearchUtils.getElasticSearchObject(dataset))
  }

  /**
   * Reindex the given file.
   */
  def index(file: File) {
    connect
    index("data", file.id, SearchUtils.getElasticSearchObject(file))
  }

  override def onStop() {
    client.map(_.close())
    client = None
    Logger.info("ElasticsearchPlugin has stopped")
  }

}
