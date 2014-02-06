/**
 *
 */
package api

import play.api.mvc.Controller
import play.api.Play.current
import services.PostgresPlugin

/**
 * Metadata about sensors registered with the system. Datastreams can be associalted with sensors.
 * 
 * @author Luigi Marini
 *
 */
object Sensors extends Controller with ApiController {

  def add() = SecuredAction(authorization=WithPermission(Permission.CreateSensors)) { request =>
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          Ok("")
        }
        case None => {
          Ok("")
         }
      }
  }
  
  def get(id: String) = SecuredAction(authorization=WithPermission(Permission.GetSensors)) { request =>
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          Ok("")
        }
        case None => {
          Ok("")
         }
      }
  }
  
  def list() = SecuredAction(authorization=WithPermission(Permission.ListSensors)) { request =>
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
  
  def search() = SecuredAction(authorization=WithPermission(Permission.SearchSensors)) { request =>
      current.plugin[PostgresPlugin] match {
        case Some(plugin) => {
          Ok("")
        }
        case None => {
          Ok("")
         }
      }
  }
  
  def delete(id: String) = SecuredAction(authorization=WithPermission(Permission.RemoveSensors)) { request =>
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