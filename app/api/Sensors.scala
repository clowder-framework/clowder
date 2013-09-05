/**
 *
 */
package api

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.Play.current
import services.PostgresPlugin

/**
 * Metadata about sensors registered with the system. Datastreams can be associalted with sensors.
 * 
 * @author Luigi Marini
 *
 */
object Sensors extends Controller {

  def add() = Authenticated {
    Action(parse.json) { request =>
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          Ok("")
        }
        case None => {
          Ok("")
         }
      }
    }
  }
  
  def get(id: String) = Authenticated {
    Action(parse.json) { request =>
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          Ok("")
        }
        case None => {
          Ok("")
         }
      }
    }
  }
  
  def list() = Authenticated {
    Action(parse.json) { request =>
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          val sensors = plugin.listSensors()
          Ok("")
        }
        case None => {
          Ok("")
         }
      }
    }
  }
  
  def search() = Authenticated {
    Action(parse.json) { request =>
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          Ok("")
        }
        case None => {
          Ok("")
         }
      }
    }
  }
  
  def delete(id: String) = Authenticated {
    Action(parse.json) { request =>
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          Ok("")
        }
        case None => {
          Ok("")
         }
      }
    }
  }
  
}