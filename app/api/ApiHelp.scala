/**
 *
 */
package api

import play.api.mvc.Controller
import play.api.libs.json.Json._
import play.api.mvc.Action

/**
 * Documentation about API using swagger.
 * 
 * @author Luigi Marini
 *
 */
object ApiHelp extends Controller {

  def getResources() = Action {
    Ok(toJson("""
		    {
			  apiVersion: "0.2",
			  swaggerVersion: "1.1",
			  basePath: "http://petstore.swagger.wordnik.com/api",
			  apis: [
			    {
			      path: "/datasets.{format}",
			      description: "Operations about datasets"
			    }
			  ]
			}
    """))
  }
  
}