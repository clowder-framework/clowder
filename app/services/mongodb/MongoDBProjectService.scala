package services.mongodb

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import models.{Project => ClowderProject}
import salat.dao.{ModelCompanion, SalatDAO}
import services.mongodb.MongoContext.context
import services.{DI, ProjectService}

/**
 * Use MongoDB to store Projects.
 */
class MongoDBProjectService extends ProjectService {

  override def getAllProjects(): List[String] = {
    Project.dao.find(MongoDBObject()).sort(orderBy = MongoDBObject("name" -> 1)).map(_.name).toList
  }

  override def addNewProject(project: String) = {
    Project.insert(new ClowderProject(project));
  }

}


object Project extends ModelCompanion[ClowderProject, ObjectId] {
  val COLLECTION = "projects"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[ClowderProject, ObjectId](collection = mongos.collection(COLLECTION)) {}
}
