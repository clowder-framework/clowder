package services

import play.api.{ Plugin, Logger, Application }
import org.elasticsearch.node.NodeBuilder._
import org.elasticsearch.node.Node
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.common.xcontent.XContentFactory._
import java.util.Date
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.client.transport.NoNodeAvailableException
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.QueryBuilders

/**
 * Elasticsearch plugin.
 *
 * @author Luigi Marini
 *
 */
class ElasticsearchPlugin(application: Application) extends Plugin {

  var node: Node = null
  var client: TransportClient = null

  override def onStart() {
    val configuration = application.configuration
    try {
      node = nodeBuilder().clusterName("medici").client(true).node()
      val settings = ImmutableSettings.settingsBuilder()
      settings.put("client.transport.sniff", true)
      settings.build();
      client = new TransportClient(settings)
      client.addTransportAddress(new InetSocketTransportAddress("localhost", 9300))

      client.prepareIndex("data", "file")
      client.prepareIndex("data", "dataset")
      
      Logger.info("ElasticsearchPlugin has started")
    } catch {
      case nn: NoNodeAvailableException => Logger.error("Error connecting to elasticsearch: " + nn)
      case _ : Throwable => Logger.error("Unknown exception connecting to elasticsearch")
    }
  }

  def search(index: String, query: String): SearchResponse = {
    Logger.info("Searching ElasticSearch for " + query)
    
    val response = client.prepareSearch(index)
      .setTypes("file","dataset")
      .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
      .setQuery(QueryBuilders.matchQuery("_all", query))
      .setFrom(0).setSize(60).setExplain(true)
      .execute()
      .actionGet()
      
    Logger.info("Search hits: " + response.getHits().getTotalHits())
    response
  }

  /**
   * Index document using an arbitrary map of fields.
   */
  def index(index: String, docType: String, id: String, fields: List[(String, String)]) {
    var builder = jsonBuilder()
      .startObject()
      fields.map(fv => builder.field(fv._1, fv._2))
      builder.endObject()
    val response = client.prepareIndex(index, docType, id)
      .setSource(builder)
      .execute()
      .actionGet()
    Logger.info("Indexing document: " + response.getId())
  }

  def testQuery() {
    val response = client.prepareSearch("twitter")
      .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
      .setQuery(termQuery("user", "kimchy"))
      .setFrom(0).setSize(60).setExplain(true)
      .execute()
      .actionGet();
    Logger.info(response.toString())
  }

  def testIndex() {
    val response = client.prepareIndex("twitter", "tweet", "1")
      .setSource(jsonBuilder()
        .startObject()
        .field("user", "kimchy")
        .field("postDate", new Date())
        .field("message", "trying out Elastic Search")
        .endObject())
      .execute()
      .actionGet();
    Logger.info(response.toString())
  }

  override def onStop() {
    client.close()
    node.close()
    Logger.info("ElasticsearchPlugin has stopped")
  }
}
