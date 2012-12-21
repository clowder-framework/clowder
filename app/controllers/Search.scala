package controllers

import play.api.mvc._
import services.ElasticsearchPlugin
import play.Logger
import scala.collection.JavaConversions.mapAsScalaMap

/**
 * Text search.
 * 
 * @author Luigi Marini
 */
object Search extends Controller{
  
  /**
   * Search results.
   */
  def search(query: String) = Action {
    Logger.debug("Searching for: " + query)
    import play.api.Play.current
    val result = current.plugin[ElasticsearchPlugin].map{_.search("files", query)}
    for (hit <- result.get.hits().hits()) {
      Logger.debug("Search result: " + hit.getExplanation())
      Logger.info("Fields: ")
      for ((key,value) <- mapAsScalaMap(hit.getFields())) {
        Logger.info(value.getName + " = " + value.getValue())
      } 
    }
    Ok(views.html.searchResults(query, result))
  }
  
}