package services.elasticsearch

import scala.util.Try
import scala.collection.mutable.{ListBuffer, MutableList}
import scala.collection.immutable.List
import scala.concurrent.duration._
import play.api.{Application, Logger, Plugin}
import play.api.Play.current
import play.api.libs.json._
import play.libs.Akka
import play.api.libs.concurrent.Execution.Implicits._
import java.net.InetAddress
import java.util.regex.Pattern
import java.util.HashMap

import akka.actor.Cancellable
import javax.inject.{Inject, Singleton}
import java.util.Date

import org.elasticsearch.common.settings.Settings
import org.elasticsearch.client.transport.NoNodeAvailableException
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest
import org.elasticsearch.action.search.{SearchPhaseExecutionException, SearchResponse, SearchType}
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms.Bucket
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.client.{RequestOptions, RestClient, RestHighLevelClient}
import org.elasticsearch.client.indices.{CreateIndexRequest, GetIndexRequest}
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.common.xcontent.{XContentBuilder, XContentFactory, XContentType}
import org.elasticsearch.index.reindex.{BulkByScrollResponse, ReindexAction, ReindexRequest, ReindexRequestBuilder}
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.apache.http.HttpHost
import api.Permission
import models.{Collection, Dataset, File, QueuedAction, ResourceRef, SearchResult, Section, Tag, TempFile, UUID, User}
import org.elasticsearch.node.Node
import services.{CollectionService, CommentService, DatasetService, FileService, FolderService, MetadataService, QueueService, SearchService}


/**
 * Elasticsearch service.
 *
 */
