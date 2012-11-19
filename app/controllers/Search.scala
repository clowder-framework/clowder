package controllers

import play.api.mvc._

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
    Ok(views.html.searchResults(query))
  }
  
}