package controllers

import models.{ExtractorInfo, Folder, ResourceRef, UUID, Extraction}
import play.api.mvc.Controller
import api.Permission
import javax.inject.{Inject, Singleton}
import play.api.Logger
import services._

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer
import java.util.{Calendar, Date}
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.Calendar

/**
 * Information about extractors.
 */
@Singleton
class Extractors  @Inject() (extractions: ExtractionService,
                             extractorService: ExtractorService,
                             fileService: FileService,
                             datasetService: DatasetService,
                             folders: FolderService,
                             spaces: SpaceService,
                             logService: LogService,
                             datasets: DatasetService ) extends Controller with SecuredController {

  def listAllExtractions = ServerAdminAction { implicit request =>
    implicit val user = request.user
    val allExtractions = extractions.findAll()
    Ok(views.html.listAllExtractions(allExtractions))
  }

  /**
   * Gets a map of all updates from all jobs given to this extractor.
   */
  def showJobHistory(extractorName: String) = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    extractorService.getExtractorInfo(extractorName) match {
      case None => NotFound(s"No extractor found with name=${extractorName}")
      case Some(info) => {
        val allExtractions = extractions.findAll()
        val groups = extractions.groupByType(allExtractions)
        Ok(views.html.extractorJobHistory(info, groups(extractorName)))
      }
    }
  }

  /**
    * Gets list of extractors from mongo. Displays the page to enable/disable extractors.
    */
  def selectExtractors() = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    // Filter extractors by the trigger type if necessary
    var runningExtractors: List[ExtractorInfo] = extractorService.listExtractorsInfo(List.empty)
    request.getQueryString("processTriggerSearchFilter") match {
      case Some("file/*") => runningExtractors = runningExtractors.filter(re => re.process.file.length > 0)
      case Some("dataset/*") => runningExtractors = runningExtractors.filter(re => re.process.dataset.length > 0)
      case Some("metadata/*") => runningExtractors = runningExtractors.filter(re => re.process.metadata.length > 0)
      case None => {}
    }
    val selectedExtractors: List[String] = extractorService.getEnabledExtractors()
    val groups = extractions.groupByType(extractions.findAll())
    val labels = extractorService.listExtractorsLabels()
    Ok(views.html.updateExtractors(runningExtractors, selectedExtractors, groups, labels))
  }

  def manageLabels = ServerAdminAction { implicit request =>
    implicit val user = request.user
    val categories = List[String]("EXTRACT")
    val extractors = extractorService.listExtractorsInfo(categories)
    val labels = extractorService.listExtractorsLabels()

    Ok(views.html.extractorLabels(labels, extractors))
  }

  /**
    * Processes POST request. Updates list of extractors associated with this instance in mongo.
    */
  def updateExtractors() = AuthenticatedAction(parse.multipartFormData) { implicit request =>
    implicit val user = request.user

    // Bounce any non-admin users back to the homepage
    // TODO: is there a better pattern for this? I couldn't access "user" when specifying PermissionAction() above
    user match {
      case Some(u) => {
        if (!u.superAdminMode && !Permission.checkServerAdmin(user)) {
          Redirect(routes.Application.index())
        }
      }
      case None => Redirect(routes.Application.index())
    }

    //form contains space id and list of extractors.
    var extractors: List[String] = Nil

    extractorService.disableAllExtractors()

    val dataParts = request.body.dataParts
    // if extractors are selected, add them
    if (dataParts.isDefinedAt("extractors")) {
      extractors = dataParts("extractors").toList
      extractors.map(extractorService.enableExtractor(_))
    }
    Redirect(routes.Application.index())
  }

  def showExtractorLog(extractorName: String) = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    val logs = logService.getLog(extractorName, None)
    Ok(views.html.extractorLog(extractorName, logs, logs.size))
  }

  def showExtractorInfo(extractorName: String) = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    val targetExtractor = extractorService.listExtractorsInfo(List.empty).find(p => p.name == extractorName)
    targetExtractor match {
      case Some(extractor) => {
        val labels = extractorService.getLabelsForExtractor(extractor.name)
        Ok(views.html.extractorDetails(extractor, labels))
      }
      case None => InternalServerError("Extractor not found: " + extractorName)
    }
  }

  def showExtractorMetrics(extractorName: String) = AuthenticatedAction { implicit request =>
    implicit val user = request.user

    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
    val todaydate = dateFormatter.format(new java.util.Date())

    val hourcal = Calendar.getInstance
    hourcal.add(Calendar.HOUR, -24)
    val yesterdaydate = dateFormatter.format(hourcal.getTime)

    val daycal = Calendar.getInstance
    daycal.add(Calendar.WEEK_OF_MONTH, -1)
    val last7daydate = dateFormatter.format(daycal.getTime)

    val monthcal = Calendar.getInstance
    monthcal.add(Calendar.MONTH, -1)
    val lastmonthdate = dateFormatter.format(monthcal.getTime)

    Logger.debug("today date: " + todaydate)
    Logger.debug("yesterday date: " + yesterdaydate)
    Logger.debug("last 7 day date: " + last7daydate)
    Logger.debug("last month date: " + lastmonthdate)

    // get at most 10 ``DONE'' submission from yesterday.
    val myDonelist = extractions.findByExtractorIDBefore(extractorName, "DONE", yesterdaydate, 10)
    // get at most 10 ``SUBMITTED'' submission from yesterday.
    val mySubmittedlist = extractions.findByExtractorIDBefore(extractorName, "SUBMITTED", yesterdaydate, 10)
    var n: Long = 0
    var sum: Long = 0
    // calculate the diff time between ``SUBMITTED'' and ``DONE''.
    for ((done, submitted) <- (myDonelist zip mySubmittedlist)) {
      Logger.debug("my done date: " + done.start + ", fileid: " + done.file_id)
      Logger.debug("my submitted date: " + submitted.start + ", fileid: " + submitted.file_id)
      Logger.debug("done.start.getTime: " + done.start.getTime + ", submitted.start.getTime: " + submitted.start.getTime)
      val diffInMillies = Math.abs(done.start.getTime - submitted.start.getTime)
      Logger.debug("diffInMillies in ms: " + diffInMillies)
      sum = sum + diffInMillies
      n = n+1
    }
    sum = TimeUnit.SECONDS.convert(sum, TimeUnit.MILLISECONDS)
    var average = BigDecimal.valueOf(sum)
    Logger.debug("average: " + average)
    if(n > 0) {
      average = average/n
    }
    Logger.debug("average: " + average)
    // get at the number of ``SUBMITTED'' submission in last week.
    val lastweeksubmitted = extractions.findByExtractorIDBefore(extractorName, "SUBMITTED", last7daydate, 0)
    Logger.debug("lastweek submitted: " + lastweeksubmitted.size)
    // get at the number of ``SUBMITTED'' submission in last month.
    val lastmonthsubmitted = extractions.findByExtractorIDBefore(extractorName, "SUBMITTED", lastmonthdate, 0)

    // calculate the last 10 execution average time in the time range of last month between ``SUBMITTED'' and ``DONE''.
    // get at most 10 ``DONE'' submission from last month.
    val myLastTenDonelist = extractions.findByExtractorIDBefore(extractorName, "DONE", lastmonthdate, 10)
    // get at most 10 ``SUBMITTED'' submission from last month.
    val myLastTenSubmittedlist = extractions.findByExtractorIDBefore(extractorName, "SUBMITTED", lastmonthdate, 10)
    n = 0
    sum = 0
    for ((done, submitted) <- (myLastTenDonelist zip myLastTenSubmittedlist)) {
      Logger.debug("my last 10 done date: " + done.start + ", fileid: " + done.file_id)
      Logger.debug("my last 10 submitted date: " + submitted.start + ", fileid: " + submitted.file_id)
      Logger.debug("last 10 done.start.getTime: " + done.start.getTime + ", submitted.start.getTime: " + submitted.start.getTime)
      val diffInMillies = Math.abs(done.start.getTime - submitted.start.getTime)
      Logger.warn("last 10 diffInMillies in ms: " + diffInMillies)
      sum = sum + diffInMillies
      n = n+1
    }
    sum = TimeUnit.SECONDS.convert(sum, TimeUnit.MILLISECONDS)
    var lastTenAverage = BigDecimal.valueOf(sum)
    Logger.debug("last 10 average: " + lastTenAverage)
    if(n > 0) {
      lastTenAverage = lastTenAverage/n
    }
    Logger.warn("last 10 average: " + lastTenAverage)

    val targetExtractor = extractorService.listExtractorsInfo(List.empty).find(p => p.name == extractorName)
    targetExtractor match {
      case Some(extractor) => Ok(views.html.extractorMetrics(extractorName, average.toString, lastTenAverage.toString, lastweeksubmitted.size, lastmonthsubmitted.size))
      case None => InternalServerError("Extractor Info not found: " + extractorName)
    }
  }

  def submitFileExtraction(file_id: UUID) = PermissionAction(Permission.EditFile, Some(ResourceRef(ResourceRef.file, file_id))) { implicit request =>
    implicit val user = request.user
    val all_extractors = extractorService.listExtractorsInfo(List("EXTRACT", "CONVERT"))
    val extractors = all_extractors.filter(!_.process.file.isEmpty)
    fileService.get(file_id) match {

      case Some(file) => {
        val foldersContainingFile = folders.findByFileId(file.id).sortBy(_.name)
        var folderHierarchy = new ListBuffer[Folder]()
        if(foldersContainingFile.length > 0) {
          folderHierarchy = folderHierarchy ++ foldersContainingFile
          var f1: Folder = folderHierarchy.head
          while (f1.parentType == "folder") {
            folders.get(f1.parentId) match {
              case Some(fparent) => {
                folderHierarchy += fparent
                f1 = fparent
              }
              case None =>
            }
          }
        }
        val datasetsContainingFile = datasets.findByFileIdDirectlyContain(file.id).sortBy(_.name)
        val allDatasets =  datasets.get(folders.findByFileId(file.id).map(_.parentDatasetId)).found ++ datasetsContainingFile
        val allDecodedDatasets = ListBuffer.empty[models.Dataset]
        val decodedSpacesContaining= ListBuffer.empty[models.ProjectSpace]
        for (aDataset <- allDatasets) {
          val dDataset = Utils.decodeDatasetElements(aDataset)
          allDecodedDatasets += dDataset
          aDataset.spaces.map{ sp =>
            spaces.get(sp) match {
              case Some(s) => {
                decodedSpacesContaining += Utils.decodeSpaceElements(s)
              }
              case None =>
            }
          }
        }
        Ok(views.html.extractions.submitFileExtraction(extractors, file, folderHierarchy.reverse.toList, decodedSpacesContaining.toList, allDecodedDatasets.toList))
      }
      case None => InternalServerError("File not found")
    }
  }

  def submitDatasetExtraction(ds_id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, ds_id))) { implicit request =>
    implicit val user = request.user
    val all_extractors = extractorService.listExtractorsInfo(List("EXTRACT", "CONVERT"))
    val extractors = all_extractors.filter(!_.process.dataset.isEmpty)
    datasetService.get(ds_id) match {
      case Some(ds) => Ok(views.html.extractions.submitDatasetExtraction(extractors, ds))
      case None => InternalServerError("Dataset not found")
    }
  }
}