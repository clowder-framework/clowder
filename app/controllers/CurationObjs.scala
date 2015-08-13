package controllers

import java.net.URL
import java.util.Date
import javax.inject.Inject

import api.Permission
import models._
import play.api.data.{Forms, Form}
import play.api.data.Forms._

import scala.text

/**
 * Created by yanzhao3 on 8/12/15.
 */
class CurationObjs @Inject()() extends SecuredController {


  /**
   * New curation object form bindings.
   */
  val curationForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText,
      "logoUrl" -> optional(Utils.CustomMappings.urlType),
      "bannerUrl" -> optional(Utils.CustomMappings.urlType),
      "homePages" -> Forms.list(Utils.CustomMappings.urlType),
      "space_id" -> optional(Utils.CustomMappings.uuidType),
      "editTime" -> longNumber,
      "isTimeToLiveEnabled" -> boolean,
      "submitValue" -> text
    )
      (
          (name, description, logoUrl, bannerUrl, homePages, space_id, editTime, isTimeToLiveEnabled, bvalue) => spaceFormData(name = name, description = description,
            homePage = homePages, logoURL = logoUrl, bannerURL = bannerUrl, space_id, resourceTimeToLive = editTime, isTimeToLiveEnabled = isTimeToLiveEnabled, bvalue)
        )
      (
          (d:spaceFormData) => Some(d.name, d.description, d.logoURL, d.bannerURL, d.homePage, d.spaceId, d.resourceTimeToLive, d.isTimeToLiveEnabled, d.submitButtonValue)
        )
  )

  def newSpace() = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    Ok(views.html.curations.newCuration(curationForm))
  }

}
