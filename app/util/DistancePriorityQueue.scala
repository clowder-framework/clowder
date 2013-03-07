/**
 *
 */
package util

import org.apache.lucene.util.PriorityQueue

/**
 * Distance fixed sized priority queue.
 * 
 * @author Luigi Marini
 *
 */
case class SearchResult(section_id: String, distance: Double, preview_id: Option[String] = None)

class DistancePriorityQueue(maxSize: Int) extends PriorityQueue[SearchResult] {
	initialize(maxSize)
	
	override def lessThan(a: SearchResult, b: SearchResult): Boolean = {
	  if (a.distance > b.distance) {
			return true;
		} else {
			return false;
		}
	}
}