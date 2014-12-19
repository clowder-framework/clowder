package api

import play.api.mvc.Controller
import play.api.libs.json.Json._
import play.api.mvc.Action
import play.api.Logger

/**
 * Documentation about API using swagger.
 * 
 * @author Luigi Marini
 */
object ApiHelp extends Controller {

  def options(path:String) = Action { 
    Logger.info("ApiHelp: preflight request")
    Ok("")
    }
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
			      path: "/datasets.json",
			      description: "Datasets are basic containers of data"
			    },
          {
            path: "/files.json",
            description: "Files include raw bytes and metadata"
          },
          {
            path: "/collections.json",
            description: "Collections are groupings of datasets"
          }
			  ]
			}
    """))
  }
  
}
