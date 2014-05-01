package services.mongodb

import javax.inject.{Inject, Singleton}
import services.DatasetService
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import com.mongodb.casbah.Imports._
import play.api.Play._
import scala.Some
import com.mongodb.casbah.commons.MongoDBObject
import play.api.Logger
import models.{UUID, Dataset, Selected}
import org.bson.types.ObjectId
import services.SelectionService
import services.mongodb.MongoContext.context

/**
 * Created by lmarini on 4/24/14.
 */
@Singleton
class MongoDBSelectionService @Inject() (datasets: DatasetService)  extends SelectionService {

  def add(dataset: UUID, user: String) {
    Logger.debug("Selecting dataset " + dataset)
    val query = MongoDBObject("user" -> user)
    val update = $addToSet("datasets" -> dataset.stringify)
    val updated = SelectedDAO.dao.collection.findAndModify(
      query = query,
      update = update,
      upsert = true,
      fields = null,
      sort = null,
      remove = false,
      returnNew = true
    )
    Logger.debug("Selected updated " +  updated)
  }

  def remove(dataset: UUID, user: String) {
    Logger.debug("Removing dataset " + dataset + " from user selection " + user)
    val query = MongoDBObject("user" -> user)
    val update = $pull("datasets" -> dataset.stringify)
    SelectedDAO.dao.collection.update(query, update, upsert=true)
  }

  def get(user: String): List[Dataset] = {
    SelectedDAO.findOne(MongoDBObject("user"->user)) match {
      case Some(selected) => selected.datasets.flatMap(x => datasets.get(UUID(x)))
      case None => List.empty
    }
  }
}

object SelectedDAO extends ModelCompanion[Selected, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Selected, ObjectId](collection = x.collection("selected")) {}
  }
}
