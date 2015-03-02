package services

import models.UUID
import models.ContextLD
import play.api.libs.json.JsValue

/**
 * Context service for add, query and delete Json-ld contexts
 * @author Smruti Padhy
 */

trait ContextLDService {

  /** Add context for metadata **/
  def addContext(contextName: String, contextld: JsValue): UUID

  /** Get context  **/
  def getContextById(id: UUID): Option[JsValue]

  /** Get context by name **/
  def getContextByName(contextName: String): Option[JsValue]

  /** Remove context **/
  def removeContext(id: UUID)

  /** Update context **/
  def updateContext(context: ContextLD)

}