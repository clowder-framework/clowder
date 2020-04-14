package services

import api.Permission
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms.Bucket
import play.api.libs.json.Json._
import scala.util.Try
import scala.collection.mutable.{MutableList, ListBuffer}
import scala.collection.immutable.List
import play.api.{Plugin, Logger, Application}
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import java.net.InetAddress
import java.util.regex.Pattern

import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.action.search.{SearchPhaseExecutionException, SearchType, SearchResponse}
import org.elasticsearch.client.transport.NoNodeAvailableException
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.indices.IndexAlreadyExistsException

import models.{Collection, Dataset, File, Folder, UUID, ResourceRef, Section, ElasticsearchResult, User}
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
  val folders: FolderService = DI.injector.getInstance(classOf[FolderService])
  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])
  val queue: ElasticsearchQueue = DI.injector.getInstance(classOf[ElasticsearchQueue])
  var client: Option[TransportClient] = None
  val nameOfCluster = play.api.Play.configuration.getString("elasticsearchSettings.clusterName").getOrElse("clowder")
  val serverAddress = play.api.Play.configuration.getString("elasticsearchSettings.serverAddress").getOrElse("localhost")
  val serverPort = play.api.Play.configuration.getInt("elasticsearchSettings.serverPort").getOrElse(9300)
  val nameOfIndex = play.api.Play.configuration.getString("elasticsearchSettings.indexNamePrefix").getOrElse("clowder")
  val maxResults = play.api.Play.configuration.getInt("elasticsearchSettings.maxResults").getOrElse(240)

  val mustOperators = List("==", "<", ">", ":")
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
      client = Some(TransportClient.builder().settings(settings).build()
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
  def search(query: List[JsValue], grouping: String, from: Option[Int], size: Option[Int], user: Option[User]): ElasticsearchResult = {
    /** Each item in query list has properties:
      *   "field_key":      full name of field to query, e.g. 'extractors.wordCount.lines'
      *   "operator":       type of query for this term, e.g. '=='
      *   "field_value":    value to search for using specified field & operator
      *   "extractor_key":  name of extractor component only, e.g. 'extractors.wordCount'
      *   "field_leaf_key": name of immediate field only, e.g. 'lines'
      */
    val queryObj = prepareElasticJsonQuery(query, grouping)
    accumulatePageResult(queryObj, user, from.getOrElse(0), size.getOrElse(maxResults))
  }

  /** Search using a simple text string, appending parameters from API to string if provided */
  def search(query: String, resource_type: Option[String], datasetid: Option[String], collectionid: Option[String],
             spaceid: Option[String], folderid: Option[String], field: Option[String], tag: Option[String],
             from: Option[Int], size: Option[Int], permitted: List[UUID], user: Option[User],
             index: String = nameOfIndex): ElasticsearchResult = {

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

    val queryObj = prepareElasticJsonQuery(expanded_query.stripPrefix(" "), permitted)
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

  /** Delete all indices */
  def deleteAll {
    connect()
    client match {
      case Some(x) => {
        try {
          val response = x.admin().indices().prepareDelete(nameOfIndex).get()
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
  def index(collection: Collection, recursive: Boolean) {
    connect()
    // Perform recursion first if necessary
    if (recursive) {
      for (dataset <- datasets.listCollection(collection.id.toString)) {
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
    connect()
    // Perform recursion first if necessary
    if (recursive) {
      files.get(dataset.files).found.foreach(f => index(f))
      for (folderid <- dataset.folders) {
        folders.get(folderid) match {
          case Some(f) => {
            files.get(f.files).found.foreach(fi => index(fi))
          }
          case None => Logger.error(s"Error getting file $folderid for recursive indexing")
        }
      }
    }
    index(SearchUtils.getElasticsearchObject(dataset))
  }

  /** Reindex the given file. */
  def index(file: File) {
    connect()
    // Index sections first so they register for tag counts
    for (section <- file.sections) {
      index(section)
    }
    index(SearchUtils.getElasticsearchObject(file))
  }

  def index(section: Section) {
    connect()
    index(SearchUtils.getElasticsearchObject(section))
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
          searcher.setQuery(prepareElasticJsonQuery("resource_type:"+resourceType+"", List.empty))
        else {
          // Exclude Section tags to avoid double-counting since those are duplicated in File document
          searcher.setQuery(prepareElasticJsonQuery("resource_type:file|dataset|collection", List.empty))
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
     * as strings, regardless of interpreted data type. In the future, this could
     * be removed, but only once the Search API better supports those data types (e.g. Date).
     */
    """{"clowder_object": {
          |"dynamic_templates": [{
            |"metadata_interpreter": {
              |"match": "*",
              |"match_mapping_type": "*",
              |"mapping": {"type": "string"}
            |}
          |}],
          |"properties": {
            |"name": {"type": "string"},
            |"description": {"type": "string"},
            |"resource_type": {"type": "string", "include_in_all": false},
            |"child_of": {"type": "string", "include_in_all": false},
            |"parent_of": {"type": "string", "include_in_all": false},
            |"creator": {"type": "string", "include_in_all": false},
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
    // TODO: Suppert lte, gte (<=, >=)
    operator match {
      case "==" => builder.startObject().startObject("match_phrase").field(key, value).endObject().endObject()
      case "<" => builder.startObject().startObject("range").startObject(key).field("lt", value).endObject().endObject().endObject()
      case ">" => builder.startObject().startObject("range").startObject(key).field("gt", value).endObject().endObject().endObject()
      case ":" => {
        if (key == "_all")
          builder.startObject().startObject("regexp").field("_all", wrapRegex(value)).endObject().endObject()
        else
          builder.startObject().startObject("query_string").field("default_field", key).field("query", value).endObject().endObject()
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

  /**Convert list of search term JsValues into an Elasticsearch-ready JSON query object**/
  def prepareElasticJsonQuery(query: List[JsValue], grouping: String): XContentBuilder = {
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
    if (mustList.length > 0) {
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

  /**Convert search string into an Elasticsearch-ready JSON query object**/
  def prepareElasticJsonQuery(query: String, permitted: List[UUID]): XContentBuilder = {
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
    val official_terms = List("name", "creator", "resource_type", "in", "contains", "tag")

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

    var builder = jsonBuilder().startObject().startObject("bool")

    // First, populate the MUST portion of Bool query
    var populatedMust = false
    terms.map(term => {
      for (operator <- mustOperators) {
        if (term.contains(operator)) {
          val key = term.substring(0, term.indexOf(operator))
          val value = term.substring(term.indexOf(operator)+1, term.length)

          // Only add a MUST object if we have terms to populate it; empty objects break Elasticsearch
          if (mustOperators.contains(operator) && !populatedMust) {
            builder.startArray("must")
            populatedMust = true
          }

          builder = parseMustOperators(builder, key, value, operator)
        }
      }
    })

    // Include special OR condition for restricting to permitted spaces
    if (permitted.length > 0) {
      // Only add a MUST object if we have terms to populate it; empty objects break Elasticsearch
      if (!populatedMust) {
        builder.startArray("must")
        populatedMust = true
      }
      builder.startObject.startObject("bool").startArray("should")
      permitted.foreach(ps => {
        builder.startObject().startObject("match").field("child_of", ps.stringify).endObject().endObject()
      })
      builder.endArray().endObject().endObject()
    }

    if (populatedMust) builder.endArray()

    // Second, populate the MUST NOT portion of Bool query
    var populatedMustNot = false
    terms.map(term => {
      for (operator <- mustNotOperators) {
        if (term.contains(operator)) {
          val key = term.substring(0, term.indexOf(operator))
          val value = term.substring(term.indexOf(operator), term.length)

          // Only add a MUST object if we have terms to populate it; empty objects break Elasticsearch
          if (mustNotOperators.contains(operator) && !populatedMustNot) {
            builder.startArray("must_not")
            populatedMustNot = true
          }

          builder = parseMustNotOperators(builder, key, value, operator)
        }
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