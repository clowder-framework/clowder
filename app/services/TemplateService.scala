package services

import models.{UUID, Template}

import scala.util.Try

/**
  * Created by todd_n on 1/11/16.
  */
trait TemplateService {

  def count() : Long

  def insert(template : Template) : Option[String]

  def get(id: UUID) : Option[Template]

  def delete(id: UUID) : Try[Unit]

  def list() : List[Template]

}
