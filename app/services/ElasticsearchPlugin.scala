package services

import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms.Bucket
import scala.util.Try
import scala.collection.mutable.{MutableList, ListBuffer}
import scala.collection.immutable.List
import play.api.libs.concurrent.Execution.Implicits._
import play.api.{Plugin, Logger, Application}
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import java.net.InetAddress

import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.action.search.{SearchPhaseExecutionException, SearchType, SearchResponse}
import org.elasticsearch.client.transport.NoNodeAvailableException

import models.{Collection, Dataset, File, UUID, ResourceRef, Section}
import play.api.Play.current
import play.api.libs.json._
import _root_.util.SearchUtils

import org.elasticsearch.index.query.QueryBuilders

import org.elasticsearch.ElasticsearchException
import org.elasticsearch.indices.IndexAlreadyExistsException


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

  val mustOperators = List("==", "<", ">", ":")
  val mustNotOperators = List("!=")


  override def onStart() {
    Logger.debug("ElasticsearchPlugin started but not yet connected")
    connect()
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
  def search(query: List[JsValue], grouping: String, from: Option[Int], size: Option[Int]): List[ResourceRef] = {
    /** Each item in query list has properties:
      *   "field_key":      full name of field to query, e.g. 'extractors.wordCount.lines'
      *   "operator":       type of query for this term, e.g. '=='
      *   "field_value":    value to search for using specified field & operator
      *   "extractor_key":  name of extractor component only, e.g. 'extractors.wordCount'
      *   "field_leaf_key": name of immediate field only, e.g. 'lines'
      */
    val queryObj = prepareElasticJsonQuery(query, grouping)
    val response: SearchResponse = _search(queryObj, from=from, size=size)

    var results = MutableList[ResourceRef]()
    Option(response.getHits()) match {
      case Some(hits) => {
        for (hit <- hits.getHits()) {
          val resource_type = hit.getSource().get("resource_type").toString
          results += new ResourceRef(Symbol(resource_type), UUID(hit.getId()))
        }
      }
      case None => {}
    }

    results.toList
  }

  /** Search using a simple text string */
  def search(query: String, index: String = nameOfIndex): List[ResourceRef] = {
    val specialOperators = mustOperators ++ mustNotOperators
    val queryObj = if (specialOperators.exists(query.contains(_))) {
      // Parse search string into object based on special operators
      prepareElasticJsonQuery(query)
    } else {
      // Plain text search with no field qualifiers
      jsonBuilder().startObject().startObject("regexp").field("_all", query).endObject().endObject()
    }

    try {
      val response = _search(queryObj, index)
      var results = MutableList[ResourceRef]()
      Option(response.getHits()) match {
        case Some(hits) => {
          for (hit <- hits.getHits()) {
            val resource_type = hit.getSource().get("resource_type").toString
            results += new ResourceRef(Symbol(resource_type), UUID(hit.getId()))
          }
        }
        case None => {}
      }

      results.toList
    } catch {
      case spee: SearchPhaseExecutionException => {
        List[ResourceRef]()
      }
      case e: Exception => {
        List[ResourceRef]()
      }
    }
  }

  /*** Execute query */
  def _search(queryObj: XContentBuilder, index: String = nameOfIndex, from: Option[Int] = Some(0), size: Option[Int] = Some(60)): SearchResponse = {
    connect()
    client match {
      case Some(x) => {
        Logger.info("Searching Elasticsearch: "+queryObj.string())
        var responsePrep = x.prepareSearch(index)
          .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
          .setQuery(queryObj)

        from match {
          case Some(f) => responsePrep = responsePrep.setFrom(f)
          case None => {}
        }
        size match {
          case Some(s) => responsePrep = responsePrep.setSize(s)
          case None => {}
        }

        val response = responsePrep.setExplain(true).execute().actionGet()
        Logger.debug("Search hits: " + response.getHits().getTotalHits())
        response
      }
      case None => {
        Logger.error("Could not call search because Elasticsearch is not connected.")
        new SearchResponse()
      }
    }
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
  def delete(index: String, docType: String, id: String) {
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
      for (fileId <- dataset.files) {
        files.get(fileId) match {
          case Some(f) => {
            index(f)
          }
          case None => Logger.error(s"Error getting file $fileId for recursive indexing")
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
            .addAggregation(AggregationBuilders.terms("by_tag").field("tags.name").size(10000))
            // Don't return actual documents; we only care about aggregation here
            .setSize(0)
        // Filter to tags on a particular type of resource if given
        if (resourceType != "") searcher.setQuery(prepareElasticJsonQuery("resource_type:"+resourceType+""))
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
    """dynamic_templates": [{
       "nonindexer": {
          "match": "*",
          "match_mapping_type":"string",
          "mapping": {
            "type": "string",
            "index": "not_analyzed"
          }
        }
      }
    ],"""

    """{"clowder_object": {
          |"properties": {
            |"resource_type": {"type": "string"},
            |"child_of": {"type": "string"},
            |"parent_of": {"type": "string"},
            |"creator": {"type": "string"},
            |"created": {"type": "date", "format": "dateOptionalTime"},
            |"metadata": {"type": "object"},
            |"comments": {
              |"properties": {
                |"created": {"type": "date", "format": "dateOptionalTime"},
                |"creator": {"type": "string"},
                |"name": {"type": "string", "index": "not_analyzed"}
              |}
            |},
            |"tags": {
              |"properties": {
                |"created": {"type":"date", "format":"dateOptionalTime"},
                |"creator": {"type": "string"},
                |"name": {"type": "string", "index":"not_analyzed"}
              |}
            |}
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
      case ":" => builder.startObject().startObject("query_string").field("default_field", key).field("query", "*"+value+"*").endObject().endObject()
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
  def prepareElasticJsonQuery(query: String): XContentBuilder = {
    /** OPERATORS
      *  ==  equals (exact match)
      *  !=  not equals (partial matches OK)
      *  <   less than
      *  >   greater than
      **/
    // TODO: Make this more robust, perhaps with some RegEx or something, to support quoted phrases
    val terms = query.split(" ")

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

  override def onStop() {
    client.map(_.close())
    client = None
    Logger.info("ElasticsearchPlugin has stopped")
  }

}
