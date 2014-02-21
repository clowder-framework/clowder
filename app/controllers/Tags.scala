package controllers

import models.Dataset
import models.FileDAO
import models.SectionDAO
import models.Tag
import api.WithPermission
import api.Permission
import scala.collection.mutable.ListBuffer
import play.api.Logger
import scala.collection.mutable.Map

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
  
  def tagCloud() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowTags)) { implicit request =>
    
      implicit val user = request.user	
	  var weightedTags: Map[String, Integer] = Map()
      
      for(dataset <- Dataset.findAll){
        for(tag <- dataset.tags){
          var tagName = tag.name
          if(weightedTags.contains(tagName))
        	  weightedTags(tagName) = weightedTags(tagName) + 4
          else
              weightedTags += ((tagName, 4))
        }
      }
      for(file <- FileDAO.findAll){
        for(tag <- file.tags){
          var tagName = tag.name
          if(weightedTags.contains(tagName))
        	  weightedTags(tagName) = weightedTags(tagName) + 2
          else
              weightedTags += ((tagName, 2))
        }
      }
      for(section <- SectionDAO.findAll){
        for(tag <- section.tags){
          var tagName = tag.name
          if(weightedTags.contains(tagName))
        	  weightedTags(tagName) = weightedTags(tagName) + 1
          else
              weightedTags += ((tagName, 1))
        }
      }
      
      Logger.debug("thelist: "+ weightedTags.toList.toString)

      Ok(views.html.tagCloud(weightedTags.toList))
  }
  
}
