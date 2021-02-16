package services

import api.Permission
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms.Bucket
import play.api.libs.json.Json._

import scala.util.Try
import scala.collection.mutable.{ListBuffer, MutableList}
import scala.collection.immutable.List
import play.api.{Application, Logger, Plugin}
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import java.net.InetAddress
import java.util.regex.Pattern

import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.action.search.{SearchPhaseExecutionException, SearchResponse, SearchType}
import org.elasticsearch.client.transport.NoNodeAvailableException
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.indices.IndexAlreadyExistsException
import org.elasticsearch.index.reindex.{ReindexAction, ReindexPlugin, ReindexRequestBuilder}
import models.{Collection, Dataset, ElasticsearchResult, File, Folder, ResourceRef, Section, UUID, User}
import play.api.Play.current
import play.api.libs.json._
import _root_.util.SearchUtils
import org.apache.commons.lang.StringUtils
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest


/**
 * Elasticsearch plugin.
 *
 */
class ElasticsearchPlugin(application: Application) extends Plugin {
  val comments: CommentService = DI.injector.getInstance(classOf[CommentService])
  val files: FileService = DI.injector.getInstance(classOf[FileService])
  val folders: FolderService = DI.injector.getInstance(classOf[FolderService])
  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])
  val spaces: SpaceService = DI.injector.getInstance(classOf[SpaceService])
  val queue: ElasticsearchQueue = DI.injector.getInstance(classOf[ElasticsearchQueue])
  var client: Option[TransportClient] = None
  val nameOfCluster = play.api.Play.configuration.getString("elasticsearchSettings.clusterName").getOrElse("clowder")
  val serverAddress = play.api.Play.configuration.getString("elasticsearchSettings.serverAddress").getOrElse("localhost")
  val serverPort = play.api.Play.configuration.getInt("elasticsearchSettings.serverPort").getOrElse(9300)
  val nameOfIndex = play.api.Play.configuration.getString("elasticsearchSettings.indexNamePrefix").getOrElse("clowder")
  val maxResults = play.api.Play.configuration.getInt("elasticsearchSettings.maxResults").getOrElse(240)

  val mustOperators = List("==", "<=", ">=", "<", ">", ":")
  val mustNotOperators = List("!=")


  override def onStart() {
    Logger.debug("ElasticsearchPlugin started but not yet connected")
    connect()
    queue.listen()
  }

  def connect(force:Boolean = false): Boolean = {
    if (!force && isEnabled()) {
      //Logger.debug("Already connected to Elasticsearch")
      return true
    }
    try {
      val settings = if (nameOfCluster != "") {
        Settings.settingsBuilder().put("cluster.name", nameOfCluster).build()
      } else {
        Settings.settingsBuilder().build()
      }
      client = Some(TransportClient.builder().settings(settings).addPlugin(classOf[ReindexPlugin]).build()
        .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(serverAddress), serverPort)))
      Logger.debug("--- Elasticsearch Client is being created----")
      client match {
        case Some(x) => {
          val indexExists = x.admin().indices().prepareExists(nameOfIndex).execute().actionGet().isExists()
          if (!indexExists) createIndex()
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

  def isEnabled(): Boolean = {
    client match {
      case Some(c) => {
        if (c.connectedNodes().size() > 0) true
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

  /** Prepare and execute Elasticsearch query, and return list of matching ResourceRefs */
  def search(query: List[JsValue], grouping: String, from: Option[Int], size: Option[Int],
             permitted: List[UUID], user: Option[User]): ElasticsearchResult = {
    /** Each item in query list has properties:
      *   "field_key":      full name of field to query, e.g. 'extractors.wordCount.lines'
      *   "operator":       type of query for this term, e.g. '=='
      *   "field_value":    value to search for using specified field & operator
      *   "extractor_key":  name of extractor component only, e.g. 'extractors.wordCount'
      *   "field_leaf_key": name of immediate field only, e.g. 'lines'
      */
    val queryObj = prepareElasticJsonQuery(query, grouping, permitted, user)
    accumulatePageResult(queryObj, user, from.getOrElse(0), size.getOrElse(maxResults))
  }

  /**
   * Search using a simple text string.
   * The API endpoint supports several parameters like datasetid that are translated and appended to the query first.
   * @param query
   * @param resource_type - Restrict to particular resource_type
   * @param datasetid - Restrict to particular dataset ID (only returns files)
   * @param collectionid - Restrict to particular collection ID
   * @param spaceid - Restrict to particular space ID
   * @param folderid - Restrict to particular folder ID
   * @param field - Restrict to a specific metadata field (assumes query is the value)
   * @param tag - Restrict to a particular tag (exact match)
   * @param from
   * @param size
   * @param permitted
   * @param user
   * @param index
   */
  def search(query: String, resource_type: Option[String], datasetid: Option[String], collectionid: Option[String],
             spaceid: Option[String], folderid: Option[String], field: Option[String], tag: Option[String],
             from: Option[Int], size: Option[Int], permitted: List[UUID], user: Option[User],
             index: String = nameOfIndex): ElasticsearchResult = {

    // Convert any parameters from API into the query syntax equivalent so we can parse it all together later
    var expanded_query = query
    field.foreach(k => expanded_query = " "+k+":\""+expanded_query+"\"")
    tag.foreach(t => expanded_query += s" tag:$t")
    resource_type.foreach(restype => expanded_query += s" resource_type:$restype")
    datasetid.foreach(dsid => expanded_query += s" in:$dsid resource_type:file")
    collectionid.foreach(cid => expanded_query += s" in:$cid")
    spaceid.foreach(spid => expanded_query += s" in:$spid")
    folderid.foreach(fid => expanded_query += s" in:$fid")

    val queryObj = prepareElasticJsonQuery(expanded_query.stripPrefix(" "), permitted, user)
    accumulatePageResult(queryObj, user, from.getOrElse(0), size.getOrElse(maxResults))
  }

  /** Perform search, check permissions, and keep searching again if page isn't filled with permitted resources */
  def accumulatePageResult(queryObj: XContentBuilder, user: Option[User], from: Int, size: Int,
                           index: String = nameOfIndex): ElasticsearchResult = {
    var total_results = ListBuffer.empty[ResourceRef]

    // Fetch initial page & filter by permissions
    val (results, total_size) = _search(queryObj, index, Some(from), Some(size))
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
      val (results, total_size)  = _search(queryObj, index, Some(new_from), Some(size*2))
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

    new ElasticsearchResult(total_results.toList,
      from,
      total_results.length,
      scanned_records,
      total_size)
  }

  /** Return a filtered list of resources that user can actually access */
  def checkResultPermissions(results: List[ResourceRef], user: Option[User]): List[ResourceRef] = {
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
  def _search(queryObj: XContentBuilder, index: String = nameOfIndex,
              from: Option[Int] = Some(0), size: Option[Int] = Some(maxResults)): (List[ResourceRef], Long) = {
    connect()
    val response = client match {
      case Some(x) => {
        Logger.info("Searching Elasticsearch: "+queryObj.string())
        var responsePrep = x.prepareSearch(index)
          .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
          .setQuery(queryObj)

        responsePrep = responsePrep.setFrom(from.getOrElse(0))
        responsePrep = responsePrep.setSize(size.getOrElse(maxResults))

        val response = responsePrep.setExplain(true).execute().actionGet()
        Logger.debug("Search hits: " + response.getHits().getTotalHits())
        response
      }
      case None => {
        Logger.error("Could not call search because Elasticsearch is not connected.")
        new SearchResponse()
      }
    }

    (response.getHits().getHits().map(h => {
      new ResourceRef(Symbol(h.getSource().get("resource_type").toString), UUID(h.getId()))
    }).toList, response.getHits().getTotalHits())
  }

  /** Create a new index with preconfigured mappgin */
  def createIndex(index: String = nameOfIndex): Unit = {
    val indexSettings = Settings.settingsBuilder().loadFromSource(jsonBuilder()
      .startObject()
        .startObject("analysis")
          .startObject("analyzer")
            .startObject("default")
              .field("type", "standard")
            .endObject()
            .startObject("email_analyzer")
              .field("type", "custom")
              .field("tokenizer", "uax_url_email")
            .endObject()
          .endObject()
        .endObject()
        .startObject("index")
          .startObject("mapping")
            .field("ignore_malformed", true)
          .endObject()
        .endObject()
      .endObject().string())

    client match {
      case Some(x) => {
        Logger.debug("Index \""+index+"\" does not exist; creating now ---")
        try {
          x.admin().indices().prepareCreate(index)
            .setSettings(indexSettings)
            .addMapping("clowder_object", getElasticsearchObjectMappings())
            .execute().actionGet()
        } catch {
          case e: IndexAlreadyExistsException => {
            Logger.debug("Index already exists; skipping creation.")
          }
        }
      }
      case None =>
    }
  }

  def swapIndex(idx: String): Unit = {
    client match {
      case Some(x) => {
        // Check if swap index exists before swapping
        if (x.admin.indices.exists(new IndicesExistsRequest(idx)).get().isExists()) {
          Logger.debug("Deleting "+nameOfIndex+" index...")
          deleteAll(nameOfIndex)
          createIndex(nameOfIndex)
          Logger.debug("Replacing with "+idx+"...")
          ReindexAction.INSTANCE.newRequestBuilder(x).source(idx).destination(nameOfIndex).get()
          Logger.debug("Deleting "+idx+" index...")
          deleteAll(idx)
        }
      }
      case None =>
    }
  }

  /** Delete all documents in default index */
  def deleteAll(idx: String = nameOfIndex) {
    connect()
    client match {
      case Some(x) => {
        try {
          val response = x.admin().indices().prepareDelete(idx).get()
          if (!response.isAcknowledged())
            Logger.error("Did not delete all data from elasticsearch.")
        } catch {
          case e: ElasticsearchException => {
            Logger.error("Could not call search.", e)
            client = None
            new SearchResponse()
          }
        }
      }
      case None => Logger.error("Could not call index because we are not connected.")
    }
  }

  /** Delete an index */
  def delete(id: String, docType: String = "clowder_object", index: String = nameOfIndex) {
    connect()
    client match {
      case Some(x) => {
        try {
          val response = x.prepareDelete(index, docType, id).execute().actionGet()
          Logger.debug("Deleting document: " + response.getId)
        } catch {
          case e: ElasticsearchException => {
            Logger.error("Could not call search.", e)
            client = None
            new SearchResponse()
          }
        }
      }
      case None => Logger.error("Could not call index because we are not connected.")
    }

  }

  /** Traverse metadata field mappings to get unique list for autocomplete */
  def getAutocompleteMetadataFields(query: String, index: String = nameOfIndex): List[String] = {
    connect()

    var listOfTerms = ListBuffer.empty[String]
    client match {
      case Some(x) => {
        if (query != "") {
          val response = x.admin.indices.getMappings(new GetMappingsRequest().indices(index)).get()
          val maps = response.getMappings().get(index).get("clowder_object")
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
    index(SearchUtils.getElasticsearchObject(collection), idx.getOrElse(nameOfIndex))
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
    index(SearchUtils.getElasticsearchObject(dataset), idx.getOrElse(nameOfIndex))
  }

  /** Reindex the given file. */
  def index(file: File, idx: Option[String]) {
    connect()
    // Index sections first so they register for tag counts
    for (section <- file.sections) {
      index(section, idx)
    }
    index(SearchUtils.getElasticsearchObject(file), idx.getOrElse(nameOfIndex))
  }

  def index(section: Section, idx: Option[String]) {
    connect()
    index(SearchUtils.getElasticsearchObject(section), idx.getOrElse(nameOfIndex))
  }

  /** Index document using an arbitrary map of fields. */
  def index(esObj: Option[models.ElasticsearchObject], index: String = nameOfIndex) {
    esObj match {
      case Some(eso) => {
        connect()
        client match {
          case Some(x) => {
            // Construct the JSON document for indexing
            val builder = jsonBuilder()
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

            val response = x.prepareIndex(index, "clowder_object", eso.resource.id.toString)
              .setSource(builder).execute().actionGet()
          }
          case None => Logger.error("Could not index because Elasticsearch is not connected.")
        }
      }
      case None => Logger.error("No ElasticsearchObject found to index")
    }
  }

  /** Return map of distinct value/count for tags **/
  def listTags(resourceType: String = "", index: String = nameOfIndex): Map[String, Long] = {
    val results = scala.collection.mutable.Map[String, Long]()

    connect()
    client match {
      case Some(x) => {
        val searcher = x.prepareSearch(index)
          .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
            .addAggregation(AggregationBuilders.terms("by_tag").field("tags").size(10000))
            // Don't return actual documents; we only care about aggregation here
            .setSize(0)
        // Filter to tags on a particular type of resource if given
        if (resourceType != "")
          searcher.setQuery(prepareElasticJsonQuery("resource_type:"+resourceType+"", List.empty, None))
        else {
          // Exclude Section tags to avoid double-counting since those are duplicated in File document
          searcher.setQuery(prepareElasticJsonQuery("resource_type:file|dataset|collection", List.empty, None))
        }

        val response = searcher.execute().actionGet()

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
  def convertJsObjectToBuilder(builder: XContentBuilder, json: JsObject): XContentBuilder = {
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
  def convertJsMappingToFields(json: JsObject, parentKey: Option[String] = None,
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
  def getElasticsearchObjectMappings(): String = {

    /** The dynamic template will restrict all dynamic metadata fields to be indexed
     * as strings for datatypes besides Objects. In the future, this could
     * be removed, but only once the Search API better supports those data types (e.g. Date).
     */
    """{"clowder_object": {
          |"numeric_detection": true,
          |"properties": {
            |"name": {"type": "string"},
            |"description": {"type": "string"},
            |"resource_type": {"type": "string", "include_in_all": false},
            |"child_of": {"type": "string", "include_in_all": false},
            |"parent_of": {"type": "string", "include_in_all": false},
            |"creator": {"type": "string", "include_in_all": false},
            |"creator_name": {"type": "string"},
            |"creator_email": {"type": "string", "search_analyzer": "email_analyzer", "analyzer": "email_analyzer"},
            |"created_as": {"type": "string"},
            |"created": {"type": "date", "format": "dateOptionalTime", "include_in_all": false},
            |"metadata": {"type": "object"},
            |"comments": {"type": "string", "include_in_all": false},
            |"tags": {"type": "string"}
          |}
    |}}""".stripMargin
  }

  /**Attempt to cast String into Double, returning None if not possible**/
  def parseDouble(s: String): Option[Double] = {
    Try { s.toDouble }.toOption
  }

  /** Create appropriate search object based on operator */
  def parseMustOperators(builder: XContentBuilder, key: String, value: String, operator: String): XContentBuilder = {
    // TODO: Other date fields may need handling like this? ES always appends 00:00:00 if missing, breaking some things
    val startdate = if (value.length == 10) value+"T00:00:00.000Z" else value
    val enddate   = if (value.length == 10) value+"T23:59:59.999Z" else value
    operator match {
      case "==" => builder.startObject().startObject("match_phrase").field(key, value).endObject().endObject()
      case "<" => {
        if (key=="created")
          builder.startObject().startObject("range").startObject(key).field("lt", startdate).endObject().endObject().endObject()
        else
          builder.startObject().startObject("range").startObject(key).field("lt", value).endObject().endObject().endObject()
      }
      case ">" => {
        if (key=="created")
          builder.startObject().startObject("range").startObject(key).field("gt", enddate).endObject().endObject().endObject()
        else
          builder.startObject().startObject("range").startObject(key).field("gt", value).endObject().endObject().endObject()
      }
      case "<=" => {
        if (key=="created")
          builder.startObject().startObject("range").startObject(key).field("lte", enddate).endObject().endObject().endObject()
        else
          builder.startObject().startObject("range").startObject(key).field("lte", value).endObject().endObject().endObject()
      }
      case ">=" => {
        if (key=="created")
          builder.startObject().startObject("range").startObject(key).field("gte", startdate).endObject().endObject().endObject()
        else
          builder.startObject().startObject("range").startObject(key).field("gte", value).endObject().endObject().endObject()
      }
      case ":" => {
        if (key == "_all")
          builder.startObject().startObject("regexp").field("_all", wrapRegex(value)).endObject().endObject()
        else if (key == "exists") {
          val cleaned = if (!value.startsWith("metadata.")) "metadata."+value else value
          builder.startObject().startObject("exists").field("field", cleaned).endObject().endObject()
        } else if (key == "missing") {
          val cleaned = if (!value.startsWith("metadata.")) "metadata."+value else value
          builder.startObject().startObject("bool").startArray("must_not").startObject()
            .startObject("exists").field("field", cleaned).endObject().endObject().endArray().endObject().endObject()
        } else if (key == "created") {
          builder.startObject.startObject("range").startObject(key).field("gte", startdate).field("lte", enddate).endObject.endObject.endObject
        } else {
          val cleaned = value.replace(":", "\\:") // Colons have special meaning in query_string
          builder.startObject().startObject("query_string").field("default_field", key)
            .field("query", cleaned).endObject().endObject()
        }
      }
      case _ => {}
    }
    builder
  }

  /** Create appropriate search object based on operator */
  def parseMustNotOperators(builder: XContentBuilder, key: String, value: String, operator: String): XContentBuilder = {
    operator match {
      case "!=" => builder.startObject().startObject("match").field(key, value).endObject().endObject()
      case _ => {}
    }
    builder
  }

  /** Convert list of search term JsValues into an Elasticsearch-ready JSON query object **/
  def prepareElasticJsonQuery(query: List[JsValue], grouping: String, permitted: List[UUID], user: Option[User]): XContentBuilder = {
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
    var builder = jsonBuilder().startObject().startObject("bool")

    // -- Wrap MUST/MUST_NOT subqueries in BOOL+SHOULD query if matching ANY term and != is used
    if (grouping == "OR" && mustNotList.length > 0)
      builder.startArray("should").startObject().startObject("bool")

    // 2) populate the MUST/SHOULD portion
    if (mustList.length > 0 || mustNotList.length > 0) {
      grouping match {
        case "AND" => builder.startArray("must")
        case "OR" => builder.startArray("should")
      }
      mustList.foreach(jv => {
        val key = (jv \ "field_key").toString.replace("\"","")
        val operator = (jv \ "operator").toString.replace("\"", "")
        val value = (jv \ "field_value").toString.replace("\"", "")
        builder = parseMustOperators(builder, key, value, operator)
      })

      // Also add != fields to MUST EXISTS query so we don't return all documents without those fields too
      mustNotList.foreach(jv => {
        val key = (jv \ "field_key").toString.replace("\"","")
        builder.startObject().startObject("exists").field("field", key).endObject().endObject()
      })

      // Apply appropriate permissions filters based on user/superadmin
      user match {
        case Some(u) => {
          if (!u.superAdminMode) {
            builder.startObject.startObject("bool").startArray("should")

            // Restrict to spaces the user is permitted access to
            permitted.foreach(ps => {
              builder.startObject().startObject("match").field("child_of", ps.stringify).endObject().endObject()
            })

            // Also include anything the user owns
            builder.startObject().startObject("match").field("creator", u.id.stringify).endObject().endObject()

            builder.endArray().endObject().endObject()
          }
        }
        case None => {
          // Metadata search is not publicly accessible so this shouldn't happen, public filter
          builder.startObject.startObject("bool").startArray("should")

          // TODO: Does this behave properly with public spaces?
          spaces.list.foreach(ps => {
            builder.startObject().startObject("match").field("child_of", ps.id.stringify).endObject().endObject()
          })

          builder.endArray().endObject().endObject()
        }
      }

      builder.endArray()
    }

    // 3) populate the MUST_NOT portion
    if (mustNotList.length > 0) {
      // -- Again, special handling for mixing OR grouping with != operator so it behaves as user expects
      if (grouping == "OR")
        builder.endObject().endObject().startObject().startObject("bool").startArray("should")
      else
        builder.startArray("must_not")

      mustNotList.foreach(jv => {
        if (grouping == "OR")
          builder.startObject().startObject("bool").startArray("must_not")

        val key = (jv \ "field_key").toString.replace("\"","")
        val operator = (jv \ "operator").toString.replace("\"", "")
        val value = (jv \ "field_value").toString.replace("\"", "")
        builder = parseMustNotOperators(builder, key, value, operator)

        if (grouping == "OR")
          builder.endArray().endObject().endObject()
      })

      if (grouping == "OR")
        builder.endArray().endObject().endObject().endArray()
      else
        builder.endArray()
    }

    // Close the bool/query objects and return
    builder.endObject().endObject()
    builder
  }

  /** Convert search string into an Elasticsearch-ready JSON query object **/
  def prepareElasticJsonQuery(query: String, permitted: List[UUID], user: Option[User]): XContentBuilder = {

    // Use regex to split string into a list, preserving quoted phrases as single value
    val matches = ListBuffer[String]()
    val m = Pattern.compile("([^\":=<> ]+|\".+?\")").matcher(query)
    while (m.find()) {
      var mat = m.group(1).replace("\"", "").replace("__", " ").trim
      if (mat.length>0) {
        // Remove operators from terms e.g. <=value becomes value
        (mustOperators ::: mustNotOperators).foreach(op => {
          if (mat.startsWith(op)) {
            // Make sure x<=4 is "x lte 4" not "x lt =4"
            var foundLonger = false
            (mustOperators ::: mustNotOperators).foreach(longerop => {
              if (longerop!=op && longerop.length>op.length && mat.startsWith(longerop)) {
                mat = mat.substring(longerop.length)
                foundLonger = true
              }
            })
            if (!foundLonger)
              mat = mat.substring(op.length)
          }
        })
        matches += mat
      }
    }

    // If a term is specified that isn't in this list, it's assumed to be a metadata field
    val official_terms = List("name", "creator", "created", "email", "resource_type", "in", "contains", "tag", "exists", "missing")

    // Create list of (key, operator, value) for passing to builder
    val terms = ListBuffer[(String, String, String)]()
    var currkey = "_all"
    var curropr = ":" // Defaults to 'contains' match on _all if no key:value pairs are found (assumes whole string is the value)
    var currval = ""
    matches.foreach(mt => {
      // Check if the current term appears before or after one of the operators, and what operator is
      var entryType = "unknown"
      (mustOperators ::: mustNotOperators).foreach(op => {
        val reducedSpaces = StringUtils.normalizeSpace(query)
        if ((reducedSpaces.contains(mt+op) || reducedSpaces.contains("\""+mt+"\""+op) ||
            reducedSpaces.contains(mt+" "+op) || reducedSpaces.contains("\""+mt+"\" "+op)) && entryType=="unknown") {
          entryType = "key"
          curropr = op
        } else if (reducedSpaces.contains(op+mt) || reducedSpaces.contains(op+"\""+mt+"\"") ||
            reducedSpaces.contains(op+" "+mt) || reducedSpaces.contains(op+" \""+mt+"\"")) {
          entryType = "value"
          curropr = op
        }
      })
      if (entryType=="unknown") entryType = "value"

      // Determine if the string was a key or value
      if (entryType == "key") {
        // Do some user-friendly replacement
        if (mt == "tag") currkey = "tags"
        else if (mt == "in") currkey = "child_of"
        else if (mt == "contains") currkey = "parent_of"
        else if (mt == "creator") currkey = "creator_name"
        else if (mt == "created") {
          currkey = "created"
        }
        else if (mt == "email") currkey = "creator_email"
        else if (!official_terms.contains(mt)) currkey = "metadata."+mt
        else
          currkey = mt
      } else if (entryType == "value") {
        if (currkey!="exists" && currkey!="missing")
          currval = mt.toLowerCase()
        else currval= mt
        terms += ((currkey, curropr, currval))
        currkey = "_all"
        curropr = ":"
        currval = ""
      }
    })

    // Now that we have a nicely structured list of (key, operator, value) tuples we can translate to Elastic objects
    var builder = jsonBuilder().startObject().startObject("bool")

    // First, populate the MUST portion of Bool query
    var populatedMust = false
    terms.map(entry => {
      val key = entry._1
      val curropr = entry._2
      val value = entry._3
      if (mustOperators.contains(curropr)) {
        // Only add a MUST object if we have terms to populate it; empty objects break Elasticsearch
        if (!populatedMust) {
          builder.startArray("must")
          populatedMust = true
        }
        builder = parseMustOperators(builder, key, value, curropr)
      }

      // For != operators, include an EXISTS query to avoid returning all documents without that field
      if (mustNotOperators.contains(curropr)) {
        if (!populatedMust) {
          builder.startArray("must")
          populatedMust = true
        }
        builder.startObject().startObject("exists").field("field", key).endObject().endObject()
      }
    })

    // Apply appropriate permissions filters based on user/superadmin
    user match {
      case Some(u) => {
        if (!u.superAdminMode) {
          // Only add a MUST object if we have terms to populate it; empty objects break Elasticsearch
          if (!populatedMust) {
            builder.startArray("must")
            populatedMust = true
          }

          builder.startObject.startObject("bool").startArray("should")

          // Restrict to spaces the user is permitted access to
          permitted.foreach(ps => {
            builder.startObject().startObject("match").field("child_of", ps.stringify).endObject().endObject()
          })

          // Also include anything the user owns
          builder.startObject().startObject("match").field("creator", u.id.stringify).endObject().endObject()

          builder.endArray().endObject().endObject()
        }
      }
      case None => {
        // Metadata search is not publicly accessible so this shouldn't happen, public filter
        builder.startObject.startObject("bool").startArray("should")

        // TODO: Does this behave properly with public spaces?
        spaces.list.foreach(ps => {
          builder.startObject().startObject("match").field("child_of", ps.id.stringify).endObject().endObject()
        })

        builder.endArray().endObject().endObject()
      }
    }

    if (populatedMust) builder.endArray()

    // Second, populate the MUST NOT portion of Bool query
    var populatedMustNot = false
    terms.map(entry => {
      val key = entry._1
      val curropr = entry._2
      val value = entry._3
      if (mustNotOperators.contains(curropr)) {
        // Only add a MUST object if we have terms to populate it; empty objects break Elasticsearch
        if (!populatedMustNot) {
          builder.startArray("must_not")
          populatedMustNot = true
        }
        builder = parseMustNotOperators(builder, key, value, curropr)
      }
    })
    if (populatedMustNot) builder.endArray()

    // Close the bool/query objects and return
    builder.endObject().endObject()
    builder
  }

  def wrapRegex(value: String, query_string: Boolean = false): String = {
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

  override def onStop() {
    client.map(_.close())
    client = None
    Logger.info("ElasticsearchPlugin has stopped")
  }

}