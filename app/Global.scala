import com.mongodb.casbah.Imports._
import play.api._
import models._
import se.radley.plugin.salat._
import play.libs.Akka
import services.MongoFileDB

/**
 * Configure application. Create dummy users.
 */
object Global extends GlobalSettings {
  
  override def onStart(app: Application) {
    
    // testing
//    val ncsa = Company(name = "NCSA")
//    Company.save(ncsa)
//    User.save(User(
//        username = "Luigi",
//        password = "1234",
//        friends = Some(List("Gaetano", "Mario")),
//        company = ncsa.id
//    ))
//    
//    Dataset(files_id = List(new ObjectId))
    
        // casbah joda conversions
//    import com.mongodb.casbah.commons.conversions.scala._
//    RegisterJodaTimeConversionHelpers()
//    RegisterConversionHelpers()
    
    
//    import com.mongodb.casbah.commons.conversions.scala.{RegisterConversionHelpers, RegisterJodaTimeConversionHelpers}
//    RegisterConversionHelpers()
//    RegisterJodaTimeConversionHelpers()
    
    
//    if (User.count(DBObject(), Nil, Nil) == 0) {
//      Logger.info("Loading Testdata")
//
//      User.save(User(
//        username = "leon",
//        password = "1234",
//        address = Some(Address("Orebro", "123 45", "Sweden"))
//      ))
//
//      User.save(User(
//        username = "guillaume",
//        password = "1234",
//        address = Some(Address("Paris", "75000", "France"))
//      ))
//    }
  }

  override def onStop(app: Application) {
  }
  
}