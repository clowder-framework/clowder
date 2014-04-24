package controllers

import api.WithPermission
import api.Permission
import services.{SectionService, FileService, DatasetService}
import javax.inject.Inject

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
}
