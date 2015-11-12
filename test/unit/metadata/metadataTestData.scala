package unit.metadata

import models._
import java.util.Date
import play.api.libs.json.JsObject
import java.net.URL


trait metadataTestData {
  val id = UUID.generate
  val testCreator = UserAgent(id= UUID.generate, typeOfAgent="cat:user", MiniUser(id, "Test User", "", None),
    userId = Some(new URL("http://dts.ncsa.illinois.edu/user06")))
  val testMetadata = Metadata(
    id = UUID.generate,
    attachedTo = ResourceRef(ResourceRef.file, UUID.generate),
    createdAt = new Date,
    creator = testCreator,
    content = new JsObject(Seq()))
}