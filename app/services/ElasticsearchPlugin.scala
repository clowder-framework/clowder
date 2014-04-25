package services

import play.api.{ Plugin, Logger, Application }
import play.api.Play.current
import org.elasticsearch.node.NodeBuilder._
import org.elasticsearch.node.Node
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.client.transport.NoNodeAvailableException
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.QueryBuilders
import models.{UUID, Dataset}
import scala.collection.mutable.ListBuffer
import scala.util.parsing.json.JSONArray

/**
 * Elasticsearch plugin.
 *
 * @author Luigi Marini
 *
 */
class ElasticsearchPlugin(application: Application) extends Plugin {

  var node: Node = null
  var client: TransportClient = null
  val comments: CommentService = DI.injector.getInstance(classOf[CommentService])

  override def onStart() {
    val configuration = application.configuration
    try {
      var nameOfCluster = play.api.Play.configuration.getString("elasticsearchSettings.clusterName").getOrElse("medici")
      var serverAddress = play.api.Play.configuration.getString("elasticsearchSettings.serverAddress").getOrElse("localhost")
      var serverPort = play.api.Play.configuration.getInt("elasticsearchSettings.serverPort").getOrElse(9300)
      
      node = nodeBuilder().clusterName(nameOfCluster).client(true).node()
      val settings = ImmutableSettings.settingsBuilder()
      settings.put("client.transport.sniff", true)
      settings.build();
      client = new TransportClient(settings)
      client.addTransportAddress(new InetSocketTransportAddress(serverAddress, serverPort))

      client.prepareIndex("data", "file")
      client.prepareIndex("data", "dataset")

      Logger.info("ElasticsearchPlugin has started")
    } catch {
      case nn: NoNodeAvailableException => Logger.error("Error connecting to elasticsearch: " + nn)
      case _: Throwable => Logger.error("Unknown exception connecting to elasticsearch")
    }
  }

  def search(index: String, query: String): SearchResponse = {
    Logger.info("Searching ElasticSearch for " + query)

    val response = client.prepareSearch(index)
      .setTypes("file", "dataset")
      .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
      .setQuery(QueryBuilders.queryString(query))
      .setFrom(0).setSize(60).setExplain(true)
      .execute()
      .actionGet()

    Logger.info("Search hits: " + response.getHits().getTotalHits())
    response
  }

  /**
   * Index document using an arbitrary map of fields.
   */
  def index(index: String, docType: String, id: UUID, fields: List[(String, String)]) {
    var builder = jsonBuilder()
      .startObject()
    fields.map(fv => builder.field(fv._1, fv._2))
    builder.endObject()
    val response = client.prepareIndex(index, docType, id.stringify)
      .setSource(builder)
      .execute()
      .actionGet()
    Logger.info("Indexing document: " + response.getId())
  }

  def indexDataset(dataset: Dataset) {
    var tagListBuffer = new ListBuffer[String]()

    for (tag <- dataset.tags) {
      tagListBuffer += tag.name
    }

    val tagsJson = new JSONArray(tagListBuffer.toList)

    Logger.debug("tagStr=" + tagsJson);

    val commentsByDataset = for (comment <- comments.findCommentsByDatasetId(dataset.id, false)) yield {
      comment.text
    }
    val commentJson = new JSONArray(commentsByDataset)

    Logger.debug("commentStr=" + commentJson.toString())

    index("data", "dataset", dataset.id,
      List(("name", dataset.name), ("description", dataset.description), ("tag", tagsJson.toString), ("comments", commentJson.toString)))
  }

  override def onStop() {
    client.close()
    node.close()
    Logger.info("ElasticsearchPlugin has stopped")
  }
}
