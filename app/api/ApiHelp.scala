package api

import play.api.mvc.Controller
import play.api.libs.json.Json._
import play.api.mvc.Action

/**
 * Documentation about API using swagger.
 * 
 * @author Luigi Marini
 */
object ApiHelp extends Controller {

  /**
   * Used as entry point by swagger.
   */
  def getResources() = Action {
    Ok(toJson("""
		    {
			  apiVersion: "0.1",
			  swaggerVersion: "1.1",
			  basePath: "http://localhost:9000/api",
			  apis: [
			    {
			      path: "/datasets.{format}",
			      description: "Datasets are basic containers of data"
			    }
			  ]
			}
    """))
  }
  
}