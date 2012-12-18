package service

import play.api.{Plugin, Logger, Application}
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
    node = nodeBuilder().client(true).node()
    val settings = ImmutableSettings.settingsBuilder()
    settings.put("client.transport.sniff", true)
    settings.build();
    client = new TransportClient(settings)
    client.addTransportAddress(new InetSocketTransportAddress("localhost", 9300))
    testIndex()
    testQuery()
    Logger.info("ElasticsearchPlugin has started")
    } catch {
      case nn: NoNodeAvailableException => Logger.error("Error connecting to elasticsearch: " + nn)
      case _ => Logger.error("Unknown exception connecting to elasticsearch")
    }
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
                                    .endObject()
                                   )
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