package controllers

import javax.inject.Inject

import models.User
import play.api.Logger
import play.api.Play.current
import services.{CollectionService, DatasetService, FileService, SectionService, ElasticsearchPlugin}
import util.Parsers

import scala.collection.mutable.ListBuffer

/**
 * Tagging.
 */
class Tags @Inject()(collections: CollectionService, datasets: DatasetService, files: FileService, sections: SectionService) extends SecuredController {

  def tagListWeighted() = PrivateServerAction { implicit request =>
    implicit val user = request.user
    val tags = computeTagWeights(user)
    if (tags.isEmpty) {
      Ok(views.html.tagList(List.empty[(String, Double)]))
    } else {
      val minFont = current.configuration.getDouble("tag.list.minFont").getOrElse(1.0)
      val maxFont = current.configuration.getDouble("tag.list.maxFont").getOrElse(5.0)
      val maxWeight = tags.maxBy(_._2)._2
      val minWeight = tags.minBy(_._2)._2
      val divide = (maxFont - minFont) / (maxWeight - minWeight)

      Ok(views.html.tagList(tags.map { case (k, v) => (k, minFont + (v - minWeight) * divide) }))
    }
  }

  def tagListOrdered() = PrivateServerAction { implicit request =>
    implicit val user = request.user

    Ok(views.html.tagListChar(createTagList(user)))
  }

  def createTagList(user: Option[User]) = {
    val tagMap = collection.mutable.Map.empty[Char, collection.mutable.Map[String, Long]]

    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) if plugin.isEnabled() => {
        val results = plugin.listTags()
        for ((k,v) <- results) {
          val firstChar = if (k(0).isLetter) k(0).toUpper else '#'
          if (!tagMap.contains(firstChar)) {
            val map = collection.mutable.Map[String, Long]((k, v)).withDefaultValue(0)
            tagMap(firstChar) = map
          } else {
            val map = tagMap(firstChar)
            map(k) = map(k) + v
          }
        }
      }
      case _ => {
        Logger.error("ElasticSearch plugin could not be reached for tag search; using Mongo instead")

        datasets.getTags(user).foreach { case (tag: String, count: Long) =>
          if (tag.length == 0) {
            Logger.error("tag with length 0 : " + tag + " " + count)
          } else {
            val firstChar = if (tag(0).isLetter) tag(0).toUpper else '#'
            if (!tagMap.contains(firstChar)) {
              val map = collection.mutable.Map[String, Long]((tag, count)).withDefaultValue(0)
              tagMap(firstChar) = map
            } else {
              val map = tagMap(firstChar)
              map(tag) = map(tag) + count
            }
          }
        }
        files.getTags(user).foreach { case (tag: String, count: Long) =>
          if (tag.length == 0) {
            Logger.error("tag with length 0 : " + tag + " " + count)
          } else {
            val firstChar = if (tag(0).isLetter) tag(0).toUpper else '#'
            if (!tagMap.contains(firstChar)) {
              val map = collection.mutable.Map[String, Long]((tag, count)).withDefaultValue(0)
              tagMap(firstChar) = map
            } else {
              val map = tagMap(firstChar)
              map(tag) = map(tag) + count
            }
          }
        }
        sections.getTags(user).foreach { case (tag: String, count: Long) =>
          if (tag.length == 0) {
            Logger.error("tag with length 0 : " + tag + " " + count)
          } else {
            val firstChar = if (tag(0).isLetter) tag(0).toUpper else '#'
            if (!tagMap.contains(firstChar)) {
              val map = collection.mutable.Map[String, Long]((tag, count)).withDefaultValue(0)
              tagMap(firstChar) = map
            } else {
              val map = tagMap(firstChar)
              map(tag) = map(tag) + count
            }
          }
        }
      }
    }

    tagMap.map{ case (k, v) => (k, v.toMap)}.toMap
  }

  def computeTagWeights(user: Option[User]) = {
    val weightedTags = collection.mutable.Map.empty[String, Long].withDefaultValue(0)

    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) if plugin.isEnabled() => {
        for ((k, v) <- plugin.listTags("dataset")) {
          weightedTags(k) = weightedTags(k) + v * current.configuration.getInt("tags.weight.dataset").getOrElse(1)
        }
        for ((k, v) <- plugin.listTags("file")) {
          weightedTags(k) = weightedTags(k) + v * current.configuration.getInt("tags.weight.files").getOrElse(1)
        }
        for ((k, v) <- plugin.listTags("section")) {
          weightedTags(k) = weightedTags(k) + v * current.configuration.getInt("tags.weight.sections").getOrElse(1)
        }
      }
      case _ => {
        Logger.error("ElasticSearch plugin could not be reached for tag search; using Mongo instead")
        datasets.getTags(user).foreach { case (tag: String, count: Long) =>
          weightedTags(tag) = weightedTags(tag) + count * current.configuration.getInt("tags.weight.dataset").getOrElse(1)
        }

        files.getTags(user).foreach { case (tag: String, count: Long) =>
          weightedTags(tag) = weightedTags(tag) + count * current.configuration.getInt("tags.weight.files").getOrElse(1)
        }

        sections.getTags(user).foreach { case (tag: String, count: Long) =>
          weightedTags(tag) = weightedTags(tag) + count * current.configuration.getInt("tags.weight.sections").getOrElse(1)
        }
      }
    }

    weightedTags.toList
  }
}
