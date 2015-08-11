package models

/**
 * Contains information about an authorization request
 *
 * @author Yan Zhao
 */

case class RequestResource(
id: UUID,
name: String = "N/A",
comment: String = "N/A"){
  override def equals(o: Any) = o match {
    case that: RequestResource => that.id.equals(this.id)
    case _ => false
  }
}