@Singleton
class ElasticsearchSearchService @Inject() (
                                             comments: CommentService,
                                             files: FileService,
                                             folders: FolderService,
                                             datasets: DatasetService,
                                             collections: CollectionService,
                                             metadatas: MetadataService,
                                             queue: QueueService) extends SearchService {
  var client: Option[RestHighLevelClient] = None
  val nameOfCluster = play.api.Play.configuration.getString("elasticsearchSettings.clusterName").getOrElse("clowder")
  val serverAddress = play.api.Play.configuration.getString("elasticsearchSettings.serverAddress").getOrElse("http://localhost")
  val serverPort = play.api.Play.configuration.getInt("elasticsearchSettings.serverPort").getOrElse(9300)
  val nameOfIndex = play.api.Play.configuration.getString("elasticsearchSettings.indexNamePrefix").getOrElse("clowder")
  val maxResults = play.api.Play.configuration.getInt("elasticsearchSettings.maxResults").getOrElse(240)

  val mustOperators = List("==", "<", ">", ":")
  val mustNotOperators = List("!=")

  // Queue stuff
  val useQueue = play.api.Play.configuration.getBoolean("elasticsearchSettings.useQueue").getOrElse(true)
  override val queueName = "elasticsearch"
  var queueTimer: Cancellable = null


  private def connect(force:Boolean = false): Boolean = {
    if (!force && isEnabled()) {
      listen()
      return true
    }

    try {
      val settings = if (nameOfCluster != "") {
        Settings.builder().put("cluster.name", nameOfCluster).build()
      } else {
        Settings.builder().build()
      }

      // TODO: Need Reindex client
      // .addPlugin(classOf[ReindexPlugin]).
      client = Some(new RestHighLevelClient(RestClient.builder(new HttpHost(serverAddress, serverPort))))

      Logger.debug("--- Elasticsearch Client is being created----")
      client match {
        case Some(c) => {
          if (!c.indices().exists(new GetIndexRequest(nameOfIndex), RequestOptions.DEFAULT))
            createIndex()
          Logger.info("Connected to Elasticsearch")
          listen()
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
      case e: Throwable => {
        Logger.error("Unknown exception connecting to Elasticsearch", e)
        client.map(_.close())
        client = None
        false
      }
    }
  }

  def isEnabled(): Boolean = {
    client match {
      case Some(c) => {
        if (c.cluster().health(new ClusterHealthRequest(nameOfCluster), RequestOptions.DEFAULT).getNumberOfNodes > 0)
          true
        else {
          Logger.debug("Elasticsearch node count is zero; attempting to reconnect")
          connect(true)
        }
      }
      case _ => {
        Logger.debug("No Elasticsearch client found; attempting to connect")
        connect(true)
      }
    }
  }

  def getInformation(): JsObject = {
    Json.obj("server" -> serverAddress,
      "clustername" -> nameOfCluster,
      "queue" -> {
        if (useQueue)
          queue.status(queueName)
        else
          Json.obj("enabled" -> false)
      },
      "status" -> "connected")
  }

  /** Prepare and execute Elasticsearch query, and return list of matching ResourceRefs */
  def search(query: List[JsValue], grouping: String, from: Option[Int], size: Option[Int], user: Option[User]): SearchResult = {
    /** Each item in query list has properties:
     *   "field_key":      full name of field to query, e.g. 'extractors.wordCount.lines'
     *   "operator":       type of query for this term, e.g. '=='
     *   "field_value":    value to search for using specified field & operator
     *   "extractor_key":  name of extractor component only, e.g. 'extractors.wordCount'
     *   "field_leaf_key": name of immediate field only, e.g. 'lines'
     */
    // TODO: Better way to build a URL?
    val source_url = s"/api/search?query=$query&grouping=$grouping"

    val queryObj = prepareElasticJsonQuery(query, grouping)
    accumulatePageResult(queryObj, user, from.getOrElse(0), size.getOrElse(maxResults), source_url)
  }

  /** Search using a simple text string, appending parameters from API to string if provided */
  def search(query: String, resource_type: Option[String], datasetid: Option[String], collectionid: Option[String],
             spaceid: Option[String], folderid: Option[String], field: Option[String], tag: Option[String],
             from: Option[Int], size: Option[Int], permitted: List[UUID], user: Option[User]): SearchResult = {

    var expanded_query = query

    // whether to restrict to a particular metadata field, or search all fields (including tags, name, etc.)
    val mdfield = field match {
      case Some(k) => expanded_query = " "+k+":\""+expanded_query+"\""
      case None => {}
    }

    // Restrict to a particular tag - currently requires exact match
    tag match {
      case Some(t) => expanded_query += " tag:"+t
      case None => {}
    }

    // Restrict to particular resource_type if requested
    resource_type match {
      case Some(restype) => expanded_query += " resource_type:"+restype
      case None => {}
    }

    // Restrict to particular dataset ID (only return files)
    datasetid match {
      case Some(dsid) => expanded_query += " in:"+dsid+" resource_type:file"
      case None => {}
    }

    // Restrict to particular collection ID
    collectionid match {
      case Some(cid) => expanded_query += " in:"+cid
      case None => {}
    }

    spaceid match {
      case Some(spid) => expanded_query += " in:"+spid
      case None => {}
    }

    folderid match {
      case Some(fid) => expanded_query += " in:"+fid
      case None => {}
    }

    // TODO: Better way to build a URL?
    val source_url = s"/api/search?query=$query" +
      (resource_type match {case Some(x) => s"&resource_type=$x" case None => ""}) +
      (datasetid match {case Some(x) => s"&datasetid=$x" case None => ""}) +
      (collectionid match {case Some(x) => s"&collectionid=$x" case None => ""}) +
      (spaceid match {case Some(x) => s"&spaceid=$x" case None => ""}) +
      (folderid match {case Some(x) => s"&folderid=$x" case None => ""}) +
      (field match {case Some(x) => s"&field=$x" case None => ""}) +
      (tag match {case Some(x) => s"&tag=$x" case None => ""})

    val queryObj = prepareElasticJsonQuery(expanded_query.stripPrefix(" "), permitted, user)
    accumulatePageResult(queryObj, user, from.getOrElse(0), size.getOrElse(maxResults), source_url)
  }

  /** Perform search, check permissions, and keep searching again if page isn't filled with permitted resources */
  private def accumulatePageResult(queryObj: SearchSourceBuilder, user: Option[User], from: Int, size: Int, source_url: String): SearchResult = {
    var total_results = ListBuffer.empty[ResourceRef]

    // Fetch initial page & filter by permissions
    val (results, total_size) = _search(queryObj, Some(from), Some(size))
    Logger.debug(s"Found ${results.length} results with ${total_size} total")
    val filtered = checkResultPermissions(results, user)
    Logger.debug(s"Permission to see ${filtered.length} results")
    var scanned_records = size
    var new_from = from + size

    // Make sure page is filled if possible
    filtered.foreach(rr => total_results += rr)
    var exhausted = false
    while (total_results.length < size && !exhausted) {
      Logger.debug(s"Only have ${total_results.length} total results; searching for ${size*2} more from ${new_from}")
      val (results, total_size)  = _search(queryObj, Some(new_from), Some(size*2))
      Logger.debug(s"Found ${results.length} results with ${total_size} total")
      if (results.length == 0 || new_from+results.length == total_size) exhausted = true // No more results to find
      val filtered = checkResultPermissions(results, user)
      Logger.debug(s"Permission to see ${filtered.length} results")
      var still_scanning = true
      results.foreach(r => {
        if (still_scanning) {
          new_from += 1
          scanned_records += 1
        }
        if (filtered.exists(fr => fr==r) && total_results.length < size) {
          total_results += r
        }
        if (total_results.length >= size) {
          // Only increment the records scanned while the page isn't full, to make next 'from' page link correct
          still_scanning = false
        }
      })
    }

    val unpreppedResponse = ElasticsearchResult(total_results.toList,
      from,
      total_results.length,
      scanned_records,
      total_size)

    prepareSearchResponse(unpreppedResponse, source_url, user)
  }

  /** Return a filtered list of resources that user can actually access */
  private def checkResultPermissions(results: List[ResourceRef], user: Option[User]): List[ResourceRef] = {
    var filteredResults = ListBuffer.empty[ResourceRef]

    var filesFound = ListBuffer.empty[UUID]
    var datasetsFound = ListBuffer.empty[UUID]
    var collectionsFound = ListBuffer.empty[UUID]

    // Check permissions for each resource
    results.foreach(resource => {
      resource.resourceType match {
        case ResourceRef.file => if (Permission.checkPermission(user, Permission.ViewFile, resource))
          filesFound += resource.id
        case ResourceRef.dataset => if (Permission.checkPermission(user, Permission.ViewDataset, resource))
          datasetsFound += resource.id
        case ResourceRef.collection => if (Permission.checkPermission(user, Permission.ViewDataset, resource))
          collectionsFound += resource.id
        case _ => {}
      }
    })

    // Reorganize the separate lists back into original Elasticsearch score order
    results.foreach(resource => {
      resource.resourceType match {
        case ResourceRef.file => filesFound.filter(f => f == resource.id).foreach(f => filteredResults += resource)
        case ResourceRef.dataset => datasetsFound.filter(d => d == resource.id).foreach(d => filteredResults += resource)
        case ResourceRef.collection => collectionsFound.filter(c => c == resource.id).foreach(c => filteredResults += resource)
        case _ => {}
      }
    })

    filteredResults.distinct.toList
  }

  /*** Execute query and return list of results and total result count as tuple */
  private def _search(queryObj: SearchSourceBuilder, from: Option[Int] = Some(0), size: Option[Int] = Some(maxResults)): (List[ResourceRef], Long) = {
    connect()
    client match {
      case Some(c) => {
        Logger.info("Searching Elasticsearch: "+queryObj)
        var req = new SearchRequest()
        req = req.indices(nameOfIndex)
          .searchType(SearchType.DFS_QUERY_THEN_FETCH)
          .source(queryObj
            .from(from.getOrElse(0))
            .size(size.getOrElse(maxResults)))

        val response = c.search(req, RequestOptions.DEFAULT)
        Logger.debug("Search hits: " + response.getHits().getTotalHits().value)
        (response.getHits().getHits().map(h => {
          new ResourceRef(Symbol(h.getSourceAsMap().get("resource_type").toString), UUID(h.getId()))
        }).toList, response.getHits().getTotalHits().value)
      }
      case None => {
        Logger.error("Could not call search because Elasticsearch is not connected.")
        (List.empty, 0)
      }
    }
  }

  /** Format a simple search result */
  private def prepareSearchResponse(response: ElasticsearchResult, source_url: String, user: Option[User]): SearchResult = {
    var results = ListBuffer.empty[JsValue]

    // Use bulk Mongo queries to get many resources at once
    val filesList = files.get(Permission.checkPermissions(user, Permission.ViewFile,
      response.results.filter(_.resourceType == 'file)).approved.map(_.id)).found
    val datasetsList = datasets.get(Permission.checkPermissions(user, Permission.ViewDataset,
      response.results.filter(_.resourceType == 'dataset)).approved.map(_.id)).found
    val collectionsList = collections.get(Permission.checkPermissions(user, Permission.ViewCollection,
      response.results.filter(_.resourceType == 'collection)).approved.map(_.id)).found

    // Now reorganize the separate lists back into Elasticsearch score order
    for (resource <- response.results) {
      resource.resourceType match {
        case ResourceRef.file => filesList.filter(_.id == resource.id).foreach(f => results += Json.toJson(f))
        case ResourceRef.dataset => datasetsList.filter(_.id == resource.id).foreach(d => results += Json.toJson(d))
        case ResourceRef.collection => collectionsList.filter(_.id == resource.id).foreach(c => results += Json.toJson(c))
      }
    }

    // TODO: add views etc. other properties for the handlebars template

    addPageURLs(results.distinct.toList, response, source_url)
  }

  /** Provide URLs referring to first/last/next/previous pages of current result set if possible */
  private def addPageURLs(results: List[JsValue], response: ElasticsearchResult, url_root: String): SearchResult = {
    val lead = if (url_root.contains('?')) "&" else "?"

    var first: Option[String] = None
    var last: Option[String] = None
    var prev: Option[String] = None
    var next: Option[String] = None

    // Add pagination fields if necessary
    if (response.from > 0) {
      val prev_idx = List(response.from - response.size, 0).max
      first = Some(url_root + lead + s"from=0&size=${response.size}")
      prev = Some(url_root + lead + s"from=$prev_idx&size=${response.size}")
    }

    if (response.from + response.scanned_size < response.total_size) {
      val next_idx = List[Long](response.from + response.scanned_size, response.total_size).min
      var last_idx = next_idx
      while (last_idx < response.total_size - response.size) {
        last_idx += response.size
      }
      val last_size = response.total_size - last_idx
      last = Some(url_root + lead + s"from=$last_idx&size=${last_size}")
      next = Some(url_root + lead + s"from=$next_idx&size=${response.size}")
    }

    new SearchResult(
      results, response.from, response.results.length, response.size, response.scanned_size, response.total_size,
      first, last, prev, next)
  }

  /** Create a new index with preconfigured mapping */
  def createIndex(index: String = nameOfIndex): Unit = {
    client match {
      case Some(c) => {
        Logger.debug("Index \""+nameOfIndex+"\" does not exist; creating now ---")
        try {
          val request = new CreateIndexRequest(nameOfIndex)
            .settings(Settings.builder()
              .put("analysis.analyzer.default.type", "standard")
              .put("analysis.analyzer.email_analyzer.type", "custom")
              .put("analysis.analyzer.email_analyzer.tokenizer", "uax_url_email")
              .put("index.mapping.ignore_malformed", true)
            )
            .mapping(getElasticsearchObjectMappings(), XContentType.JSON)
          c.indices.create(request, RequestOptions.DEFAULT)
        } catch {
          case e: ElasticsearchException => {
            Logger.debug("Index already exists; skipping creation.")
          }
        }
      }
      case None =>
    }
  }

  def swapIndex() {
    val idx = nameOfIndex + "_reindex_temp_swap"

    client match {
      case Some(c) => {
        // Check if swap index exists before swapping
        if (c.indices.exists(new GetIndexRequest(idx), RequestOptions.DEFAULT)) {
          Logger.debug("Deleting "+nameOfIndex+" index...")
          deleteAll(nameOfIndex)
          createIndex(nameOfIndex)
          Logger.debug("Replacing with "+idx+"...")

          val request = new ReindexRequest()
          request.setSourceIndices(idx)
          request.setDestIndex(nameOfIndex)
          c.reindex(request, RequestOptions.DEFAULT);

          //ReindexAction.INSTANCE.newRequestBuilder(c).source(idx).destination(nameOfIndex).get()
          Logger.debug("Deleting "+idx+" index...")
          deleteAll(idx)
        } else {
          Logger.error("Elasticsearch index "+idx+" does not exist for swap.")
        }
      }
      case None =>
    }
  }

  /** Delete all documents in an index */
  def deleteAll(idx: String = nameOfIndex) {
    connect()
    client match {
      case Some(c) => {
        try {
          val request = new DeleteIndexRequest(idx)
          val response = c.indices.delete(request, RequestOptions.DEFAULT)
          if (!response.isAcknowledged())
            Logger.error("Did not delete all data from elasticsearch.")
        } catch {
          case e: ElasticsearchException => {
            Logger.error("Could not call search.", e)
            client = None
          }
        }
      }
      case None => Logger.error("Could not call index because we are not connected.")
    }
  }

  /** Delete an index */
  def delete(id: String, docType: String = "clowder_object") {
    connect()
    client match {
      case Some(c) => {
        try {
          val request = new DeleteRequest(nameOfIndex).id(id)
          val response = c.delete(request, RequestOptions.DEFAULT)
          Logger.debug("Deleting document: " + response.getId)
        } catch {
          case e: ElasticsearchException => {
            Logger.error("Could not call search.", e)
            client = None
          }
        }
      }
      case None => Logger.error("Could not call index because we are not connected.")
    }

  }

  /** Traverse metadata field mappings to get unique list for autocomplete */
  def getAutocompleteMetadataFields(query: String): List[String] = {
    connect()

    var listOfTerms = ListBuffer.empty[String]
    client match {
      case Some(c) => {
        if (query != "") {
          val response = c.indices().getMapping(new GetMappingsRequest(), RequestOptions.DEFAULT)
          val maps = response.getMappings().get(nameOfIndex).get("clowder_object")
          val resultList = convertJsMappingToFields(Json.parse(maps.source().toString).as[JsObject], None, Some("metadata"))

          resultList.foreach(term => {
            val leafKey = term.split('.').last
            if ((leafKey.toLowerCase startsWith query.toLowerCase) && !(listOfTerms contains term))
              listOfTerms += term
          })
        }
      }
      case None => Logger.error("Could not get metadata autocomplete suggestions; Elasticsearch is not connected.")
    }

    listOfTerms.toList
  }

  /**
   * Reindex using a resource reference - will send to queue if enabled
   */
  def index(resource: ResourceRef, recursive: Boolean = true, idx: Option[String]) = {
    resource.resourceType match {
      case 'file => {
        files.get(resource.id) match {
          case Some(f) => {
            if (useQueue)
              queue.queue("index_file", resource, queueName)
            else
              index(f, None)
          }
          case None => Logger.error(s"File ID not found: ${resource.id.stringify}")
        }
      }
      case 'dataset => {
        datasets.get(resource.id) match {
          case Some(ds) => {
            if (useQueue)
              queue.queue("index_dataset", resource, queueName)
            else
              index(ds, recursive, None)
          }
          case None => Logger.error(s"Dataset ID not found: ${resource.id.stringify}")
        }
      }
      case 'collection => {
        collections.get(resource.id) match {
          case Some(c) => {
            if (useQueue)
              queue.queue("index_collection", resource, queueName)
            else
              index(c, recursive, None)
          }
          case None => Logger.error(s"Collection ID not found: ${resource.id.stringify}")
        }
      }
    }
  }

  /**
   * Reindex the given collection, if recursive is set to true it will
   * also reindex all datasets and files.
   */
  def index(collection: Collection, recursive: Boolean, idx: Option[String]) {
    connect()
    // Perform recursion first if necessary
    if (recursive) {
      for (dataset <- datasets.listCollection(collection.id.toString)) {
        index(dataset, recursive, idx)
      }
    }
    _index(getElasticsearchObject(collection), idx)
  }

  /**
   * Reindex the given dataset, if recursive is set to true it will
   * also reindex all files.
   */
  def index(dataset: Dataset, recursive: Boolean, idx: Option[String]) {
    connect()
    // Perform recursion first if necessary
    if (recursive) {
      files.get(dataset.files).found.foreach(f => index(f, idx))
      for (f <- folders.findByParentDatasetId(dataset.id))
        files.get(f.files).found.foreach(fi => index(fi, idx))
    }
    _index(getElasticsearchObject(dataset), idx)
  }

  /** Reindex the given file. */
  def index(file: File, idx: Option[String]) {
    connect()
    // Index sections first so they register for tag counts
    for (section <- file.sections) {
      index(section, idx)
    }
    _index(getElasticsearchObject(file), idx)
  }

  def index(file: TempFile, idx: Option[String]): Unit = {
    connect()
    _index(getElasticsearchObject(file), idx)
  }

  def index(section: Section, idx: Option[String]) {
    connect()
    _index(getElasticsearchObject(section), idx)
  }

  def indexAll(): String = {
    // Add all individual entries to the queue and delete this action
    val idx = nameOfIndex + "_reindex_temp_swap"
    Logger.debug("Reindexing database into temporary reindex file: "+idx)
    createIndex(idx)
    // queue everything for each resource type
    collections.indexAll(Some(idx))
    datasets.indexAll(Some(idx))
    files.indexAll(Some(idx))

    // queue action to swap index once we're done reindexing
    queue.queue("index_swap", queueName)
    "Reindexing operation added to queue."
  }

  /** Index document using an arbitrary map of fields. */
  private def _index(esObj: Option[ElasticsearchObject], idx: Option[String]) {
    esObj match {
      case Some(eso) => {
        connect()
        client match {
          case Some(c) => {
            val builder = XContentFactory.jsonBuilder()
              .startObject()
              // BASIC INFO
              .field("creator", eso.creator)
              .field("creator_name", eso.creator_name)
              .field("creator_email", eso.creator_email)
              .field("created", eso.created)
              .field("created_as", eso.created_as)
              .field("resource_type", eso.resource.resourceType.name)
              .field("name", eso.name)
              .field("description", eso.description)

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
              builder.value(t)
            })
            builder.endArray()

            // COMMENTS
            builder.startArray("comments")
            eso.comments.foreach( c => {
              builder.value(c)
            })
            builder.endArray()

            // METADATA
            builder.startObject("metadata")
            for ((k,v) <- eso.metadata) {
              // Elasticsearch 2 does not allow periods in field names
              val clean_k = k.replace(".", "_")
              v match {
                case jv: JsObject => {
                  builder.startObject(clean_k)
                  convertJsObjectToBuilder(builder, jv)
                  builder.endObject()
                }
                case jv: JsArray => {
                  builder.startArray(clean_k)
                  jv.value.foreach(subv => {
                    builder.value(subv.toString.replace("\"",""))
                  })
                  builder.endArray()
                }
                case _ => {}
              }
            }
            builder.endObject()
              .endObject()


            c.index(new IndexRequest(idx.getOrElse(nameOfIndex)).id(eso.resource.id.toString).source(builder), RequestOptions.DEFAULT)
          }
          case None => Logger.error("Could not index because Elasticsearch is not connected.")
        }
      }
      case None => Logger.error("No ElasticsearchObject found to index")
    }
  }

  /** Return map of distinct value/count for tags **/
  def listTags(resourceType: String = ""): Map[String, Long] = {
    val results = scala.collection.mutable.Map[String, Long]()

    connect()
    client match {
      case Some(x) => {

        var bldr = new SearchSourceBuilder()
        val agg = AggregationBuilders.count("by_tag").field("tags")
        bldr = bldr.aggregation(agg).size(0)
        // Filter to tags on a particular type of resource if given
        if (resourceType != "")
          bldr = bldr.query(prepareElasticJsonQuery("resource_type:"+resourceType+"", List.empty, None).query())
        else {
          // Exclude Section tags to avoid double-counting since those are duplicated in File document
          bldr = bldr.query(prepareElasticJsonQuery("resource_type:file|dataset|collection", List.empty, None).query())
        }
        val req = new SearchRequest(nameOfIndex).source(bldr)
        val response = x.search(req, RequestOptions.DEFAULT)

        // Extract value/counts from Aggregation object
        val aggr = response.getAggregations
          .get[org.elasticsearch.search.aggregations.bucket.terms.StringTerms]("by_tag")
        aggr.getBuckets().toArray().foreach(bucket => {
          val term = bucket.asInstanceOf[Bucket].getKey.toString
          val count = bucket.asInstanceOf[Bucket].getDocCount
          if (!term.isEmpty) {
            results.update(term, count)
          }
        })
        results.toMap
      }
      case None => {
        Logger.error("Could not call search because we are not connected.")
        Map[String, Long]()
      }
    }
  }

  /** Take a JsObject and parse into an XContentBuilder JSON object for indexing into Elasticsearch */
  private def convertJsObjectToBuilder(builder: XContentBuilder, json: JsObject): XContentBuilder = {
    json.keys.map(k => {
      // Iterate across keys of the JsObject to parse each value as appropriate
      (json \ k) match {
        case v: JsArray => {
          // Elasticsearch 2 does not allow periods in field names
          builder.startArray(k.toString.replace(".", "_"))
          v.value.foreach(jv => {
            // Try to interpret numeric value from each String if possible
            parseDouble(jv.toString) match {
              case Some(d) => builder.value(d)
              case None => builder.value(jv)
            }
          })
          builder.endArray()
        }
        case v: JsNumber => builder.field(k, v.value.doubleValue)
        case v: JsString => {
          // Try to interpret numeric value from String if possible
          parseDouble(v.value) match {
            case Some(d) => builder.field(k, d)
            case None => {
              // Elasticsearch 2 does not allow periods in field names
              builder.field(k.replace(".", "_"), v.value.replace("\"",""))
            }
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
            case None => {
              // Elasticsearch 2 does not allow periods in field names
              builder.field(k.replace(".", "_"), v.toString.replace("\"",""))
            }
          }
        }
        case _ => {}
      }
    })
    builder
  }

  /** Take a JsObject and list all unique fields under targetObject field, except those in ignoredFields */
  private def convertJsMappingToFields(json: JsObject, parentKey: Option[String] = None,
                                       targetObject: Option[String] = None, foundTarget: Boolean = false): List[String] = {

    var fields = ListBuffer.empty[String]
    // TODO: These are Elasticsearch mapping-internal fields but might also appear in metadata...
    val ignoredFields = List("type", "format", "properties")

    // If targetObject given, only list fields from that level down
    var foundTargetObjectRootLevel = false
    var foundTargetObject = targetObject match {
      case Some(targ) => foundTarget
      case None => true
    }

    json.keys.map(k => {
      // Check whether to start collecting fields yet
      if (!foundTargetObject) {
        targetObject match {
          case Some(targ) => if (k == targ) {
            foundTargetObject = true
            foundTargetObjectRootLevel = true
          }
          case None => {}
        }
      }

      // Get fully qualified field path, with dot-notation (only generated inside metadata fields)
      var longKey = (parentKey match {
        case Some(pk) => {
          if (pk contains "metadata")
            pk+'.'+k
          else k
        }
        case None => k
      })

      // Remove ignored fields
      ignoredFields.foreach(ig => {
        if (longKey.indexOf("."+ig+".") > -1)
          longKey = longKey.replace("."+ig+".", ".")
        if (longKey.endsWith("."+ig))
          longKey = longKey.stripSuffix("."+ig)
      }
      )

      // Process value of field depending on type
      val okToAppend = (!(ignoredFields contains k) && foundTargetObject)
      (json \ k) match {
        case v: JsArray => if (okToAppend) fields.append(longKey)
        case v: JsNumber => {}
        case v: JsString => if (okToAppend) fields.append(longKey)
        case v: JsObject => {
          // For objects, recursively get keys from sub-objects
          val subList = convertJsMappingToFields(v, Some(longKey), targetObject, foundTargetObject)
          if (subList.length > 0)
            fields = fields ++ subList
          else if (okToAppend) fields.append(longKey)
        }
        case v: JsValue => if (okToAppend) fields.append(longKey)
        case _ => {}
      }

      // Finally, stop capturing fields if we are done at the target object root level
      if (foundTargetObjectRootLevel) {
        val rootCheck = fields.indexOf(targetObject.get)
        if (rootCheck > -1) fields.remove(rootCheck)
        foundTargetObject = false
      }

    })

    fields.toList.distinct
  }

  /** Return string-encoded JSON object describing field types */
  private def getElasticsearchObjectMappings(): String = {
    /** The dynamic template will restrict all dynamic metadata fields to be indexed
     * as strings for datatypes besides Objects. In the future, this could
     * be removed, but only once the Search API better supports those data types (e.g. Date).
     */
     """{
      |"date_detection": false,
      |"properties": {
        |"name": {"type": "text", "copy_to": "_all"},
        |"description": {"type": "text", "copy_to": "_all"},
        |"resource_type": {"type": "keyword"},
        |"child_of": {"type": "keyword"},
        |"parent_of": {"type": "keyword"},
        |"creator": {"type": "text"},
        |"creator_name": {"type": "string"},
        |"creator_email": {"type": "string", "search_analyzer": "email_analyzer", "analyzer": "email_analyzer"},
        |"created_as": {"type": "text", "copy_to": "_all"},
        |"created": {"type": "date", "format": "dateOptionalTime"},
        |"metadata": {"type": "object"},
        |"comments": {"type": "text"},
        |"tags": {"type": "keyword", "copy_to": "_all"},
        |"_all": {"type": "text"}
      |}
    |}""".stripMargin
  }

  /**Attempt to cast String into Double, returning None if not possible**/
  private def parseDouble(s: String): Option[Double] = {
    Try { s.toDouble }.toOption
  }

  /**Convert list of search term JsValues into an Elasticsearch-ready JSON query object**/
  private def prepareElasticJsonQuery(query: List[JsValue], grouping: String): SearchSourceBuilder = {
    /** OPERATORS
     *  :   contains (partial match)
     *  ==  equals (exact match)
     *  !=  not equals (partial matches OK)
     *  <   less than
     *  >   greater than
     **/

    // Separate query terms into two groups based on operator - must know ahead of time how many of each we have
    val mustList = ListBuffer.empty[JsValue]
    val mustNotList = ListBuffer.empty[JsValue]
    query.foreach(jv => {
      val operator = (jv \ "operator").toString.replace("\"","")
      if (mustOperators.contains(operator))
        mustList.append(jv)
      else if (mustNotOperators.contains(operator))
        mustNotList.append(jv)
    })

    // 1) Wrap entire object in BOOL query - everything within must match
    var bool_clause = QueryBuilders.boolQuery()

    // 2) populate the MUST/SHOULD portion
    if (mustList.length > 0) {
      mustList.foreach(jv => {
        val key = (jv \ "field_key").toString.replace("\"","")
        val operator = (jv \ "operator").toString.replace("\"", "")
        val value = (jv \ "field_value").toString.replace("\"", "")

        val q = operator match {
          case "==" => QueryBuilders.matchPhraseQuery(key, value)
          case "<" => QueryBuilders.rangeQuery(key).lt(value)
          case ">" => QueryBuilders.rangeQuery(key).gt(value)
          case ":" => {
            if (key == "all")
              QueryBuilders.regexpQuery("_all", wrapRegex(value))
            else
              QueryBuilders.queryStringQuery(value).field(key)
          }
        }

        bool_clause = grouping match {
          case "AND" => bool_clause.must(q)
          case "OR" => bool_clause.should(q)
        }
      })
    }

    // 3) populate the MUST_NOT portion
    if (mustNotList.length > 0) {
      mustNotList.foreach(jv => {
        val key = (jv \ "field_key").toString.replace("\"","")
        val operator = (jv \ "operator").toString.replace("\"", "")
        val value = (jv \ "field_value").toString.replace("\"", "")

        bool_clause = operator match {
          case "!=" => bool_clause.mustNot(QueryBuilders.matchPhraseQuery(key, value))
        }
      })
    }

    // Close the bool/query objects and return
    new SearchSourceBuilder().query(bool_clause)
  }

  /**Convert search string into an Elasticsearch-ready JSON query object**/
  private def prepareElasticJsonQuery(query: String, permitted: List[UUID], user: Option[User]): SearchSourceBuilder = {
    /** OPERATORS
     *  ==  equals (exact match)
     *  !=  not equals (partial matches OK)
     *  <   less than
     *  >   greater than
     **/

    // Use regex to split string into a list, preserving quoted phrases as single value
    val matches = ListBuffer[String]()
    val m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(query.replace(":", " "))
    while (m.find()) {
      matches += m.group(1).replace("\"", "").replace("__", " ")
    }

    // If a term is specified that isn't in this list, it's assumed to be a metadata field
    val official_terms = List("name", "creator", "email", "resource_type", "in", "contains", "tag")

    // Create list of "key:value" terms for parsing by builder
    val terms = ListBuffer[String]()
    var currterm = ""
    matches.foreach(mt => {
      // Determine if the string was a key or value
      if (query.contains(mt+":") || query.contains("\""+mt+"\":")) {
        // Do some user-friendly replacement
        if (mt == "tag")
          currterm += "tags:"
        else if (mt == "in")
          currterm += "child_of:"
        else if (mt == "contains")
          currterm += "parent_of:"
        else if (mt == "creator")
          currterm += "creator_name:"
        else if (mt == "email")
          currterm += "creator_email:"
        else if (!official_terms.contains(mt))
          currterm += "metadata."+mt+":"
        else
          currterm += mt+":"
      } else if (query.contains(":"+mt) || query.contains(":\""+mt+"\"")) {
        currterm += mt.toLowerCase()
        terms += currterm
        currterm = ""
      } else {
        terms += "_all:"+mt.toLowerCase()
      }
    })

    var bool_clause = QueryBuilders.boolQuery()

    // First, populate the MUST portion of Bool query
    terms.map(term => {
      for (operator <- mustOperators) {
        if (term.contains(operator)) {
          val key = term.substring(0, term.indexOf(operator))
          val value = term.substring(term.indexOf(operator)+1, term.length)

          bool_clause = operator match {
            case "==" => bool_clause.must(QueryBuilders.matchPhraseQuery(key, value))
            case "<"  => bool_clause.must(QueryBuilders.rangeQuery(key).lt(value))
            case ">"  => bool_clause.must(QueryBuilders.rangeQuery(key).gt(value))
            case ":" => {
              if (key == "all")
                bool_clause.must(QueryBuilders.regexpQuery("_all", wrapRegex(value)))
              else
                bool_clause.must(QueryBuilders.queryStringQuery(value).field(key))
            }
          }
        }
      }
    })

    // If user is superadmin or there is no user, no filters applied
    user match {
      case Some(u) => {
        if (!u.superAdminMode) {
          permitted.foreach(ps => {
            bool_clause = bool_clause.should(QueryBuilders.matchQuery("child_of", ps.stringify))
          })
        }
      }
      case None => {
        // Calling this with no user should only happen internally (e.g. listTags) so no filter
      }
    }

    // Second, populate the MUST NOT portion of Bool query
    terms.map(term => {
      for (operator <- mustNotOperators) {
        if (term.contains(operator)) {
          val key = term.substring(0, term.indexOf(operator))
          val value = term.substring(term.indexOf(operator), term.length)

          bool_clause = operator match {
            case "!=" => bool_clause.mustNot(QueryBuilders.matchPhraseQuery(key, value))
          }
        }
      }
    })

    // Close the bool/query objects and return
    new SearchSourceBuilder().query(bool_clause)
  }

  private def wrapRegex(value: String, query_string: Boolean = false): String = {
    /**
     * Given a search string, prepare it to get substring results from regex.
     *
     */

    var beg = ""
    var end = ""

    if (query_string) {
      beg = "/"
      end = "/"
    }

    if (!value.startsWith("^"))
      beg += ".*"
    if (!value.endsWith("$"))
      end = ".*" + end

    return beg + value.stripPrefix("^").stripSuffix("$") + end
  }

  /**Convert File to ElasticsearchObject and return, fetching metadata as necessary**/
  private def getElasticsearchObject(f: File): Option[ElasticsearchObject] = {
    val id = f.id

    // Get child_of relationships for File
    var child_of: ListBuffer[String] = ListBuffer()
    // ...first, the dataset which contains the file or its folder
    datasets.findByFileIdAllContain(id).map(ds => {
      child_of += ds.id.toString
      ds.spaces.map(spid => child_of += spid.toString)
      ds.collections.map(collid => child_of += collid.toString)
    })
    // ...second, the immediate parent folder ID (and the folder's parent) itself
    folders.findByFileId(id).map(fld => {
      child_of += fld.id.toString
      child_of += fld.parentDatasetId.toString
    })
    val child_of_distinct = child_of.toList.distinct

    // Get tags for file and its sections
    var ftags: ListBuffer[String] = ListBuffer()
    f.tags.foreach(t =>
      ftags += t.name
    )
    f.sections.foreach(sect => {
      sect.tags.foreach(sect_tag =>
        ftags += sect_tag.name
      )
    })

    // Get comments for file
    val fcomments = for (c <- comments.findCommentsByFileId(id)) yield {
      c.text
    }

    // Get metadata for File
    var metadata = Map[String, JsValue]()
    for (md <- metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.file, id))) {
      val creator = md.creator.displayName

      // If USER metadata, ignore the name and set the Metadata Definition field to the creator
      if (md.creator.typeOfAgent=="cat:user") {
        val subjson = md.content.as[JsObject]
        subjson.keys.foreach(subkey => {
          // If we already have some metadata from this creator, merge the results; otherwise, create new entry
          if (metadata.keySet.exists(_ == subkey)) {
            metadata += (subkey -> metadata(subkey).as[JsArray].append((subjson \ subkey)))
          }
          else {
            metadata += (subkey -> Json.arr((subjson \ subkey)))
          }
        })
      } else if (md.creator.typeOfAgent=="user") {
        // Override the creator if this is non-UI user-submitted metadata and group the objects together
        val creator = "user-submitted"
        if (metadata.keySet.exists(_ == creator))
          metadata += (creator -> (metadata(creator).as[JsObject] ++ (md.content.as[JsObject])))
        else
          metadata += (creator -> md.content.as[JsObject])
      }
      else {
        // If we already have some metadata from this creator, merge the results; otherwise, create new entry
        if (metadata.keySet.exists(_ == creator))
          metadata += (creator -> (metadata(creator).as[JsObject] ++ (md.content.as[JsObject])))
        else
          metadata += (creator -> md.content.as[JsObject])
      }
    }

    Some(new ElasticsearchObject(
      ResourceRef(ResourceRef.file, id),
      f.filename,
      f.author.id.toString,
      f.author.fullName.toString,
      f.author.email.getOrElse("").toString,
      f.uploadDate,
      f.originalname,
      List.empty,
      child_of_distinct,
      f.description,
      ftags.toList,
      fcomments,
      metadata
    ))
  }

  /**Convert Dataset to ElasticsearchObject and return, fetching metadata as necessary**/
  private def getElasticsearchObject(ds: Dataset): Option[ElasticsearchObject] = {
    val id = ds.id

    // Get parent collections and spaces
    var child_of: ListBuffer[String] = ListBuffer()
    ds.collections.map(collId => child_of += collId.toString)
    ds.spaces.map(spid => child_of += spid.toString)
    val child_of_distinct = child_of.toList.distinct

    // Get child files & folders
    var parent_of: ListBuffer[String] = ListBuffer()
    ds.files.map(fileId => parent_of += fileId.toString)
    ds.folders.map(folderId => parent_of += folderId.toString)
    val parent_of_distinct = parent_of.toList.distinct

    // Get comments for dataset
    val dscomments = for (c <- comments.findCommentsByDatasetId(id)) yield {
      c.text
    }

    // Get metadata for Dataset
    var metadata = Map[String, JsValue]()
    for (md <- metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, id))) {

      val creator = md.creator.displayName

      // If USER metadata, ignore the name and set the Metadata Definition field to the creator
      if (md.creator.typeOfAgent=="cat:user") {
        val subjson = md.content.as[JsObject]
        subjson.keys.foreach(subkey => {
          // If we already have some metadata from this creator, merge the results; otherwise, create new entry
          if (metadata.keySet.exists(_ == subkey)) {
            metadata += (subkey -> metadata(subkey).as[JsArray].append((subjson \ subkey)))
          }
          else {
            metadata += (subkey -> Json.arr((subjson \ subkey)))
          }
        })
      } else {
        // If we already have some metadata from this creator, merge the results; otherwise, create new entry
        if (metadata.keySet.exists(_ == creator))
        // Merge must check for JsObject or JsArray separately - they cannot be merged or converted to JsValue directly
          try {
            metadata += (creator -> (metadata(creator).as[JsObject] ++ (md.content.as[JsObject])))
          } catch {
            case _ => {
              metadata += (creator -> (metadata(creator).as[JsArray] ++ (md.content.as[JsArray])))
            }
          }
        else
        // However for first entry JsValue is OK - will be converted to Object or Array for later merge if needed
          metadata += (creator -> md.content.as[JsValue])
      }
    }

    Some(new ElasticsearchObject(
      ResourceRef(ResourceRef.dataset, id),
      ds.name,
      ds.author.id.toString,
      ds.author.fullName.toString,
      ds.author.email.getOrElse("").toString,
      ds.created,
      "",
      parent_of_distinct,
      child_of_distinct,
      ds.description,
      ds.tags.map( (t:Tag) => t.name ),
      dscomments,
      metadata
    ))
  }

  /**Convert Collection to ElasticsearchObject and return, fetching metadata as necessary**/
  private def getElasticsearchObject(c: Collection): Option[ElasticsearchObject] = {
    // Get parent_of relationships for Collection
    // TODO: Re-enable after listCollection implements Iterator; crashes on large databases otherwise
    //var parent_of = datasets.listCollection(c.id.toString).map( ds => ds.id.toString )
    var parent_of = c.child_collection_ids.map( cc_id => cc_id.toString)

    // Get child relationships
    var child_of: ListBuffer[String] = ListBuffer()
    c.parent_collection_ids.map( pc_id => child_of += pc_id.toString)
    c.spaces.map( spid => child_of += spid.toString)
    val child_of_distinct = child_of.toList.distinct

    Some(new ElasticsearchObject(
      ResourceRef(ResourceRef.collection, c.id),
      c.name,
      c.author.id.toString,
      c.author.fullName.toString,
      c.author.email.getOrElse("").toString,
      c.created,
      "",
      parent_of,
      child_of_distinct,
      c.description,
      List.empty,
      List.empty,
      Map()
    ))
  }

  /**Convert Section to ElasticsearchObject and return**/
  private def getElasticsearchObject(s: Section): Option[ElasticsearchObject] = {
    val id = s.id

    // For Section, child_of will be a one-item list containing parent file ID
    val child_of = List(s.id.toString)

    // Get metadata for Section
    var metadata = Map[String, JsValue]()
    for (md <- metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.section, id))) {
      val creator = md.creator.displayName

      // If USER metadata, ignore the name and set the Metadata Definition field to the creator
      if (md.creator.typeOfAgent=="cat:user") {
        val subjson = md.content.as[JsObject]
        subjson.keys.foreach(subkey => {
          // If we already have some metadata from this creator, merge the results; otherwise, create new entry
          if (metadata.keySet.exists(_ == subkey)) {
            metadata += (subkey -> metadata(subkey).as[JsArray].append((subjson \ subkey)))
          }
          else {
            metadata += (subkey -> Json.arr((subjson \ subkey)))
          }
        })
      } else if (md.creator.typeOfAgent=="user") {
        // Override the creator if this is non-UI user-submitted metadata and group the objects together
        val creator = "user-submitted"
        if (metadata.keySet.exists(_ == creator))
          metadata += (creator -> (metadata(creator).as[JsObject] ++ (md.content.as[JsObject])))
        else
          metadata += (creator -> md.content.as[JsObject])
      }
      else {
        // If we already have some metadata from this creator, merge the results; otherwise, create new entry
        if (metadata.keySet.exists(_ == creator))
          metadata += (creator -> (metadata(creator).as[JsObject] ++ (md.content.as[JsObject])))
        else
          metadata += (creator -> md.content.as[JsObject])
      }
    }

    Some(new ElasticsearchObject(
      ResourceRef(ResourceRef.section, id),
      "section-"+id.toString,
      "",
      "",
      "",
      new Date,
      "",
      List.empty,
      child_of,
      s.description.getOrElse(""),
      s.tags.map( (t:Tag) => t.name ),
      List.empty,
      metadata
    ))
  }

  /**Convert TempFile to ElasticsearchObject and return, fetching metadata as necessary**/
  private def getElasticsearchObject(file: TempFile): Option[ElasticsearchObject] = {
    Some(new ElasticsearchObject(
      ResourceRef(ResourceRef.file, file.id),
      file.filename,
      "",
      "",
      "",
      file.uploadDate,
      "",
      List.empty,
      List.empty,
      "",
      List.empty,
      List.empty,
      Map()
    ))
  }

  // start pool to being processing queue actions
  private def listen() = {
    if (queueTimer == null) {
      Logger.info("Now listening for queued actions in ElasticearchSearchService.")
      // TODO: Need to make these in a separate pool
      queueTimer = Akka.system().scheduler.schedule(0 seconds, 5 millis) {
        queue.getNextQueuedAction(queueName) match {
          case Some(qa) => {
            try {
              Logger.info("Handling queued action!")
              handleQueuedAction(qa)
              queue.removeQueuedAction(qa, queueName)
            }
            catch {
              case except: Throwable => {
                Logger.error(s"Error handling ${qa.action}: ${except}")
                queue.removeQueuedAction(qa, queueName)
              }
            }

          }
          case None => {}
        }
      }
    }
  }

  // process the next entry in the queue
  private def handleQueuedAction(action: QueuedAction) = {
    val recursive = action.elastic_parameters.fold(false)(_.recursive)
    val idx: Option[String] = action.elastic_parameters.fold[Option[String]](None)(_.index)

    action.target match {
      case Some(targ) => {
        action.action match {
          case "index_file" => {
            val target = files.get(targ.id) match {
              case Some(f) => index(f, idx)
              case None => throw new NullPointerException(s"File ${targ.id.stringify} no longer found for indexing")
            }
          }
          case "index_dataset" => {
            val target = datasets.get(targ.id) match {
              case Some(ds) => index(ds, recursive, idx)
              case None => throw new NullPointerException(s"Dataset ${targ.id.stringify} no longer found for indexing")
            }
          }
          case "index_collection" => {
            val target = collections.get(targ.id) match {
              case Some(c) => index(c, recursive, idx)
              case None => throw new NullPointerException(s"Collection ${targ.id.stringify} no longer found for indexing")
            }
          }
          case "index_all" => indexAll()
          case "index_swap" => swapIndex()
          case _ => throw new IllegalArgumentException(s"Unrecognized action: ${action.action}")
        }
      }
      case None => {
        action.action match {
          case "index_file" => throw new IllegalArgumentException(s"No target specified for action ${action.action}")
          case "index_dataset" => throw new IllegalArgumentException(s"No target specified for action ${action.action}")
          case "index_collection" => throw new IllegalArgumentException(s"No target specified for action ${action.action}")
          case "index_all" => indexAll()
          case "index_swap" => swapIndex()
          case _ => throw new IllegalArgumentException(s"Unrecognized action: ${action.action}")
        }
      }
    }
  }

}

