package controllers

import play.api.mvc._
import services.ElasticsearchPlugin
import play.Logger
import scala.collection.JavaConversions.mapAsScalaMap
import services.Services

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
      Services.files.getFile(hit.getId())
    }
    val files = result.get.hits().hits().map(hit => Services.files.getFile(hit.getId()).get)
    Ok(views.html.searchResults(query, files))
  }
  
  def multimediasearch()=Action{
    Logger.debug("Starting multimedia serach interface")
    Ok(views.html.multimediasearch())
    //Ok("Sucessful")
  }
  
  def advanced()=Action{
    Logger.debug("Starting Advanced Search interface");
    Ok(views.html.advancedsearch())
  }
  
 def SearchByText()=TODO 
 //def uploadquery()=TODO
 
 def uploadquery() = Action(parse.multipartFormData) { request =>
  request.body.file("picture").map { picture =>
    import java.io.File
    val filename = picture.filename 
    val contentType = picture.contentType
    picture.ref.moveTo(new File("/tmp/picture"))
    Ok("File uploaded")
  }.getOrElse {
    Redirect(routes.Application.index).flashing(
      "error" -> "Missing file"
    )
  }
}
   /*Action(parse.multipartFormData) { request =>
  request.body.file("picture").map { picture =>
    import java.io.File
    val filename = picture.filename 
    val contentType = picture.contentType
    picture.ref.moveTo(new File("/tmp/picture"))
    Ok("File uploaded")
  }.getOrElse {
    Redirect(routes.Application.index).flashing(
      "error" -> "Missing file"
    )
  }
}*/
}