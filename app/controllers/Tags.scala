package controllers


import models.Dataset
import models.Tag

import api.WithPermission
import api.Permission
import scala.collection.mutable.ListBuffer
import play.api.Logger
import scala.collection.mutable.Map
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
  
  def tagCloud() = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ShowTags)) { implicit request =>
    
      implicit val user = request.user	
	  var weightedTags: Map[String, Integer] = Map()
      
      for(dataset <- datasets.listDatasets){
        for(tag <- dataset.tags){
          var tagName = tag.name
          if(weightedTags.contains(tagName))
        	  weightedTags(tagName) = weightedTags(tagName) + 4
          else
              weightedTags += ((tagName, 4))
        }
      }
      for(file <- files.listFiles){
        for(tag <- file.tags){
          var tagName = tag.name
          if(weightedTags.contains(tagName))
        	  weightedTags(tagName) = weightedTags(tagName) + 2
          else
              weightedTags += ((tagName, 2))
        }
      }
      for(section <- sections.listSections){
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
