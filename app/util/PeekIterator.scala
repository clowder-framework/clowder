package util

/**
 * Iterator that allows to look at the next
 */
class PeekIterator[T](parent: Iterator[T]) extends Iterator[T] {
  var nextElement: Option[T] = None

  def hasNext(): Boolean = nextElement.isDefined || parent.hasNext

  def next(): T = {
    nextElement match {
      case Some(x) => {
        nextElement = None
        x
      }
      case None => parent.next
    }
  }

  def peek(): Option[T] = {
    nextElement match {
      case Some(x) => Some(x)
      case None => {
        if (parent.hasNext) {
          nextElement = Some(parent.next)
          nextElement
        } else {
          None
        }
      }
    }
  }
}
