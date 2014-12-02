package controllers
import javax.inject.Inject

import api.{Permission, WithPermission}
import play.api.Routes
import play.api.mvc.Action
import play.api.mvc.Controller
import api.Sections
import models.AppAppearance
import javax.inject.{Singleton, Inject}
import play.api.mvc.Action
import services.FileService
import play.api.Logger
import services.UserService
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.Form
import play.api.data.Forms._
import models.{UUID, Collection, Info, User}
import java.util.Date
import play.api.Logger
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import api.WithPermission
import api.Permission
import play.api.Play.current
import javax.inject.{Singleton, Inject}
import services._


class Profile @Inject()(users: UserService) extends  SecuredController {

	

val bioForm = Form(
  mapping(
    "biography" -> optional(text),
    "personal" -> optional(text),
    "number" -> optional(text),
    "building" -> optional(text),
    "room" -> optional(text),
    "currentprojects" -> optional(text),
    "pastprojects"-> optional(text)
  )(Info.apply)(Info.unapply)
)
/**
case class Info(biography: String, personal: String)

val bioForm = Form(
	mapping(
  "biography" -> nonEmptyText,
  "personal"->nonEmptyText
  )(Info.apply, Info.unapply)
)*/
 def editProfile() = SecuredAction() {
    implicit request =>
      implicit val user = request.user
      var biog: Option[String] = None
	  var person: Option[String] = None
	  var num: Option[String] = None
	  var build: Option[String] = None
	  var room: Option[String] = None
	  var cp: Option[String] = None
	  var pp: Option[String] = None
      user match {
	        case Some(x) => {
	        	print(x.email.toString())
	        	implicit val email = x.email
	        	email match {
	        		case Some(addr) => {
	        			implicit val modeluser = users.findByEmail(addr.toString())
	        			modeluser match {
	        				
	            			case Some(muser) => {

		            			muser.biography match {
		            				case Some(filledOut) => biog = Option(filledOut)
		            				case None => biog = None
		            			}
		            			muser.personal match {
		            				case Some(filledOut) => person = Option(filledOut)
		            				case None => person = None
		            			}
		            			muser.number match {
		            				case Some(filledOut) => num = Option(filledOut)
		            				case None => num = None
		            			}
		            			muser.building match {
		            				case Some(filledOut) => build = Option(filledOut)
		            				case None => build = None
		            			}
		            			muser.room match {
		            				case Some(filledOut) => room = Option(filledOut)
		            				case None => room = None
		            			}
		            			muser.currentprojects match {
		            				case Some(filledOut) => cp = Option(filledOut)
		            				case None => cp = None
		            			}
		            			muser.pastprojects match {
		            				case Some(filledOut) => pp = Option(filledOut)
		            				case None => pp = None
		            			}

	            			val newbioForm = bioForm.fill(Info(biog, person, num, build, room, cp, pp))
	            			  Ok(views.html.editProfile(newbioForm))

	            		}
	            		case None => {
	            			Ok("NOT WORKS")
	            		}
	           		 }
	        		}
	        	}
	            
	      }
	        case None => {
	        	Ok("NOT WORKING")
	        }
		}	
      
  }


  def addFriend(email: String) = SecuredAction() { request =>
  	implicit val user = request.user
  	user match {
	        case Some(x) => {
            	implicit val myemail = x.email
            	myemail match {
            		case Some(addr) => {
                  implicit val modeluser = users.findByEmail(addr.toString())
                  implicit val otherUser = users.findByEmail(email)
                  modeluser match {
                    case Some(muser) => {
                       muser.friends match {
                        case Some(viewList) =>{
                          users.editList(addr.toString(), "friends", email)
                          otherUser match {
                          	case Some(other) => {
                          		Ok(views.html.profilepage(other))
                          	}
                          }
                          
                        }
                        case None => {
                          val newList: List[String] = List(email)
                          users.createList(addr.toString(), "friends", newList)
                          otherUser match {
                          	case Some(other) => {
                          		Ok(views.html.profilepage(other))
                          	}
                          }
                        }
                      }
                  }
                 }
           }}
       }
   }
  }
  
	  def view = SecuredAction() { request =>
	  	implicit val user = request.user
	  	user match {
	        case Some(x) => {
	        	implicit val email = x.email
	        	email match {
	        		case Some(addr) => {
	        			implicit val modeluser = users.findByEmail(addr.toString())
	        			modeluser match {
	            			case Some(muser) => {
	            			//users.editField(addr.toString(), "RANDOMKSDJLFD")
	            			Ok(views.html.profilepage(muser))
	            		}
	            		case None => {
	            			Ok("NOT WORKS")
	            		}
	           		 }
	        		}
	        	}
	            
	      }
	        case None => {
	        	Ok("NOT WORKING")
	        }
		}	
	  }  

	  def viewProfile(email: Option[String]) = SecuredAction() { request =>
	  			implicit val user = request.user
	        	email match {
	        		case Some(addr) => {
	        			implicit val modeluser = users.findByEmail(addr.toString())
	        			modeluser match {
	            			case Some(muser) => {
	            			Ok(views.html.profilepage(muser))
	            		}
	            		case None => {
	            			Ok("NOT WORKS")
	            		}
	           		 }
	        		}
	        	}
	            
	      }
def submitChanges = SecuredAction() {  implicit request =>
	implicit val user  = request.user
  bioForm.bindFromRequest.fold(
    errors => BadRequest(views.html.editProfile(errors)),
    biography => {
      //users.editField(addr.toString(), biography)
      //Redirect(routes.Profile.view)
      user match {
	        case Some(x) => {
	        	print(x.email.toString())
	        	implicit val email = x.email
	        	email match {
	        		case Some(addr) => {
	        			implicit val modeluser = users.findByEmail(addr.toString())
	        			modeluser match {
	            			case Some(muser) => {
	            			users.editField(addr.toString(), "biography", biography.bio)
	            			users.editField(addr.toString(), "personal", biography.personal)
	            			users.editField(addr.toString(), "number", biography.number)
	            			users.editField(addr.toString(), "building", biography.building)
	            			users.editField(addr.toString(), "room", biography.room)
	            			users.editField(addr.toString(), "currentprojects", biography.currentprojects)
	            			users.editField(addr.toString(), "pastprojects", biography.pastprojects)

	            			Redirect(routes.Profile.view)
	            		}
	            		case None => {
	            			Ok("NOT WORKS")
	            		}
	           		 }
	        		}
	        	}
	            
	      }
	        case None => {
	        	Ok("NOT WORKING")
	        }
		}	
    }
  )
}

}


