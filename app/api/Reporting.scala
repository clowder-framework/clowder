package api

import api.Permission._
import play.api.libs.iteratee.Enumerator
import play.api.mvc.Controller
import play.api.Logger
import javax.inject.Inject
import java.util.{TimeZone, Date}
import services._
import models.{File, Dataset, Collection, ProjectSpace, User, UserStatus}


/**
 * Reporting and downloading of reports.
 */
class Reporting @Inject()(selections: SelectionService,
                          datasets: DatasetService,
                          files: FileService,
                          collections: CollectionService,
                          spaces: SpaceService,
                          users: UserService) extends Controller with ApiController {

  val dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"))

  def fileMetrics() = ServerAdminAction { implicit request =>
    Logger.debug("Generating file metrics report")

    var contents: String = "type,id,name,owner,owner_id,size_kb,uploaded,views,downloads,last_viewed,last_downloaded,location,parent_datasets,parent_collections,parent_spaces\n"

    files.getMetrics(None).foreach(f => {
      contents += _buildFileRow(f)
    })

    Ok.chunked(Enumerator(contents.getBytes("UTF-8")).andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=FileMetrics.csv"
    )
  }

  def datasetMetrics() = ServerAdminAction { implicit request =>
    Logger.debug("Generating dataset metrics report")

    var contents: String = "type,id,name,owner,owner_id,created,views,downloads,last_viewed,last_downloaded,parent_collections,parent_spaces\n"

    datasets.getMetrics(None).foreach(ds => {
      contents += _buildDatasetRow(ds)
    })

    Ok.chunked(Enumerator(contents.getBytes("UTF-8")).andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=DatasetMetrics.csv"
    )
  }

  def collectionMetrics() = ServerAdminAction { implicit request =>
    Logger.debug("Generating collection metrics report")

    var contents: String = "type,id,name,owner,owner_id,created,views,last_viewed,parent_collections,parent_spaces\n"

    collections.getMetrics(None).foreach(coll => {
      contents += _buildCollectionRow(coll)
    })

    Ok.chunked(Enumerator(contents.getBytes("UTF-8")).andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=CollectionMetrics.csv"
    )
  }

  """Includes file, dataset, collection reports"""
  def allMetrics() = ServerAdminAction { implicit request =>
    Logger.debug("Generating all metrics report")

    var contents: String = "type,id,name,owner,owner_id,size_kb,uploaded/created,views,downloads,last_viewed,last_downloaded,location,parent_datasets,parent_collections,parent_spaces\n"

    collections.getMetrics(None).foreach(coll => {
      contents += _buildCollectionRow(coll, true)
    })
    datasets.getMetrics(None).foreach(ds => {
      contents += _buildDatasetRow(ds, true)
    })
    files.getMetrics(None).foreach(f => {
      contents += _buildFileRow(f)
    })

    Ok.chunked(Enumerator(contents.getBytes("UTF-8")).andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=AllMetrics.csv"
    )
  }

  def spaceMetrics() = ServerAdminAction { implicit request =>
    Logger.debug("Generating space metrics report")

    var contents: String = "type,id,name,description,creator_id,created,datasets,collections,users\n"

    spaces.listAccess(0, Set[Permission](Permission.ViewSpace), request.user, true, false, false, false).foreach(sp => {
      val creator_id = users.get(sp.creator) match {
        case Some(u) => u.getMiniUser.id
        case None => ""
      }

      contents += "\"space\","
      contents += "\""+sp.id.toString+"\","
      contents += "\""+sp.name+"\","
      contents += "\""+sp.description+"\","
      contents += "\""+creator_id+"\","
      contents += dateFormat.format(sp.created)+","
      contents += sp.datasetCount.toString+","
      contents += sp.collectionCount.toString+","
      contents += sp.userCount.toString+","
      contents += "\n"
    })

    Ok.chunked(Enumerator(contents.getBytes("UTF-8")).andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=SpaceMetrics.csv"
    )
  }

  def userMetrics() = ServerAdminAction { implicit request =>
    Logger.debug("Generating user metrics report")

    var contents: String = "type,id,name,email,provider,last_login,days_since_last_login,active,admin,admin_spaces,member_spaces\n"

    users.list().foreach(u => {
      // Get owned and member space counts
      var admin_spaces = 0
      var member_spaces = 0
      u.spaceandrole.foreach(sar => {
        if (sar.role.name == "Admin")
          admin_spaces += 1
        else // Editor or Viewer
          member_spaces += 1
      })

      contents += "\"user\","
      contents += "\""+u.id.toString+"\","
      contents += "\""+u.getMiniUser.fullName+"\","
      contents += "\""+u.email.getOrElse("")+"\","
      contents += "\""+u.identityId.providerId+"\","
      u.lastLogin match {
        case Some(lastdate) => {
          contents += dateFormat.format(lastdate)+","
          val currdate = new Date
          val difference =  (currdate.getTime()-currdate.getTime())/86400000
          contents += Math.abs(difference).toString+","
        }
        case None => ",,"
      }
      contents += (if (u.status==UserStatus.Inactive) "false" else "true")+","
      contents += (if (u.status==UserStatus.Admin) "true" else "false")+","
      contents += admin_spaces.toString+","
      contents += member_spaces.toString
      contents += "\n"
    })

    Ok.chunked(Enumerator(contents.getBytes("UTF-8")).andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=UserMetrics.csv"
    )
  }

  def _buildFileRow(f: File): String = {
    var contents = ""

    // Parent datasets, collections & spaces are sublists within the columns
    val parent_datasets = datasets.findByFileIdAllContain(f.id)
    var ds_list = ""
    var coll_list = ""
    var space_list = ""
    var i = 1
    parent_datasets.foreach(ds => {
      ds_list += (if (i>1) ", " else "") + ds.id
      var j = 1
      ds.collections.foreach(coll => {
        coll_list += (if (j>1) ", " else "") + coll.uuid
        j += 1
      })
      var k = 1
      ds.spaces.foreach(sp => {
        space_list += (if (k>1) ", " else "") + sp.uuid
        k += 1
      })
      i += 1
    })

    contents += "\"file\","
    contents += "\""+f.id.toString+"\","
    contents += "\""+f.filename+"\","
    contents += "\""+f.author.fullName+"\","
    contents += "\""+f.author.id+"\","
    contents += (f.length/1000).toInt.toString+","
    contents += dateFormat.format(f.uploadDate)+","
    contents += f.stats.views.toString+","
    contents += f.stats.downloads.toString+","
    val lvstr = f.stats.last_viewed match {
      case Some(lvdate) => dateFormat.format(lvdate)
      case None => ""
    }
    contents += "\""+lvstr+"\","
    val ldstr = f.stats.last_downloaded match {
      case Some(lddate) => dateFormat.format(lddate)
      case None => ""
    }
    contents += "\""+ldstr+"\","
    contents += "\""+f.loader_id+"\","
    contents += "\""+ds_list+"\","
    contents += "\""+coll_list+"\","
    contents += "\""+space_list+"\""
    contents += "\n"

    return contents
  }

  def _buildDatasetRow(ds: Dataset, returnAllColums: Boolean = false): String = {
    """returnAllColumns will include empty columns to align with file rows on report"""
    var contents = ""

    // Parent collections & spaces are sublists within the columns
    var coll_list = ""
    var space_list = ""
    var j = 1
    ds.collections.foreach(coll => {
      coll_list += (if (j>1) ", " else "") + coll.uuid
      j += 1
    })
    var k = 1
    ds.spaces.foreach(sp => {
      space_list += (if (k>1) ", " else "") + sp.uuid
      k += 1
    })

    contents += "\"dataset\","
    contents += "\""+ds.id.toString+"\","
    contents += "\""+ds.name+"\","
    contents += "\""+ds.author.fullName+"\","
    contents += "\""+ds.author.id+"\","
    if (returnAllColums) contents += "," // datasets do not have size
    contents += dateFormat.format(ds.created)+","
    contents += ds.stats.views.toString+","
    contents += ds.stats.downloads.toString+","
    val lvstr = ds.stats.last_viewed match {
      case Some(lvdate) => dateFormat.format(lvdate)
      case None => ""
    }
    contents += "\""+lvstr+"\","
    val ldstr = ds.stats.last_downloaded match {
      case Some(lddate) => dateFormat.format(lddate)
      case None => ""
    }
    contents += "\""+ldstr+"\","
    if (returnAllColums) contents += "," // datasets do not have location
    if (returnAllColums) contents += "," // datasets do not have parent_datasets
    contents += "\""+coll_list+"\","
    contents += "\""+space_list+"\""
    contents += "\n"

    return contents
  }

  def _buildCollectionRow(coll: Collection, returnAllColums: Boolean = false): String = {
    """returnAllColumns will include empty columns to align with file rows on report"""

    var contents = ""

    // Parent collections & spaces are sublists within the columns
    var coll_list = ""
    var space_list = ""
    var j = 1
    coll.parent_collection_ids.foreach(par => {
      coll_list += (if (j>1) ", " else "") + par.uuid
      j += 1
    })
    var k = 1
    coll.spaces.foreach(sp => {
      space_list += (if (k>1) ", " else "") + sp.uuid
      k += 1
    })

    contents += "\"collection\","
    contents += "\""+coll.id.toString+"\","
    contents += "\""+coll.name+"\","
    contents += "\""+coll.author.fullName+"\","
    contents += "\""+coll.author.id+"\","
    if (returnAllColums) contents += "," // collections do not have size
    contents += dateFormat.format(coll.created)+","
    contents += coll.stats.views.toString+","
    if (returnAllColums) contents += "," // collections do not have downloads
    val lvstr = coll.stats.last_viewed match {
        case Some(lvdate) => dateFormat.format(lvdate)
        case None => ""
      }
    contents += "\""+lvstr+"\","
    if (returnAllColums) contents += "," // collections do not have last_downloaded
    if (returnAllColums) contents += "," // collections do not have location
    if (returnAllColums) contents += "," // collections do not have parent_datasets
    contents += "\""+coll_list+"\","
    contents += "\""+space_list+"\""
    contents += "\n"

    return contents
  }

}
