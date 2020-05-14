package models

case class Pager(
  prev: Option[models.UUID],
  next: Option[models.UUID]
)
