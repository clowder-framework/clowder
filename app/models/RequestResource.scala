package models

/**
 * Created by yanzhao3 on 7/29/15.
 */
//trait RequestResource extends scala.AnyRef {
//  def id: UUID
//  def name: String = "N/A"
//  def comment: String = "N/A"
//}

case class RequestResource(
id: UUID,
name: String = "N/A",
comment: String = "N/A"){
  override def equals(o: Any) = o match {
    case that: RequestResource => that.id.equals(this.id)
    case _ => false
  }
}