package services

import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest
import org.elasticsearch.common.xcontent.XContentBuilder
import play.api.libs.concurrent.Akka
import scala.concurrent.duration._
import scala.util.Try
import scala.collection.mutable.{MutableList, ListBuffer}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.{Plugin, Logger, Application}
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.client.transport.NoNodeAvailableException
import org.elasticsearch.action.search.SearchResponse
import models.{Collection, Dataset, File, UUID, ResourceRef}
import play.api.Play.current
import play.api.libs.json._
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
  val nameOfIndex = play.api.Play.configuration.getString("elasticsearchSettings.indexNamePrefix").getOrElse("clowder")

  override def onStart() {
    Logger.debug("ElasticsearchPlugin started but not yet connected")
    connect
  }

  def connect(): Boolean = {
    if (client.isDefined) {
      //Logger.debug("Already connected to Elasticsearch")
      return true
    }
    try {
      val settings = ImmutableSettings.settingsBuilder().put("cluster.name", nameOfCluster).build()
      client = Some(new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress(serverAddress, serverPort)))
      Logger.debug("--- Elasticsearch Client is being created----")
      client match {
        case Some(x) => {
          val indexSettings = ImmutableSettings.settingsBuilder().loadFromSource(jsonBuilder()
            .startObject()
              .startObject("analysis")
                .startObject("analyzer")
                  .startObject("default")
                    .field("type", "snowball")
            .endObject().endObject().endObject().endObject().string())
          val indexExists = x.admin().indices().prepareExists(nameOfIndex+"-data").execute().actionGet().isExists()

          if (!indexExists) {
            Logger.debug("Index \""+nameOfIndex+"-data\" does not exist; creating now ---")
            x.admin().indices().prepareCreate(nameOfIndex+"-data").setSettings(indexSettings).execute().actionGet()

            //TODO: use something like api.routes.Admin.reindex() instead?
            Akka.system.scheduler.scheduleOnce(1 seconds) {
              Logger.debug("Reindexing all documents")
              collections.index(None)
              datasets.index(None)
              files.index(None)
            }
          } else {
            // Check whether this still has deprecated mappaing in "-data" index and delete if so
            val mappings = x.admin().indices().prepareGetMappings(nameOfIndex+"-data").execute().actionGet().getMappings()
            // TODO: if (mappings.get(nameOfIndex+"-data").containsKey("dataset")) {
            if (mappings.get(nameOfIndex+"-data").containsKey("clowder_object")) {
              Logger.debug("Index \""+nameOfIndex+"-data\" already exists but requires update and reindexing ---")
              deleteAll
              x.admin().indices().prepareCreate(nameOfIndex+"-data").setSettings(indexSettings).execute().actionGet()

              //TODO: use something like api.routes.Admin.reindex() instead?
              Akka.system.scheduler.scheduleOnce(1 seconds) {
                Logger.debug("Reindexing all documents")
                collections.index(None)
                datasets.index(None)
                files.index(None)
              }
            } else {
              Logger.debug("Index \"data\" already exists ---")
            }
          }

          Logger.info("Connected to Elasticsearch")
          true
        }
        case None => {
          Logger.error("Error connecting to Elasticsearch: No client created")
          false
        }
      }

    } catch {
      case nn: NoNodeAvailableException => {
        Logger.error("Error connecting to Elasticsearch: " + nn)
        client.map(_.close())
        client = None
        false
      }
      case _: Throwable => {
        Logger.error("Unknown exception connecting to Elasticsearch")
        client.map(_.close())
        client = None
        false
      }
    }
  }

  /** Prepare and execute Elasticsearch query, and return list of matching ResourceRefs */
  def search(query: List[JsValue]): List[ResourceRef] = {
    /** Each item in query list has properties:
      *   "field_key":      full name of field to query, e.g. 'extractors.wordCount.lines'
      *   "operator":       type of query for this term, e.g. '=='
      *   "field_value":    value to search for using specified field & operator
      *   "extractor_key":  name of extractor component only, e.g. 'extractors.wordCount'
      *   "field_leaf_key": name of immediate field only, e.g. 'lines'
      */
    val queryObj = SearchUtils.prepareElasticJsonQuery(query)
    val response: SearchResponse = _search(queryObj)

    var results = MutableList[ResourceRef]()
    for (hit <- response.getHits().getHits()) {
      val resource_type = hit.getSource().get("resource_type").toString
      results += new ResourceRef(Symbol(resource_type), UUID(hit.getId()))
    }

    results.toList
  }

  /** Search using a simple text string */
  def search(query: String, index: String = nameOfIndex+"-data"): List[ResourceRef] = {
    val specialOperators = List(":", "==", "!=", "<", ">")
    val queryObj = if (specialOperators.exists(query.contains(_))) {
      // Parse search string into object based on special operators
      SearchUtils.prepareElasticJsonQuery(query)
    } else {
      // Plain text search with no field qualifiers
      jsonBuilder().startObject()
        .startObject("match").field("_all", query.replaceAll("([+:/\\\\])", "\\\\$1")).endObject()
      .endObject()
    }

    val response = _search(queryObj, index)

    var results = MutableList[ResourceRef]()
    for (hit <- response.getHits().getHits()) {
      val resource_type = hit.getSource().get("resource_type").toString
      results += new ResourceRef(Symbol(resource_type), UUID(hit.getId()))
    }

    results.toList
  }

  /*** Execute query */
  def _search(queryObj: XContentBuilder, index: String = nameOfIndex+"-data"): SearchResponse = {
    connect
    client match {
      case Some(x) => {
        Logger.info("Searching Elasticsearch")

        val response = x.prepareSearch(index)
          .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
          .setQuery(queryObj)
          .setFrom(0).setSize(60).setExplain(true)
          .execute()
          .actionGet()
        Logger.info("Search hits: " + response.getHits().getTotalHits())
        response
      }
      case None => {
        Logger.error("Could not call search because Elasticsearch is not connected.")
        new SearchResponse()
      }
    }
  }

  /** Delete all indices */
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

  /** Delete an index */
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

  /** Flatten json object to have a single level of key/values with dot notation for nesting */
  def flattenJson(js: JsValue, prefix: String = ""): JsObject = {
    // From http://stackoverflow.com/questions/24273433/play-scala-how-to-flatten-a-json-object

    // We will use this substring to trim extractor names from key strings
    //    e.g. "http://localhost:9000/clowder/api/extractors/wordCount.lines" -> "wordCount.lines"
    val extractorString = "/extractors/"

    js.as[JsObject].fields.foldLeft(Json.obj()) {
      // value is sub-object so recursively handle
      case (acc, (k, v: JsObject)) => {
        val key = if (k contains extractorString) {
          k.substring(k.indexOf(extractorString)+extractorString.length())
        } else k

        if(prefix.isEmpty) acc.deepMerge(flattenJson(v, key))
        else acc.deepMerge(flattenJson(v, s"$prefix.$key"))
      }
      case (acc, (k, v)) => {
        val key = if (k contains extractorString) {
          k.substring(k.indexOf(extractorString)+extractorString.length())
        } else k

        if(prefix.isEmpty) acc + (key -> v)
        else acc + (s"$prefix.$key" -> v)
      }
    }
  }

  /** Traverse metadata field mappings to get unique list for autocomplete */
  def getAutocompleteFields(query: String, index: String = nameOfIndex+"-data"): List[String] = {
    connect

    var listOfTerms = ListBuffer.empty[String]
    client match {
      case Some(x) => {
        Logger.debug("Getting autocomplete suggestions for: " + query)
        if (query != "") {
          val response = x.admin.indices.getMappings(new GetMappingsRequest().indices(index)).get()
          val maps = response.getMappings().get(index).get("clowder_object")
          val resultList = convertJsMappingToFields(Json.parse(maps.source().toString).as[JsObject])

          resultList.foreach(term => {
            if ((term.toLowerCase startsWith query.toLowerCase) && !(listOfTerms contains term))
              listOfTerms += term
          })
        }
      }
      case None => Logger.error("Could not get metadata autocomplete suggestions; Elasticsearch is not connected.")
    }

    listOfTerms.toList
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
    index(SearchUtils.getElasticsearchObject(collection))
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
    index(SearchUtils.getElasticsearchObject(dataset))
  }

  /** Reindex the given file. */
  def index(file: File) {
    connect
    index(SearchUtils.getElasticsearchObject(file))
  }

  /** Index document using an arbitrary map of fields. */
  def index(esObj: Option[models.ElasticsearchObject], index: String = nameOfIndex+"-data") {
    esObj match {
      case Some(eso) => {
        connect
        client match {
          case Some(x) => {
            // Construct the JSON document for indexing
            val builder = jsonBuilder()
              .startObject()
              // BASIC INFO
              .field("creator", eso.creator)
              .field("created", eso.created)
              .field("resource_type", eso.resource.resourceType.name)
              .field("name", eso.name)

            // PARENT_OF/CHILD_OF
            builder.startArray("parent_of")
            eso.parent_of.foreach( parent_id => builder.value(parent_id) )
            builder.endArray()
            builder.startArray("child_of")
            eso.child_of.foreach( child_id => builder.value(child_id) )
            builder.endArray()

            // TAGS
            builder.startArray("tags")
            eso.tags.foreach( t => {
              builder.startObject()
                .field("creator", t.creator)
                .field("created", t.created)
                .field("name", t.name)
                .endObject()
            })
            builder.endArray()

            // COMMENTS
            builder.startArray("comments")
            eso.comments.foreach( c => {
              builder.startObject()
                .field("creator", c.creator)
                .field("created", c.created)
                .field("name", c.text)
                .endObject()
            })
            builder.endArray()

            // METADATA
            builder.startObject("metadata")
            for ((k,v) <- eso.metadata) {
              builder.startObject(k)
              convertJsObjectToBuilder(builder, v)
              builder.endObject()
            }
            builder.endObject()
              .endObject()

            val response = x.prepareIndex(index, "clowder_object", eso.resource.id.toString)
              .setSource(builder).execute().actionGet()
          }
          case None => Logger.error("Could not index because Elasticsearch is not connected.")
        }
      }
      case None => Logger.error("No ElasticsearchObject found to index")
    }
  }

  /** Take a JsObject and parse into an XContentBuilder JSON object for Elasticsearch */
  def convertJsObjectToBuilder(builder: XContentBuilder, json: JsObject): XContentBuilder = {
    json.keys.map(k => {
      // Iterate across keys of the JsObject to parse each value as appropriate
      (json \ k) match {
        case v: JsArray => {
          builder.startArray(k)
          v.value.foreach(jv => {
            // Try to interpret numeric value from each String if possible
            parseDouble(jv.toString) match {
              case Some(d) => builder.value(d)
              case None => builder.value(jv.toString)
            }
          })
          builder.endArray()
        }
        case v: JsNumber => builder.field(k, v.value.doubleValue)
        case v: JsString => {
          // Try to interpret numeric value from String if possible
          parseDouble(v.value) match {
            case Some(d) => builder.field(k, d)
            case None => builder.field(k, v.value)
          }
        }
        case v: JsObject => {
          builder.startObject(k)
          convertJsObjectToBuilder(builder, v)
          builder.endObject()
        }
        case v: JsValue => {
          // Try to interpret numeric value from String if possible
          parseDouble(v.toString) match {
            case Some(d) => builder.field(k, d)
            case None => builder.field(k, v.toString)
          }
        }
        case _ => {}
      }
    })
    builder
  }

  /** Take a JsObject and list all unique fields, excepting those in ignoredFields */
  def convertJsMappingToFields(json: JsObject): List[String] = {
    val ignoredFields = List("type", "format", "properties")
    var fields = ListBuffer.empty[String]

    // TODO: capture parent relationships somehow?
    json.keys.map(k => {
      (json \ k) match {
        case v: JsArray => fields.append(k)
        case v: JsNumber => {}
        case v: JsString => fields.append(k)
        case v: JsObject => {
          val subList = convertJsMappingToFields(v)
          if (subList.length > 0)
            fields = fields ++ subList
          if (!(ignoredFields contains k))
            fields.append(k)
        }
        case v: JsValue => fields.append(k)
        case _ => {}
      }
    })
    ignoredFields.foreach(f => {
      val pos = fields.indexOf(f)
      if (pos > -1) fields.remove(pos)
    })
    fields.toList
  }

  /**Attempt to cast String into Double, returning None if not possible**/
  def parseDouble(s: String): Option[Double] = {
    // From http://stackoverflow.com/questions/9542126/scala-is-a-string-parseable-as-a-double
    Try { s.toDouble }.toOption
  }

  override def onStop() {
    client.map(_.close())
    client = None
    Logger.info("ElasticsearchPlugin has stopped")
  }

}
