package models

/**
 * A simple structure to assist in handling paging through a list of resources.
 * `prev` and `next` contain the ID of the previous and next resources (respectively).
 *
 * Currently this class is used for paging through files in a dataset, but could
 * be used to handle paging for any list of arbitrary UUIDs / resources in other contexts.
 *
 * See:
 * {@link views.files},
 * {@link controllers.Files#file}
 *
 * @param prev UUID of the previous resource in the list
 * @param next UUID of the next resource in the list
 *
 *     @author lambert8
 */
case class Pager(
  prev: Option[models.UUID],
  next: Option[models.UUID]
)
