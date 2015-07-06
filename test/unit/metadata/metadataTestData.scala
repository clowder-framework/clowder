package unit.metadata

import models.{ResourceRef, UserAgent, Metadata, UUID}
import java.util.Date
import play.api.libs.json.JsObject
import java.net.URL


trait metadataTestData {
  val testCreator = UserAgent(id= UUID.generate, typeOfAgent="cat:user", userId = Some(new URL("http://dts.ncsa.illinois.edu/user06")))
  val testMetadata = Metadata(
    id = UUID.generate,
    attachedTo = ResourceRef(ResourceRef.file, UUID.generate),
    createdAt = new Date,
    creator = testCreator,
    content = new JsObject(Seq()))
}