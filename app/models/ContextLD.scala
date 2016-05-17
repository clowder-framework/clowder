package models

import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsString


/**
 * Model for Context in Json-ld
 */

case class ContextLD(
  id: UUID = UUID.generate,
  contextName: JsString, //e.g. ncsa.cv.face.jsonld or some user id .jsonld
  context: JsValue) 





