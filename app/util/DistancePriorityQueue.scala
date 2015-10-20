/**
 *
 */
package util

import org.apache.lucene.util.PriorityQueue
import play.api.libs.json._

/**
 * Distance fixed sized priority queue.
 * 
 * @author Luigi Marini
 *
 */
case class SearchResult(section_id: String, distance: Double, preview_id: Option[String] = None)

object SearchResult {
	implicit val SearchResultFormat = Json.format[SearchResult]
}

class DistancePriorityQueue(maxSize: Int) extends PriorityQueue[SearchResult](maxSize: Int) {
	
	override def lessThan(a: SearchResult, b: SearchResult): Boolean = {
	  if (a.distance > b.distance) {
			return true;
		} else {
			return false;
		}
	}
}