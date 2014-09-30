package services

import play.api.{Plugin, Logger, Application}
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
import java.text.SimpleDateFormat

/**
 * Elasticsearch plugin.
 *
 * @author Luigi Marini
 *
 */
class ElasticsearchPlugin(application: Application) extends Plugin {    var client: Option[TransportClient] = null
  val comments: CommentService = DI.injector.getInstance(classOf[CommentService])
  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])
  var node: Option[Node] = None
  override def onStart() {
    val configuration = application.configuration
    try {
      node = Some(nodeBuilder().clusterName("medici").client(true).node())
      val settings = ImmutableSettings.settingsBuilder()
      settings.put("cluster.name","medici") //uncomment this if the cluster name is "medici"
      settings.put("client.transport.sniff", true)
      settings.build();
      client = Some(new TransportClient(settings))
      client match {
        case Some(x) => {
	      x.addTransportAddress(new InetSocketTransportAddress("localhost", 9300))
	      x.prepareIndex("data", "file").execute()
	      x.prepareIndex("data", "dataset").execute()  
	      x.prepareIndex("data", "collection").execute()
        }
        case None=>{
          node match {
          case Some(x) => x.close
        }
        node = None
        }
      }
     Logger.info("ElasticsearchPlugin has started")
    } catch {
      case nn: NoNodeAvailableException => {Logger.error("Error connecting to elasticsearch: " + nn)
        client = None
        node match {
          case Some(x) => x.close
        }
        node = None
      }
      
      case _: Throwable => {
        Logger.error("Unknown exception connecting to elasticsearch")
        client = None
       }
    }
  }

  def search(index: String, query: String): SearchResponse = {
    Logger.info("Searching ElasticSearch for " + query)

    client match {
      case Some(x) => {
	    Logger.info("Searching ElasticSearch for " + query)
	    val response = x.prepareSearch(index)
	      .setTypes("file","dataset")
	      .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
	     // .setQuery(QueryBuilders.matchQuery("_all", query))
	      .setQuery(QueryBuilders.queryString(query))
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
    

  /**
   * Index document using an arbitrary map of fields.
   */
  def index(index: String, docType: String, id: UUID, fields: List[(String, String)]) {
    var builder = jsonBuilder()
      .startObject()
    fields.map(fv => builder.field(fv._1, fv._2))
    builder.endObject()
    
    client match {
     case Some(x) => {
	    val builder = jsonBuilder()
	      .startObject()
	      fields.map(fv => builder.field(fv._1, fv._2))
	      builder.endObject()
	    val response = x.prepareIndex(index, docType, id.toString)
	      .setSource(builder)
	      .execute()
	      .actionGet()
	    Logger.info("Indexing document: " + response.getId())
      }
     case None => Logger.error("Could not call index because we are not connected.")
    }
  }

  def delete(index: String, docType: String, id: String) {   
    client match {
      case Some(x)=>{
         val response = x.prepareDelete(index,docType,id).execute().actionGet()
         Logger.info("Deleting document: " + response.getId())
        
      }
      case None=> Logger.error("Could not call index because we are not connected.")
    }
       
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
    
    val usrMd = datasets.getUserMetadataJSON(dataset.id)
    Logger.debug("usrmd=" + usrMd)

    val techMd = datasets.getTechnicalMetadataJSON(dataset.id)
    Logger.debug("techmd=" + techMd)

    val xmlMd = datasets.getXMLMetadataJSON(dataset.id)
    Logger.debug("xmlmd=" + xmlMd)
    
    var fileDsId = ""
    var fileDsName = ""          
    for(file <- dataset.files){
    	fileDsId = fileDsId + file.id.stringify + "  "
    	fileDsName = fileDsName + file.filename + "  "
    }

    var dsCollsId = ""
    var dsCollsName = ""
      
    for(collection <- collections.listInsideDataset(dataset.id)){
    	dsCollsId = dsCollsId + collection.id.stringify + " %%% "
    	dsCollsName = dsCollsName + collection.name + " %%% "
    }

    val formatter = new SimpleDateFormat("dd/MM/yyyy")

    index("data", "dataset", dataset.id,
    		List(("name", dataset.name), ("description", dataset.description), ("author",dataset.author.fullName),("created",formatter.format(dataset.created)), ("fileId",fileDsId),("fileName",fileDsName), ("collId",dsCollsId),("collName",dsCollsName), ("tag", tagsJson.toString), ("comments", commentJson.toString), ("usermetadata", usrMd), ("technicalmetadata", techMd), ("xmlmetadata", xmlMd)  ))

  }

  override def onStop() {
    client match {
      case Some(x) => x.close
    }
    client = None
    node match {
          case Some(x) => x.close
        }
        node = None
    Logger.info("ElasticsearchPlugin has stopped")
   }
    
    
}
