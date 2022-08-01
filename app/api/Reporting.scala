package api

import api.Permission._
import play.api.libs.iteratee.Enumerator

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import play.api.Logger
import javax.inject.Inject
import java.util.{Date, TimeZone}

import services._
import models.{Collection, Dataset, File, ProjectSpace, UUID, User, UserStatus, ExtractionJob}

import org.apache.commons.lang3.Range.between
import scala.collection.mutable.{ListBuffer, Map => MutaMap}
import util.Parsers


/**
 * Reporting and downloading of reports.
 */
class Reporting @Inject()(selections: SelectionService,
                          datasets: DatasetService,
                          files: FileService,
                          collections: CollectionService,
                          spaces: SpaceService,
                          users: UserService,
                          extractions: ExtractionService) extends Controller with ApiController {

  val dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"))

  def fileMetrics(since: Option[String], until: Option[String]) = ServerAdminAction { implicit request =>
    Logger.debug("Generating file metrics report")

    val results = files.getIterator(None, since, until)
    var headerRow = true
    val enum = Enumerator.generateM({
      val chunk = if (headerRow) {
        val header = "type,id,name,owner,owner_id,size_kb,uploaded,views,downloads,last_viewed,last_downloaded,location,parent_datasets,parent_collections,parent_spaces,status\n"
        headerRow = false
        Some(header.getBytes("UTF-8"))
      } else {
        scala.concurrent.blocking {
          if (results.hasNext) {
            try {
              Some(_buildFileRow(results.next).getBytes("UTF-8"))
            }
            catch {
              case _ => Some("".getBytes("UTF-8"))
            }
          }
          else None
        }
      }

      Future(chunk)
    })

    Ok.chunked(enum.andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=FileMetrics.csv"
    )
  }

  def datasetMetrics(since: Option[String], until: Option[String]) = ServerAdminAction { implicit request =>
    Logger.debug("Generating dataset metrics report")

    val results = datasets.getIterator(None, since, until)
    var headerRow = true
    val enum = Enumerator.generateM({
      val chunk = if (headerRow) {
        val header = "type,id,name,owner,owner_id,created,views,downloads,last_viewed,last_downloaded,parent_collections,parent_spaces\n"
        headerRow = false
        Some(header.getBytes("UTF-8"))
      } else {
        scala.concurrent.blocking {
          if (results.hasNext) {
            try {
              Some(_buildDatasetRow(results.next).getBytes("UTF-8"))
            }
            catch {
              case _ => Some("".getBytes("UTF-8"))
            }
          }
          else None
        }
      }

      Future(chunk)
    })

    Ok.chunked(enum.andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=DatasetMetrics.csv"
    )
  }

  def collectionMetrics() = ServerAdminAction { implicit request =>
    Logger.debug("Generating collection metrics report")

    val results = collections.getMetrics()
    var headerRow = true
    val enum = Enumerator.generateM({
      val chunk = if (headerRow) {
        val header = "type,id,name,owner,owner_id,created,views,last_viewed,parent_collections,parent_spaces\n"
        headerRow = false
        Some(header.getBytes("UTF-8"))
      } else {
        scala.concurrent.blocking {
          if (results.hasNext) {
            try {
              Some(_buildCollectionRow(results.next).getBytes("UTF-8"))
            }
            catch {
              case _ => Some("".getBytes("UTF-8"))
            }
          }
          else None
        }
      }

      Future(chunk)
    })

    Ok.chunked(enum.andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=CollectionMetrics.csv"
    )
  }

  """Includes file, dataset, collection reports"""
  def allMetrics() = ServerAdminAction { implicit request =>
    Logger.debug("Generating all metrics report")

    // TODO: This will still fail on excessively large instances without Enumerator refactor - should we maintain this endpoint or remove?

    var contents: String = "type,id,name,owner,owner_id,size_kb,uploaded/created,views,downloads,last_viewed,last_downloaded,location,parent_datasets,parent_collections,parent_spaces,status\n"

    collections.getMetrics().foreach(coll => {
      contents += _buildCollectionRow(coll, true)
    })
    datasets.getIterator(None, None, None).foreach(ds => {
      contents += _buildDatasetRow(ds, true)
    })
    files.getIterator(None, None, None).foreach(f => {
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
      contents += "\""+sp.name.replace("\"", "\"\"")+"\","
      contents += "\""+sp.description.replace("\"", "\"\"")+"\","
      contents += "\""+creator_id+"\","
      contents += dateFormat.format(sp.created)+","
      contents += sp.datasetCount.toString+","
      contents += sp.collectionCount.toString+","
      contents += sp.userCount.toString
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

    // Hard-code Anonymous user for now
    contents += "\"user\",\"000000000000000000000000\",\"Anonymous User\",\"\",\"\",,,,,,\n"

    users.list(limit=0).foreach(u => {
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
        case None => contents += ",,"
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

  private def _buildFileRow(f: File): String = {
    var contents = ""

    // Parent datasets, collections & spaces are sublists within the columns
    val parent_datasets = datasets.findByFileIdAllContain(f.id)
    var ds_list = ""
    var coll_list = ""
    var space_list = ""
    var i = 1
    var j = 1
    var k = 1
    parent_datasets.foreach(ds => {
      ds_list += (if (i>1) ", " else "") + ds.id
      ds.collections.foreach(coll => {
        if (!coll_list.contains(coll.uuid)) {
          coll_list += (if (j>1) ", " else "") + coll.uuid
          j += 1
        }
      })
      ds.spaces.foreach(sp => {
        if (!space_list.contains(sp.uuid)) {
          space_list += (if (k>1) ", " else "") + sp.uuid
          k += 1
        }
      })
      i += 1
    })

    // Get stats if they exist, otherwise use default values
    val vwcount = if (f.stats == null) "0" else f.stats.views.toString
    val lvstr = if (f.stats == null) "" else {
      f.stats.last_viewed match {
        case Some(lvdate) => dateFormat.format(lvdate)
        case None => ""
      }
    }
    val dlcount = if (f.stats == null) "0" else f.stats.downloads.toString
    val ldstr = if (f.stats == null) "" else {
      f.stats.last_downloaded match {
        case Some(lddate) => dateFormat.format(lddate)
        case None => ""
      }
    }

    contents += "\"file\","
    contents += "\""+f.id.toString+"\","
    contents += "\""+f.filename+"\","
    contents += "\""+f.author.fullName+"\","
    contents += "\""+f.author.id+"\","
    contents += (f.length/1000).toInt.toString+","
    contents += dateFormat.format(f.uploadDate)+","
    contents += vwcount+","
    contents += dlcount+","
    contents += lvstr+","
    contents += ldstr+","
    contents += "\""+f.loader_id+"\","
    contents += "\""+ds_list+"\","
    contents += "\""+coll_list+"\","
    contents += "\""+space_list+"\","
    contents += "\""+f.status+"\""
    contents += "\n"

    return contents
  }

  private def _buildDatasetRow(ds: Dataset, returnAllColums: Boolean = false): String = {
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

    // Get stats if they exist, otherwise use default values
    val vwcount = if (ds.stats == null) "0" else ds.stats.views.toString
    val lvstr = if (ds.stats == null) "" else {
      ds.stats.last_viewed match {
        case Some(lvdate) => dateFormat.format(lvdate)
        case None => ""
      }
    }
    val dlcount = if (ds.stats == null) "0" else ds.stats.downloads.toString
    val ldstr = if (ds.stats == null) "" else {
      ds.stats.last_downloaded match {
        case Some(lddate) => dateFormat.format(lddate)
        case None => ""
      }
    }

    contents += "\"dataset\","
    contents += "\""+ds.id.toString+"\","
    contents += "\""+ds.name.replace("\"", "\"\"")+"\","
    contents += "\""+ds.author.fullName+"\","
    contents += "\""+ds.author.id+"\","
    if (returnAllColums) contents += "," // datasets do not have size
    contents += dateFormat.format(ds.created)+","
    contents += vwcount+","
    contents += dlcount+","
    contents += lvstr+","
    contents += ldstr+","
    if (returnAllColums) contents += "," // datasets do not have location
    if (returnAllColums) contents += "," // datasets do not have parent_datasets
    contents += "\""+coll_list+"\","
    contents += "\""+space_list+"\""
    if (returnAllColums) contents += "," // datasets do not have status
    contents += "\n"

    return contents
  }

  private def _buildCollectionRow(coll: Collection, returnAllColums: Boolean = false): String = {
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

    // Get stats if they exist, otherwise use default values
    val vwcount = if (coll.stats == null) "0" else coll.stats.views.toString
    val lvstr = if (coll.stats == null) "" else {
      coll.stats.last_viewed match {
        case Some(lvdate) => dateFormat.format(lvdate)
        case None => ""
      }
    }

    contents += "\"collection\","
    contents += "\""+coll.id.toString+"\","
    contents += "\""+coll.name.replace("\"", "\"\"")+"\","
    contents += "\""+coll.author.fullName+"\","
    contents += "\""+coll.author.id+"\","
    if (returnAllColums) contents += "," // collections do not have size
    contents += dateFormat.format(coll.created)+","
    contents += vwcount+","
    if (returnAllColums) contents += "," // collections do not have downloads
    contents += lvstr+","
    if (returnAllColums) contents += "," // collections do not have last_downloaded
    if (returnAllColums) contents += "," // collections do not have location
    if (returnAllColums) contents += "," // collections do not have parent_datasets
    contents += "\""+coll_list+"\","
    contents += "\""+space_list+"\""
    if (returnAllColums) contents += "," // collections do not have status
    contents += "\n"

    return contents
  }

  def spaceStorage(space: Option[String], since: Option[String], until: Option[String]) = ServerAdminAction { implicit request =>
    // Iterate over the files of every dataset in the space
    val results = datasets.getIterator(space, None, None) // TODO: Can't use time filters here if user intends files

    var headerRow = true
    val enum = Enumerator.generateM({
      val chunk = if (headerRow) {
        val header = "file_type,id,name,owner,owner_email,owner_id,size_kb,uploaded,location,parent_datasets,parent_collections,parent_spaces,space_owners,space_admins\n"
        headerRow = false
        Some(header.getBytes("UTF-8"))
      } else {
        scala.concurrent.blocking {
          if (results.hasNext) {
            try {
              val ds = results.next

              // Each file in the dataset inherits same parent info from dataset
              val ds_list = ds.id.stringify
              var coll_list = ""
              var space_list = ""
              val space_ids: ListBuffer[UUID] = ListBuffer.empty
              var j = 1
              var k = 1
              ds.collections.foreach(coll => {
                if (!coll_list.contains(coll.uuid)) {
                  coll_list += (if (j>1) ", " else "") + coll.uuid
                  j += 1
                }
              })
              ds.spaces.foreach(sp => {
                if (!space_list.contains(sp.uuid)) {
                  space_list += (if (k>1) ", " else "") + sp.uuid
                  space_ids += sp
                  k += 1
                }
              })

              // Get admin and owner of space(s)
              // TODO: Should we include email and/or name too?
              var space_owner_list = ""
              var space_admin_list = ""
              var l = 1
              var m = 1
              spaces.get(space_ids.toList).found.foreach(sp => {
                space_owner_list += (if (l>1) ", " else "") + sp.creator.uuid
                l += 1
                spaces.getUsersInSpace(sp.id, Some("Admin")).foreach(spadmin => {
                  space_admin_list += (if (m>1) ", " else "") + spadmin.id.uuid
                  m += 1
                })
              })

              var contents = ""
              files.get(ds.files).found.foreach(f => {
                // TODO: Need to redesign File model because this is gonna be so slow...
                val sinceOK = {
                  since match {
                    case None => true
                    case Some(t) => (Parsers.fromISO8601(t).before(f.uploadDate))
                  }
                }
                val untilOK = {
                  until match {
                    case None => true
                    case Some(t) => (Parsers.fromISO8601(t).after(f.uploadDate))
                  }
                }

                if (sinceOK && untilOK) {
                  // build next row of storage report
                  contents += "\""+f.contentType+"\","
                  contents += "\""+f.id.toString+"\","
                  contents += "\""+f.filename+"\","
                  contents += "\""+f.author.fullName+"\","
                  contents += "\""+f.author.email.getOrElse("")+"\","
                  contents += "\""+f.author.id+"\","
                  contents += (f.length/1000).toInt.toString+","
                  contents += dateFormat.format(f.uploadDate)+","
                  contents += "\""+f.loader_id+"\","
                  contents += "\""+ds_list+"\","
                  contents += "\""+coll_list+"\","
                  contents += "\""+space_list+"\","
                  contents += "\""+space_owner_list+"\","
                  contents += "\""+space_admin_list+"\""
                  contents += "\n"
                }
              })
              // Submit all file rows for this dataset at once
              Some(contents.getBytes("UTF-8"))
            }
            catch {
              case _ => Some("".getBytes("UTF-8"))
            }
          }
          else None
        }
      }

      Future(chunk)
    })

    val filename = space match {
      case Some(spid) => "SpaceStorage_"+spid+".csv"
      case None => "SpaceStorage.csv"
    }
    Ok.chunked(enum.andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> ("attachment; filename="+filename)
    )
  }

  private def determineJobType(jobMsg: String): String = {
    if (jobMsg == "SUBMITTED")
      "queue"
    else
      "work" // TODO: Better solution?
  }

  def extractorUsage(since: Option[String], until: Option[String]) = ServerAdminAction { implicit request =>
    Logger.debug("Generating extraction metrics report")

    /** This mapping is used to aggregate jobs.
     * A job is considered some countable extraction duration. It has a jobType so
     * we can attempt to differentiate "time in queue" from "time being processed".
     *
     * jobLookup: [
     *  UserID -> [
     *    UniqueJobKey -> {
     *      jobs: [ list of jobs identical to current_job below ]
     *      current_job: {
     *        target      event.file_id (but can be a dataset ID or metadata ID in reality)
     *        targetType  file/dataset/metadata
     *        extractor   extractor id (e.g. ncsa.file.digest)
     *        spaceId     id of space containing target
     *        jobId       official job_id, if available
     *        jobType     is this a queue event or an actual work event on a node? see determineJobType()
     *        lastStatus  most recent event.status for the job
     *        start       earliest event.start time from events in this job (event.end is often blank)
     *        end         latest event.start time from events in this job (event.end is often blank)
     *
     *      }
     *    }
     */
    val jobLookup: MutaMap[UUID,
      MutaMap[String, (List[ExtractionJob], Option[ExtractionJob])]] = MutaMap.empty

    val results = extractions.getIterator(true, since, until, None)
    while (results.hasNext) {
      val event = results.next

      // Collect info to associate this event with a job if possible
      val jobId = event.job_id match {
        case Some(jid) => jid.stringify
        case None => ""
      }
      val jobType = determineJobType(event.status)
      val uniqueKey = event.file_id + " - " + event.extractor_id

      // Add user and uniqueKey if they don't exist yet
      if (!jobLookup.get(event.user_id).isDefined)
        jobLookup(event.user_id) = MutaMap.empty
      if (!jobLookup.get(event.user_id).get.get(uniqueKey).isDefined)
        jobLookup(event.user_id)(uniqueKey) = (List.empty, None)

      // If we don't have an ongoing job, or it's not same jobType, start a new ongoing job
      var jobList    = jobLookup(event.user_id)(uniqueKey)._1
      val currentJob = jobLookup(event.user_id)(uniqueKey)._2
      val newJobBeginning = currentJob match {
        case Some(currJob) => currJob.jobType != jobType
        case None => true
      }

      if (newJobBeginning) {
        // Determine parent details for new job - quick dataset check first, then file search
        var spaces = ""
        var resourceType = "file"
        val parentDatasets = datasets.findByFileIdAllContain(event.file_id)
        if (parentDatasets.length > 0) {
          parentDatasets.foreach(ds => {
            spaces = ds.spaces.mkString(",")
            resourceType = "file"
          })
        } else {
          datasets.get(event.file_id) match {
            case Some(ds) => {
              spaces = ds.spaces.mkString(",")
              resourceType = "dataset"
            }
            case None => {}
          }
        }

        // Push current job to jobs list (saying it ended at start of next stage) and make new job entry
        if (currentJob.isDefined) {
          jobList = jobList ::: List(currentJob.get.copy(end=event.start))
        }
        val newJob = ExtractionJob(event.file_id.stringify, resourceType, event.extractor_id, spaces, jobId, jobType, 1,
          event.status, event.start, event.start)
        jobLookup(event.user_id)(uniqueKey) = (jobList, Some(newJob))
      } else {
        // Don't overwrite DONE as final message in case we have small differences in timing of last extractor msg
        var status = currentJob.get.lastStatus
        if (status != "DONE") status = event.status
        val updatedJob = currentJob.get.copy(statusCount=currentJob.get.statusCount+1, lastStatus=event.status, end=event.start)
        jobLookup(event.user_id)(uniqueKey) = (jobList, Some(updatedJob))
      }
    }

    var headerRow = true
    val keyiter = jobLookup.keysIterator
    val enum = Enumerator.generateM({
      val chunk = if (headerRow) {
        val headers = List("userid", "username", "email", "resource_id", "resource_type", "space_id", "extractor",
          "job_id", "job_type", "status_count", "last_status", "start", "end", "duration_ms")
        val header = "\""+headers.mkString("\",\"")+"\"\n"
        headerRow = false
        Some(header.getBytes("UTF-8"))
      } else {
        scala.concurrent.blocking {
          if (keyiter.hasNext) {
            val userid = keyiter.next

            // Get pretty user info
            var username = ""
            var email = ""
            users.get(userid) match {
              case Some(u) => {
                username = u.fullName
                email = u.email.getOrElse("")
              }
              case None => {}
            }

            var content = ""
            val userRecords = jobLookup(userid)
            userRecords.keysIterator.foreach(jobkey => {
              val jobHistory = userRecords(jobkey)
              val jobList = jobHistory._1
              val currJob = jobHistory._2
              jobList.foreach(job => {
                val duration = (job.end.getTime - job.start.getTime)
                val row = List(userid.stringify, username, email, job.target, job.targetType, job.spaces, job.extractor,
                  job.jobId, job.jobType, job.statusCount, job.lastStatus, job.start, job.end, duration)
                if (duration > 0)
                  content += "\""+row.mkString("\",\"")+"\"\n"
              })
              // current job if it was never "closed" and pushed to the jobList (most common case)
              currJob match {
                case Some(job) => {
                  val duration = (job.end.getTime - job.start.getTime)
                  val row = List(userid.stringify, username, email, job.target, job.targetType, job.spaces, job.extractor,
                    job.jobId, job.jobType, job.statusCount, job.lastStatus, job.start, job.end, duration)
                  if (duration > 0)
                    content += "\""+row.mkString("\",\"")+"\"\n"
                }
                case None => {}
              }
            })
            Some(content.getBytes("UTF-8"))
          }
          else None
        }
      }
      Future(chunk)
    })

    Ok.chunked(enum.andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=ExtractorMetrics.csv"
    )
  }
}
