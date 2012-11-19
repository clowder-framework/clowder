package controllers

import play.api.mvc._
import models.User
import org.bson.types.ObjectId

/**
 * Manage users.
 * 
 * @author Luigi Marini
 */
object Users extends Controller {
  
  /**
   * List users.
   */
  def list() = Action {
    val users = User.findAll
    Ok(views.html.list(users))
  }

  /**
   * List users by country.
   */
  def listByCountry(country: String) = Action {
    val users = User.findByCountry(country)
    Ok(views.html.list(users))
  }

  /**
   * View user.
   */
  def view(id: ObjectId) = Action {
    User.findOneById(id).map( user =>
      Ok(views.html.user(user))
    ).getOrElse(NotFound)
  }

  /**
   * Create new user.
   */
  def create(username: String) = Action {
    val user = User(
      username = username,
      password = "1234"
    )
    User.save(user)
    Ok(views.html.user(user))
  }
}