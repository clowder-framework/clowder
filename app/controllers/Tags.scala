package controllers

import javax.inject.Inject

import api.Permission
import models.User
import play.api.Logger
import play.api.Play.current
import services.{CollectionService, DatasetService, FileService, SectionService}
import util.Parsers

import scala.collection.mutable.ListBuffer

/**
 * Tagging.
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
  def search(tag: String, start: String, size: Integer, mode: String) = AuthenticatedAction { implicit request =>
    implicit val user = request.user

    var nextItems = ListBuffer.empty[AnyRef]
    var prevItems = ListBuffer.empty[AnyRef]
    var tempItems = ListBuffer.empty[models.Dataset]

    // Clean up leading, trailing and multiple contiguous white spaces.
    val tagCleaned = tag.trim().replaceAll("\\s+", " ")

    // get all datasets tagged
    //Modifications to decode HTML entities that were stored in an encoded fashion as part
    //of the datasets names or descriptions
    tempItems ++= datasets.findByTag(tagCleaned, start, size + 1, false)
    for (aDataset <- tempItems) {
      nextItems += Utils.decodeDatasetElements(aDataset)
    }
    tempItems = ListBuffer.empty[models.Dataset]
    if (start != "") {
      tempItems ++= datasets.findByTag(tagCleaned, start, size + 1, true)
      for (aDataset <- tempItems) {
        prevItems += Utils.decodeDatasetElements(aDataset)
      }
    }

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

    //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
    //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar 
    //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14   
    val viewMode: Option[String] = 
    if (mode == null || mode == "") {
      request.cookies.get("view-mode") match {
          case Some(cookie) => Some(cookie.value)
          case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
      }
    } else {
        Some(mode)
    }
      
    //Pass the viewMode into the view            
    Ok(views.html.searchByTag(tag, nextItems.slice(0, size).toList, prev, next, size, viewMode))
  }

  def tagCloud() = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    Ok(views.html.tagCloud(computeTagWeights()))
  }

  def tagListWeighted() = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    val tags = computeTagWeights()
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

  def tagListOrdered() = AuthenticatedAction { implicit request =>
    implicit val user = request.user

    Ok(views.html.tagListChar(createTagList()))
  }

  def createTagList() = {
    val tagMap = collection.mutable.Map.empty[Char, collection.mutable.Map[String, Long]]

    // TODO allow for tags in collections
    //    for(collection <- collections.listCollections(); tag <- collection.tags) {
    //      weightedTags(tag.name) = weightedTags(tag.name) + current.configuration.getInt("tags.weight.collection").getOrElse(1)
    //    }

    datasets.getTags().foreach { case (tag: String, count: Long) =>
      val firstChar = if (tag(0).isLetter) tag(0).toUpper else '#'
      if (!tagMap.contains(firstChar)) {
        val map = collection.mutable.Map[String, Long]((tag, count)).withDefaultValue(0)
        tagMap(firstChar) = map
      } else {
        val map = tagMap(firstChar)
        map(tag) = map(tag) + count
      }
    }
    files.getTags().foreach { case (tag: String, count: Long) =>
      val firstChar = if (tag(0).isLetter) tag(0).toUpper else '#'
      if (!tagMap.contains(firstChar)) {
        val map = collection.mutable.Map[String, Long]((tag, count)).withDefaultValue(0)
        tagMap(firstChar) = map
      } else {
        val map = tagMap(firstChar)
        map(tag) = map(tag) + count
      }
    }
    sections.getTags().foreach { case (tag: String, count: Long) =>
      val firstChar = if (tag(0).isLetter) tag(0).toUpper else '#'
      if (!tagMap.contains(firstChar)) {
        val map = collection.mutable.Map[String, Long]((tag, count)).withDefaultValue(0)
        tagMap(firstChar) = map
      } else {
        val map = tagMap(firstChar)
        map(tag) = map(tag) + count
      }
    }

    tagMap.map{ case (k, v) => (k, v.toMap)}.toMap
  }

  def computeTagWeights() = {
    val weightedTags = collection.mutable.Map.empty[String, Long].withDefaultValue(0)

    // TODO allow for tags in collections
//    for(collection <- collections.listCollections(); tag <- collection.tags) {
//      weightedTags(tag.name) = weightedTags(tag.name) + current.configuration.getInt("tags.weight.collection").getOrElse(1)
//    }

    datasets.getTags().foreach { case (tag: String, count: Long) =>
      weightedTags(tag) = weightedTags(tag) + count * current.configuration.getInt("tags.weight.dataset").getOrElse(1)
    }

    files.getTags().foreach { case (tag: String, count: Long) =>
      weightedTags(tag) = weightedTags(tag) + count * current.configuration.getInt("tags.weight.files").getOrElse(1)
    }

    sections.getTags().foreach { case (tag: String, count: Long) =>
      weightedTags(tag) = weightedTags(tag) + count * current.configuration.getInt("tags.weight.sections").getOrElse(1)
    }

    weightedTags.toList
  }
}