// Object that is indexed in Elasticsearch, converted from a resource object
case class ElasticsearchObject (resource: ResourceRef,
                                name: String,
                                creator: String,
                                creator_name: String,
                                creator_email: String,
                                created: Date,
                                created_as: String = "",
                                parent_of: List[String] = List.empty,
                                child_of: List[String] = List.empty,
                                description: String,
                                tags: List[String] = List.empty,
                                comments: List[String] = List.empty,
                                metadata: Map[String, JsValue] = Map())

object ElasticsearchObject {
  /**
   * Serializer for ElasticsearchObject
   */
  implicit object ElasticsearchWrites extends Writes[ElasticsearchObject] {
    def writes(eso: ElasticsearchObject): JsValue = Json.obj(
      "resource" -> JsString(eso.resource.toString),
      "name" -> JsString(eso.name),
      "creator" -> JsString(eso.creator),
      "creator_name" -> JsString(eso.creator_name),
      "creator_email" -> JsString(eso.creator_email),
      "created" -> JsString(eso.created.toString),
      "created_as" -> JsString(eso.created_as.toString),
      "parent_of" -> JsArray(eso.parent_of.toSeq.map( (p:String) => Json.toJson(p)): Seq[JsValue]),
      "child_of" -> JsArray(eso.child_of.toSeq.map( (c:String) => Json.toJson(c)): Seq[JsValue]),
      "description" -> JsString(eso.description),
      "tags" -> JsArray(eso.tags.toSeq.map( (t:String) => Json.toJson(t)): Seq[JsValue]),
      "comments" -> JsArray(eso.comments.toSeq.map( (c:String) => Json.toJson(c)): Seq[JsValue]),
      "metadata" -> JsArray(eso.metadata.toSeq.map(
        (m:(String,JsValue)) => new JsObject(Seq(m._1 -> m._2)) )
      )
    )
  }

  /**
   * Deserializer for ElasticsearchObject
   */
  implicit object ElasticsearchReads extends Reads[ElasticsearchObject] {
    def reads(json: JsValue): JsResult[ElasticsearchObject] = JsSuccess(new ElasticsearchObject(
      (json \ "resource").as[ResourceRef],
      (json \ "name").as[String],
      (json \ "creator").as[String],
      (json \ "creator_name").as[String],
      (json \ "creator_email").as[String],
      (json \ "created").as[Date],
      (json \ "created_as").as[String],
      (json \ "parent_of").as[List[String]],
      (json \ "child_of").as[List[String]],
      (json \ "description").as[String],
      (json \ "tags").as[List[String]],
      (json \ "comments").as[List[String]],
      (json \ "metadata").as[Map[String, JsValue]]
    ))
  }
}

case class ElasticsearchResult (results: List[ResourceRef],
                                from: Int = 0,           // Starting index of results
                                size: Int = 240,         // Requested page size of query
                                scanned_size: Int = 240, // Number of records scanned to fill 'size' results after permission check
                                total_size: Long = 0)    // Number of records across all pages