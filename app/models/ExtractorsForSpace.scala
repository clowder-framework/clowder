package models

/**
 * Information about extractors assigned to a space. This only represents extractors that were customized for a specific
 * space. For example enabled/disabled. By default we currently show all global extractors on each space and let users
 * enable/disable them if they so wish.
 *
 * TODO: If we wanted to have default parameters for an extractor defined by space, this could be one place to store them.
 */
case class ExtractorsForSpace(
    spaceId:String,
    enabled: List[String] = List.empty[String],
    disabled: List[String] = List.empty[String]
 )
