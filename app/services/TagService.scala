package services

import api.UserRequest
import play.api.libs.json.JsValue
import models.UUID

/**
 * Created by lmarini on 1/17/14.
 */
abstract class TagService {
  def addTagsHelper(obj_type: TagCheckObjType, id: UUID, request: UserRequest[JsValue]): (Boolean, String)
  def removeTagsHelper(obj_type: TagCheckObjType, id: UUID, request: UserRequest[JsValue]): (Boolean, String)
}

// Used in checking error conditions for tags, the checkErrorsForTag(...) method below
abstract class TagCheckObjType
case object TagCheck_File extends TagCheckObjType
case object TagCheck_Dataset extends TagCheckObjType
case object TagCheck_Section extends TagCheckObjType
case object TagCheck_Vocabulary extends TagCheckObjType
