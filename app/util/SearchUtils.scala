package util

import java.text.SimpleDateFormat

import models._
import play.api.Logger
import play.api.libs.json._
import services._

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer
import scala.util.parsing.json.JSONArray


object SearchUtils {
  lazy val files: FileService = DI.injector.getInstance(classOf[FileService])
  lazy val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  lazy val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])
  lazy val metadatas: MetadataService = DI.injector.getInstance(classOf[MetadataService])
  lazy val comments = DI.injector.getInstance(classOf[CommentService])

  /**Convert File to ElasticSearchObject and return, fetching metadata as necessary**/
  def getElasticSearchObject(f: File): Option[ElasticSearchObject] = {
    val id = f.id

    // Get child_of relationships for File
    val child_of = datasets.findByFileId(id).map(ds => {
      ds.id.toString
    })

    // Get comments for file
    val fcomments = for (comment <- comments.findCommentsByFileId(id)) yield {
      comment.asInstanceOf[ElasticSearchComment]
    }

    // Get metadata for File
    var metadata = Map[String, JsValue]()
    for (md <- metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.file, id))) {
      val creator = md.creator.displayName
      if (metadata.keySet.exists(_ == creator)) {
        // If we already have some metadata from this creator, merge the results
        metadata += (creator -> (metadata(creator).as[JsObject] ++ (md.content.as[JsObject])))
      } else {
        // Otherwise create a new entry for this creator
        metadata += (creator -> md.content)
      }
    }
    // TODO: Can these be removed? MongoSalat process to migrate them to Metadata collection?
    //val usrMd = getUserMetadataJSON(id)
    //val techMd = getTechnicalMetadataJSON(id)
    //val xmlMd = getXMLMetadataJSON(id)

    Some(new ElasticSearchObject(
      ResourceRef('file, id),
      f.author.id.toString,
      f.uploadDate,
      List.empty,
      child_of,
      f.tags.map( (t:Tag) => t.asInstanceOf[ElasticSearchTag] ),
      fcomments,
      metadata
    ))
  }

  /**Convert Dataset to ElasticSearchObject and return, fetching metadata as necessary**/
  def getElasticSearchObject(ds: Dataset): Option[ElasticSearchObject] = {
    val id = ds.id

    // Get comments for dataset
    val dscomments = for (comment <- comments.findCommentsByDatasetId(id)) yield {
      comment.asInstanceOf[ElasticSearchComment]
    }

    val metadata = datasets.getMetadata(id).map( (m:(String,Any)) => (m._1 -> Json.parse(m._2.toString)))
    // TODO: Can these be removed? MongoSalat process to migrate them to Metadata collection?
    //val usrMd = datasets.getUserMetadataJSON(dataset.id)
    //val techMd = datasets.getTechnicalMetadataJSON(dataset.id)
    //val xmlMd = datasets.getXMLMetadataJSON(dataset.id)

    Some(new ElasticSearchObject(
      ResourceRef('dataset, id),
      ds.author.id.toString,
      ds.created,
      ds.files.map(fileId => fileId.toString),
      ds.collections.map(collId => collId.toString),
      ds.tags.map( (t:Tag) => t.asInstanceOf[ElasticSearchTag] ),
      dscomments,
      metadata
    ))
  }

  /**Convert Collection to ElasticSearchObject and return, fetching metadata as necessary**/
  def getElasticSearchObject(c: Collection): Option[ElasticSearchObject] = {
    // Get parent_of relationships for Collection
    var parent_of = datasets.listCollection(c.id.toString).map(ds => {
      ds.id.toString
    })
    parent_of = parent_of ++ c.parent_collection_ids.map( pc_id => pc_id.toString)

    Some(new ElasticSearchObject(
      ResourceRef('collection, c.id),
      c.author.id.toString,
      c.created,
      parent_of,
      c.child_collection_ids.map( cc_id => cc_id.toString),
      List.empty,
      List.empty,
      Map()
    ))
  }

  /**Convert File to ElasticSearchObject and return, fetching metadata as necessary**/
  def getElasticSearchObject(file: TempFile): Option[ElasticSearchObject] = {
    Some(new ElasticSearchObject(
      ResourceRef('file, file.id),
      "",
      file.uploadDate,
      List.empty,
      List.empty,
      List.empty,
      List.empty,
      Map()
    ))
  }

}
