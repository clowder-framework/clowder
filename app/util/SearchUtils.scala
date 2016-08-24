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
  // TODO: Can we remove the indexing on this TempFile entirely? comment out indexing after asking Inna/Smruti
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
}
