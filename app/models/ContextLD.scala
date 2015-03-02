package models

import play.api.libs.json.JsValue
import play.api.libs.json.Json

/**
 * Model for Context in Json-ld 
 * @author Smruti Padhy
 * @author Rob Kooper
 */

case class ContextLD (
    id : UUID = UUID.generate,
    contextName: String, //e.g. ncsa.cv.face.jsonld or some user id .jsonld
    context: JsValue
) 





