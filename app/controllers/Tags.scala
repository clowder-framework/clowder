package controllers

import models.Dataset
import models.FileDAO
import models.SectionDAO
import api.WithPermission
import api.Permission
/**
 * Tagging.
 * 
 * @author Luigi Marini
 */
object Tags extends SecuredController {

  def search(tag: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    // Clean up leading, trailing and multiple contiguous white spaces.
    val tagCleaned = tag.trim().replaceAll("\\s+", " ")
    val datasets = Dataset.findByTag(tagCleaned)
    val files    = FileDAO.findByTag(tagCleaned)
    val sections = SectionDAO.findByTag(tagCleaned)
    val sectionsWithFiles = for (s <- sections; f <- FileDAO.get(s.file_id.toString)) yield (s, f)
    Ok(views.html.searchByTag(tag, datasets, files, sectionsWithFiles))
  }
}
