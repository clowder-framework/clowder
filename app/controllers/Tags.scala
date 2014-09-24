package controllers

import scala.collection.mutable.Map

import api.Permission
import api.WithPermission
import javax.inject.Inject
import play.api.Logger
import services.DatasetService
import services.FileService
import services.SectionService



/**
 * Tagging.
 * 
 * @author Luigi Marini
 */
class Tags @Inject()(datasets: DatasetService, files: FileService, sections: SectionService) extends SecuredController {

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

	  val weightedTags = collection.mutable.Map.empty[String, Integer].withDefaultValue(0)

    for(dataset <- datasets.listDatasets; tag <- dataset.tags) {
      weightedTags(tag.name) = weightedTags(tag.name) + 4
    }

    for(file <- files.listFiles; tag <- file.tags) {
      weightedTags(tag.name) = weightedTags(tag.name) + 2
    }

    for(section <- sections.listSections; tag <- section.tags) {
      weightedTags(tag.name) = weightedTags(tag.name) + 1
    }

    Logger.debug("thelist: "+ weightedTags.toList.toString)

    Ok(views.html.tagCloud(weightedTags.toList))
  }  
}
