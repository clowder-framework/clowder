package controllers

import models.Dataset
import models.FileDAO
import models.SectionDAO
import api.WithPermission
import api.Permission
import services.{FileService, DatasetService}
import javax.inject.Inject

/**
 * Tagging.
 * 
 * @author Luigi Marini
 */
class Tags @Inject()(datasets: DatasetService, files: FileService) extends SecuredController {

  def search(tag: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    // Clean up leading, trailing and multiple contiguous white spaces.
    val tagCleaned = tag.trim().replaceAll("\\s+", " ")
    val taggedDatasets = datasets.findByTag(tagCleaned)
    val taggedFiles    = files.findByTag(tagCleaned)
    val sections = SectionDAO.findByTag(tagCleaned)
    val sectionsWithFiles = for (s <- sections; f <- files.get(s.file_id.toString)) yield (s, f)
    Ok(views.html.searchByTag(tag, taggedDatasets, taggedFiles, sectionsWithFiles))
  }
}
