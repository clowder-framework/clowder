package controllers

import models.Dataset
import models.Tag

import api.WithPermission
import api.Permission
import util.Parsers
import scala.collection.mutable.ListBuffer
import play.api.Logger
import scala.collection.mutable.Map
import services.{SectionService, FileService, DatasetService}
import javax.inject.Inject
import play.api.Logger
import services.{CollectionService, DatasetService, FileService, SectionService}
import play.api.Play.current



/**
 * Tagging.
 * 
 * @author Luigi Marini
 */
class Tags @Inject()(collections: CollectionService, datasets: DatasetService, files: FileService, sections: SectionService) extends SecuredController {

  /**
   * Search for a tag and display the page to the user. This will allow for pagination allowing the user
   * to go forwards and backwards through all tags. The user can specify the number of datasets and files
   * to display.
   *
   * The code will query the datasets, files and sections and combine the lists into a single sorted list
   * and display it to the user.
   */
  def search(tag: String, start: String, size: Integer, mode: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    implicit val user = request.user

    var nextItems = collection.mutable.ListBuffer.empty[AnyRef]
    var prevItems = collection.mutable.ListBuffer.empty[AnyRef]

    // Clean up leading, trailing and multiple contiguous white spaces.
    val tagCleaned = tag.trim().replaceAll("\\s+", " ")

    // get all datasets tagged
    nextItems ++= datasets.findByTag(tagCleaned, start, size + 1, false)
    if (start != "") prevItems ++= datasets.findByTag(tagCleaned, start, size + 1, true)

    // get all files tagged
    nextItems ++= files.findByTag(tagCleaned, start, size + 1, false)
    if (start != "") prevItems ++= files.findByTag(tagCleaned, start, size + 1, true)

    // get all sections tagged
    // TODO this logic should be moved to findByTag in sections.
    val sectionFiles = sections.findByTag(tagCleaned).map{ s => files.get(s.file_id) match {
      case Some(f) => f
    } }
    if (start == "" ) {
      nextItems ++= sectionFiles
      prevItems ++= sectionFiles
    } else {
      val startDate = Parsers.fromISO8601(start)
      nextItems ++= sectionFiles.filter(f => f.uploadDate.compareTo(startDate) <= 0)
      prevItems ++= sectionFiles.filter(f => f.uploadDate.compareTo(startDate) >= 0)
    }

    // order final list
    nextItems = nextItems.sortBy(_ match {
      case d: models.Dataset => d.created
      case f: models.File => f.uploadDate
    }).reverse
    prevItems = prevItems.sortBy(_ match {
      case d: models.Dataset => d.created
      case f: models.File => f.uploadDate
    })

    // check if there are next items
    val next = if (nextItems.size > size) {
      nextItems(size) match {
        case d: models.Dataset => Parsers.toISO8601(d.created)
        case f: models.File => Parsers.toISO8601(f.uploadDate)
      }
    } else ""

    // check if there are prev items
    val prev = if (prevItems.size > 1) {
      prevItems(Math.min(prevItems.length-1, size)) match {
        case d: models.Dataset => Parsers.toISO8601(d.created)
        case f: models.File => Parsers.toISO8601(f.uploadDate)
      }
    } else ""

    // check if there is a prev item

    Ok(views.html.searchByTag(tag, nextItems.slice(0, size).toList, prev, next, size, mode))
  }

  def tagCloud() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowTags)) { implicit request =>
    implicit val user = request.user

    Ok(views.html.tagCloud(computeTagWeights))
  }

  def tagListWeighted() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowTags)) { implicit request =>
    implicit val user = request.user

    val tags = computeTagWeights

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

  def tagListOrdered() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowTags)) { implicit request =>
    implicit val user = request.user

    Ok(views.html.tagListChar(createTagList))
  }

  def createTagList() = {
    val tagMap = collection.mutable.Map.empty[Char, collection.mutable.Map[String, Integer]]

    // TODO allow for tags in collections
    //    for(collection <- collections.listCollections(); tag <- collection.tags) {
    //      weightedTags(tag.name) = weightedTags(tag.name) + current.configuration.getInt("tags.weight.collection").getOrElse(1)
    //    }

    for(dataset <- datasets.listDatasets(); tag <- dataset.tags) {
      var firstChar = if (tag.name(0).isLetter) tag.name(0).toUpper else '#'
      if (!tagMap.contains(firstChar)) tagMap(firstChar) = collection.mutable.Map.empty[String, Integer].withDefaultValue(0)
      val map = tagMap(firstChar)
      map(tag.name) = map(tag.name) + 1
    }

    for(file <- files.listFiles; tag <- file.tags) {
      var firstChar = if (tag.name(0).isLetter) tag.name(0).toUpper else '#'
      if (!tagMap.contains(firstChar)) tagMap(firstChar) = collection.mutable.Map.empty[String, Integer].withDefaultValue(0)
      val map = tagMap(firstChar)
      map(tag.name) = map(tag.name) + 1
    }

    for(section <- sections.listSections; tag <- section.tags) {
      var firstChar = if (tag.name(0).isLetter) tag.name(0).toUpper else '#'
      if (!tagMap.contains(firstChar)) tagMap(firstChar) = collection.mutable.Map.empty[String, Integer].withDefaultValue(0)
      val map = tagMap(firstChar)
      map(tag.name) = map(tag.name) + 1
    }

    tagMap.map{ case (k, v) => (k, v.toMap)}.toMap
  }

  def computeTagWeights() = {
    val weightedTags = collection.mutable.Map.empty[String, Integer].withDefaultValue(0)

    // TODO allow for tags in collections
//    for(collection <- collections.listCollections(); tag <- collection.tags) {
//      weightedTags(tag.name) = weightedTags(tag.name) + current.configuration.getInt("tags.weight.collection").getOrElse(1)
//    }

    for(dataset <- datasets.listDatasets(); tag <- dataset.tags) {
      weightedTags(tag.name) = weightedTags(tag.name) + current.configuration.getInt("tags.weight.dataset").getOrElse(1)
    }

    for(file <- files.listFiles; tag <- file.tags) {
      weightedTags(tag.name) = weightedTags(tag.name) + current.configuration.getInt("tags.weight.files").getOrElse(1)
    }

    for(section <- sections.listSections; tag <- section.tags) {
      weightedTags(tag.name) = weightedTags(tag.name) + current.configuration.getInt("tags.weight.sections").getOrElse(1)
    }

    Logger.debug("thelist: "+ weightedTags.toList.toString)
    weightedTags.toList
  }
}
