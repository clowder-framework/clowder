package util

import models._
import play.api.Logger
import play.api.libs.json._
import services._

import scala.collection.immutable.List
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory._


object SearchUtils {
  lazy val files: FileService = DI.injector.getInstance(classOf[FileService])
  lazy val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  lazy val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])
  lazy val metadatas: MetadataService = DI.injector.getInstance(classOf[MetadataService])
  lazy val comments = DI.injector.getInstance(classOf[CommentService])

  val mustOperators = List(":", "==", "<", ">")
  val mustNotOperators = List("!=")

  /**Convert File to ElasticsearchObject and return, fetching metadata as necessary**/
  def getElasticsearchObject(f: File): Option[ElasticsearchObject] = {
    val id = f.id

    // Get child_of relationships for File
    val child_of = datasets.findByFileId(id).map( ds => ds.id.toString )

    // Get comments for file
    val fcomments = for (c <- comments.findCommentsByFileId(id)) yield {
      Comment.toElasticsearchComment(c)
    }

    // Get metadata for File
    var metadata = Map[String, JsObject]()
    for (md <- metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.file, id))) {
      val creator = md.creator.displayName
      if (metadata.keySet.exists(_ == creator)) {
        // If we already have some metadata from this creator, merge the results
        metadata += (creator -> (metadata(creator) ++ (md.content.as[JsObject])))
      } else {
        // Otherwise create a new entry for this creator
        metadata += (creator -> md.content.as[JsObject])
      }
    }
    // TODO: Can these be removed? MongoSalat process to migrate them to Metadata collection?
    //val usrMd = getUserMetadataJSON(id)
    //val techMd = getTechnicalMetadataJSON(id)
    //val xmlMd = getXMLMetadataJSON(id)

    Some(new ElasticsearchObject(
      ResourceRef('file, id),
      f.filename,
      f.author.id.toString,
      f.uploadDate,
      List.empty,
      child_of,
      f.description,
      f.tags.map( (t:Tag) => Tag.toElasticsearchTag(t) ),
      fcomments,
      metadata
    ))
  }

  /**Convert Dataset to ElasticsearchObject and return, fetching metadata as necessary**/
  def getElasticsearchObject(ds: Dataset): Option[ElasticsearchObject] = {
    val id = ds.id

    // Get comments for dataset
    val dscomments = for (c <- comments.findCommentsByDatasetId(id)) yield {
      Comment.toElasticsearchComment(c)
    }

    // Get metadata for File
    var metadata = Map[String, JsObject]()
    for (md <- metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, id))) {
      val creator = md.creator.displayName
      if (metadata.keySet.exists(_ == creator)) {
        // If we already have some metadata from this creator, merge the results
        metadata += (creator -> (metadata(creator) ++ (md.content.as[JsObject])))
      } else {
        // Otherwise create a new entry for this creator
        metadata += (creator -> md.content.as[JsObject])
      }
    }
    // TODO: Can these be removed? MongoSalat process to migrate them to Metadata collection?
    //val usrMd = datasets.getUserMetadataJSON(dataset.id)
    //val techMd = datasets.getTechnicalMetadataJSON(dataset.id)
    //val xmlMd = datasets.getXMLMetadataJSON(dataset.id)

    Some(new ElasticsearchObject(
      ResourceRef('dataset, id),
      ds.name,
      ds.author.id.toString,
      ds.created,
      ds.files.map(fileId => fileId.toString),
      ds.collections.map(collId => collId.toString),
      ds.description,
      ds.tags.map( (t:Tag) => Tag.toElasticsearchTag(t) ),
      dscomments,
      metadata
    ))
  }

  /**Convert Collection to ElasticsearchObject and return, fetching metadata as necessary**/
  def getElasticsearchObject(c: Collection): Option[ElasticsearchObject] = {
    // Get parent_of relationships for Collection
    var parent_of = datasets.listCollection(c.id.toString).map( ds => ds.id.toString )
    parent_of = parent_of ++ c.parent_collection_ids.map( pc_id => pc_id.toString)

    Some(new ElasticsearchObject(
      ResourceRef('collection, c.id),
      c.name,
      c.author.id.toString,
      c.created,
      parent_of,
      c.child_collection_ids.map( cc_id => cc_id.toString),
      c.description,
      List.empty,
      List.empty,
      Map()
    ))
  }

  /**Convert TempFile to ElasticsearchObject and return, fetching metadata as necessary**/
  def getElasticsearchObject(file: TempFile): Option[ElasticsearchObject] = {
    Some(new ElasticsearchObject(
      ResourceRef('file, file.id),
      file.filename,
      "",
      file.uploadDate,
      List.empty,
      List.empty,
      "",
      List.empty,
      List.empty,
      Map()
    ))
  }

  /**Convert list of search term JsValues into an Elasticsearch-ready JSON query object**/
  def prepareElasticJsonQuery(query: List[JsValue]): XContentBuilder = {
    /** OPERATORS
      *  :   contains (partial match)
      *  ==  equals (exact match)
      *  !=  not equals (partial matches OK)
      *  <   less than
      *  >   greater than
      **/
    // BOOL - https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-bool-query.html
    var builder = jsonBuilder().startObject().startObject("bool")

    // First, populate the MUST portion of Bool query
    var populatedMust = false
    query.foreach(jv => {
      val key = (jv \ "field_key").toString.replace("\"","")
      val operator = (jv \ "operator").toString.replace("\"", "")
      val value = (jv \ "field_value").toString.replace("\"", "")

      // Only add a MUST object if we have terms to populate it; empty objects break Elasticsearch
      if (mustOperators.contains(operator) && !populatedMust) {
        builder.startObject("must")
        populatedMust = true
      }

      builder = parseMustOperators(builder, key, value, operator)
    })
    if (populatedMust) builder.endObject()

    // Second, populate the MUST NOT portion of Bool query
    var populatedMustNot = false
    query.foreach(jv => {
      val key = (jv \ "field_key").toString.replace("\"","")
      val operator = (jv \ "operator").toString.replace("\"", "")
      val value = (jv \ "field_value").toString.replace("\"", "")

      // Only add a MUST object if we have terms to populate it; empty objects break Elasticsearch
      if (mustNotOperators.contains(operator) && !populatedMustNot) {
        builder.startObject("must_not")
        populatedMustNot = true
      }

      builder = parseMustNotOperators(builder, key, value, operator)
    })
    if (populatedMustNot) builder.endObject()

    // Close the bool/query objects and return
    builder.endObject().endObject()
    builder
  }

  /**Convert search string into an Elasticsearch-ready JSON query object**/
  def prepareElasticJsonQuery(query: String): XContentBuilder = {
    /** OPERATORS
      *  :   contains (partial match)
      *  ==  equals (exact match)
      *  !=  not equals (partial matches OK)
      *  <   less than
      *  >   greater than
      **/
    // TODO: Make this more robust, perhaps with some RegEx or something, to support quoted phrases
    val terms = query.split(" ")

    // BOOL - https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-bool-query.html
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
            builder.startObject("must")
            populatedMust = true
          }

          builder = parseMustOperators(builder, key, value, operator)
        }
      }
    })
    if (populatedMust) builder.endObject()

    // Second, populate the MUST NOT portion of Bool query
    var populatedMustNot = false
    terms.map(term => {
      for (operator <- mustNotOperators) {
        if (term.contains(operator)) {
          val key = term.substring(0, term.indexOf(operator))
          val value = term.substring(term.indexOf(operator), term.length)

          // Only add a MUST object if we have terms to populate it; empty objects break Elasticsearch
          if (mustNotOperators.contains(operator) && !populatedMustNot) {
            builder.startObject("must_not")
            populatedMustNot = true
          }

          builder = parseMustNotOperators(builder, key, value, operator)
        }
      }
    })
    if (populatedMustNot) builder.endObject()

    // Close the bool/query objects and return
    builder.endObject().endObject()
    builder
  }

  /** Create appropriate search object based on operator */
  def parseMustOperators(builder: XContentBuilder, key: String, value: String, operator: String): XContentBuilder = {
    operator match {
      case ":" => {
        // WILDCARD - https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-wildcard-query.html
        // TODO: Elasticsearch recommends not starting query with wildcard
        // TODO: Consider inverted index? https://www.elastic.co/blog/found-elasticsearch-from-the-bottom-up
        //builder.startObject("wildcard").field(key, value+"*").endObject()
        builder.startObject("match").field(key, value).endObject()
      }
      case "==" => {
        // MATCH - https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-match-query.html
        builder.startObject("match").field(key, value).endObject()
      }
      case "<" => {
        // RANGE - https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-range-query.html
        builder.startObject("range").startObject(key).field("lt", value).endObject().endObject()
      }
      case ">" => {
        // TODO: Suppert lte, gte (<=, >=)
        builder.startObject("range").startObject(key).field("gt", value).endObject().endObject()
      }
      case _ => {}
    }
    builder
  }

  /** Create appropriate search object based on operator */
  def parseMustNotOperators(builder: XContentBuilder, key: String, value: String, operator: String): XContentBuilder = {
    operator match {
      case "!=" => {
        // MATCH - https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-match-query.html
        builder.startObject("match").field(key, value).endObject()
      }
      case _ => {}
    }
    builder
  }
}
