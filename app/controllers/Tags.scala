package controllers

import api.Permission
import api.WithPermission
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

  def search(tag: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    // Clean up leading, trailing and multiple contiguous white spaces.
    val tagCleaned = tag.trim().replaceAll("\\s+", " ")
    val taggedDatasets = datasets.findByTag(tagCleaned)
    val taggedFiles    = files.findByTag(tagCleaned)
    val sectionsByTag = sections.findByTag(tagCleaned)
    val sectionsWithFiles = for (s <- sectionsByTag; f <- files.get(s.file_id)) yield (s, f)
    Ok(views.html.searchByTag(tag, taggedDatasets, taggedFiles, sectionsWithFiles))
  }

  def tagCloud() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowTags)) { implicit request =>
    implicit val user = request.user

    Ok(views.html.tagCloud(computeTagWeights))
  }

  def tagList() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowTags)) { implicit request =>
    implicit val user = request.user

    Ok(views.html.tagList(computeTagWeights))
  }

  def computeTagWeights() = {
    val weightedTags = collection.mutable.Map.empty[String, Integer].withDefaultValue(0)

    // TODO allow for tags in collections
//    for(collection <- collections.listCollections(); tag <- collection.tags) {
//      weightedTags(tag.name) = weightedTags(tag.name) + current.configuration.getInt("tags.weight.collection").getOrElse(1)
//    }

    for(dataset <- datasets.listDatasets; tag <- dataset.tags) {
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
