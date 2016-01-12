package services

import models.{UUID, Template}

/**
  * Created by todd_n on 1/11/16.
  */
trait TemplateService {

  def insert(template : Template) : Option[String]

  def get(id: UUID) : Option[Template]

  def list() : List[Template]

}
