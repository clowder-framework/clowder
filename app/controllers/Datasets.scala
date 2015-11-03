package controllers

import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

import api.Permission
import api.Permission.Permission
import fileutils.FilesUtils
import models._
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.Json._
import services._
import util.{Formatters, RequiredFieldsConfig}

import scala.collection.immutable._
import scala.collection.mutable.ListBuffer


/**
 * A dataset is a collection of files and streams.
 */
class Datasets @Inject()(
  datasets: DatasetService,
  files: FileService,
  collections: CollectionService,
  comments: CommentService,
  sections: SectionService,
  extractions: ExtractionService,
  dtsrequests:ExtractionRequestsService,
  sparql: RdfSPARQLService,
  users: UserService,
  previewService: PreviewService,
  relations: RelationService,
  spaceService: SpaceService) extends SecuredController {

  object ActivityFound extends Exception {}

  /**
   * Display the page that allows users to create new datasets, either by uploading multiple new files,
   * or by selecting multiple existing files.
   */
  def newDataset(space: Option[String]) = PermissionAction(Permission.CreateDataset) { implicit request =>
      implicit val user = request.user
      val spacesList = user.get.spaceandrole.map(_.spaceId).flatMap(spaceService.get(_))
      var decodedSpaceList = new ListBuffer[models.ProjectSpace]()
      for (aSpace <- spacesList) {
        //For each space in the list, check if the user has permission to add something to it, if so
        //decode it and add it to the list to pass back to the view.
        if (Permission.checkPermission(Permission.AddResourceToSpace, ResourceRef(ResourceRef.space, aSpace.id))) {
          decodedSpaceList += Utils.decodeSpaceElements(aSpace)
        }
      }
    space match {
      case Some(s) => {
        spaceService.get(UUID(s)) match {
          case Some(spaceId) => Ok(views.html.newDataset(decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, Some(s))).flashing("error" -> "Please select ONE file (upload new or existing)")
          case None => Ok(views.html.newDataset(decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, None)).flashing("error" -> "Please select ONE file (upload new or existing)")
        }

      }
      case None => Ok(views.html.newDataset(decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, None)).flashing("error" -> "Please select ONE file (upload new or existing)")
    }
  }


  def addToDataset(id: UUID, name: String, desc: String) = PermissionAction(Permission.CreateDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
      implicit val user = request.user
      Ok(views.html.addToExistingDataset(id, name, desc)).flashing("error" -> "Cannot add to the dataset")
  }

  /**
   * List datasets.
   */
  def list(when: String, date: String, limit: Int, space: Option[String], mode: String, owner: Option[String]) = PrivateServerAction { implicit request =>
    implicit val user = request.user

    val nextPage = (when == "a")
    val person = owner.flatMap(o => users.get(UUID(o)))

    val datasetList = person match {
      case Some(p) => {
        if (date != "") {
          datasets.listUser(date, nextPage, limit, request.user, request.superAdmin, p)
        } else {
          datasets.listUser(limit, request.user, request.superAdmin, p)
        }
      }
      case None => {
        space match {
          case Some(s) => {
            if (date != "") {
              datasets.listSpace(date, nextPage, limit, s)
            } else {
              datasets.listSpace(limit, s)
            }
          }
          case None => {
            if (date != "") {
              datasets.listAccess(date, nextPage, limit, Set[Permission](Permission.ViewDataset), request.user, request.superAdmin)
            } else {
              datasets.listAccess(limit, Set[Permission](Permission.ViewDataset), request.user, request.superAdmin)
            }

          }
        }
      }
    }

    // check to see if there is a prev page
    val prev = if (datasetList.nonEmpty && date != "") {
      val first = Formatters.iso8601(datasetList.head.created)
      val ds = person match {
        case Some(p) => datasets.listUser(first, nextPage=false, 1, request.user, request.superAdmin, p)
        case None => {
          space match {
            case Some(s) => datasets.listSpace(first, nextPage = false, 1, s)
            case None => datasets.listAccess(first, nextPage = false, 1, Set[Permission](Permission.ViewDataset), request.user, request.superAdmin)
          }
        }
      }
      if (ds.nonEmpty && ds.head.id != datasetList.head.id) {
        first
      } else {
        ""
      }
    } else {
      ""
    }

    // check to see if there is a next page
    val next = if (datasetList.nonEmpty) {
      val last = Formatters.iso8601(datasetList.last.created)
      val ds = person match {
        case Some(p) => datasets.listUser(last, nextPage=true, 1, request.user, request.superAdmin, p)
        case None => {
          space match {
            case Some(s) => datasets.listSpace(last, nextPage=true, 1, s)
            case None => datasets.listAccess(last, nextPage=true, 1, Set[Permission](Permission.ViewDataset), request.user, request.superAdmin)
          }
        }
      }
      if (ds.nonEmpty && ds.head.id != datasetList.last.id) {
        last
      } else {
        ""
      }
    } else {
      ""
    }

    val commentMap = datasetList.map { dataset =>
      var allComments = comments.findCommentsByDatasetId(dataset.id)
      dataset.files.map { file =>
        allComments ++= comments.findCommentsByFileId(file)
        sections.findByFileId(file).map { section =>
          allComments ++= comments.findCommentsBySectionId(section.id)
        }
      }
      dataset.id -> allComments.size
    }.toMap

    //Modifications to decode HTML entities that were stored in an encoded fashion as part
    //of the datasets names or descriptions
    val decodedDatasetList = ListBuffer.empty[models.Dataset]
    for (aDataset <- datasetList) {
      decodedDatasetList += Utils.decodeDatasetElements(aDataset)
    }

    //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
    //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar
    //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14
    val viewMode: Option[String] =
      if (mode == null || mode == "") {
        request.cookies.get("view-mode") match {
          case Some(cookie) => Some(cookie.value)
          case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
        }
      } else {
        Some(mode)
      }

    //Pass the viewMode into the view
    Ok(views.html.datasetList(decodedDatasetList.toList, commentMap, prev, next, limit, viewMode, space))
  }

  def addViewer(id: UUID, user: Option[securesocial.core.Identity]) = {
      user match{
            case Some(viewer) => {
              implicit val email = viewer.email
              email match {
                case Some(addr) => {
                  implicit val modeluser = users.findByEmail(addr.toString())
                  modeluser match {
                    case Some(muser) => {
                       muser.viewed match {
                        case Some(viewList) =>{
                          users.addUserDatasetView(addr, id)
                        }
                        case None => {
                          val newList: List[UUID] = List(id)
                          users.createNewListInUser(addr, "viewed", newList)
                        }
                      }
                  }
                  case None => {
                    Ok("NOT WORKS")
                  }
                 }
                }
              }
            }


          }
  }

  /**
   * Dataset.
   */
  def dataset(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>

      implicit val user = request.user
      Previewers.findPreviewers.foreach(p => Logger.debug("Previewer found " + p.id))
      datasets.get(id) match {
        case Some(dataset) => {

          // get files info sorted by date
          val filesInDataset = dataset.files.map(f => files.get(f) match {
            case Some(file) => file
            case None => Logger.debug(s"Unable to find file $f")
          }).asInstanceOf[List[File]].sortBy(_.uploadDate)

          var datasetWithFiles = dataset.copy(files = filesInDataset.map(_.id))
          datasetWithFiles = Utils.decodeDatasetElements(datasetWithFiles)

          val filteredPreviewers = Previewers.findDatasetPreviewers

          val metadata = datasets.getMetadata(id)
          Logger.debug("Metadata: " + metadata)
          for (md <- metadata) {
              Logger.debug(md.toString)
          }
          val userMetadata = datasets.getUserMetadata(id)
          Logger.debug("User metadata: " + userMetadata.toString)

          val collectionsInside = collections.listInsideDataset(id, request.user, request.superAdmin).sortBy(_.name)
          var decodedCollectionsInside = new ListBuffer[models.Collection]()
          var filesTags = TreeSet.empty[String]

          for (aCollection <- collectionsInside) {
              val dCollection = Utils.decodeCollectionElements(aCollection)
              decodedCollectionsInside += dCollection
          }

          var commentsByDataset = comments.findCommentsByDatasetId(id)
          filesInDataset.map {
              file =>

              commentsByDataset ++= comments.findCommentsByFileId(file.id)
              sections.findByFileId(UUID(file.id.toString)).map { section =>
                commentsByDataset ++= comments.findCommentsBySectionId(section.id)
              }
          }
          commentsByDataset = commentsByDataset.sortBy(_.posted)

          //Decode the comments so that their free text will display correctly in the view
          var decodedCommentsByDataset = ListBuffer.empty[Comment]
          for (aComment <- commentsByDataset) {
            val dComment = Utils.decodeCommentElements(aComment)
            decodedCommentsByDataset += dComment
          }

          val isRDFExportEnabled = current.plugin[RDFExportService].isDefined


          filesInDataset.map
          {
            file =>
              file.tags.map {
                tag => filesTags += tag.name
              }
          }

          // associated sensors
          val sensors: List[(String, String)]= current.plugin[PostgresPlugin] match {
            case Some(db) => {
              val base = play.api.Play.configuration.getString("geostream.dashboard.url").getOrElse("http://localhost:9000")
              val ids = relations.findTargets(id.stringify, ResourceType.dataset, ResourceType.sensor)
              db.getDashboardSensorURLs(ids)
            }
            case None => List.empty[(String, String)]
          }

          var datasetSpaces: List[ProjectSpace]= List.empty[ProjectSpace]
          dataset.spaces.map{
                  sp => spaceService.get(sp) match {
                    case Some(s) => {
                      datasetSpaces = s :: datasetSpaces
                    }
                    case None => Logger.error(s"space with id $sp on dataset $id doesn't exist.")
                  }
          }

          val decodedSpaces: List[ProjectSpace] = datasetSpaces.map{aSpace => Utils.decodeSpaceElements(aSpace)}
          val fileList: List[File] = dataset.files.map(fileId => files.get(fileId) match {
            case Some(file) => file
            case None => Logger.debug(s"Unable to find file $fileId")
          }).asInstanceOf[List[File]]

          Ok(views.html.dataset(datasetWithFiles, commentsByDataset, filesTags, filteredPreviewers.toList, metadata, userMetadata,
            decodedCollectionsInside.toList, isRDFExportEnabled, sensors, Some(decodedSpaces), fileList))

        }
        case None => {
          Logger.error("Error getting dataset" + id)
          InternalServerError
        }
    }
  }

  /**
   * Dataset by section.
   */
  def datasetBySection(section_id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.section, section_id))) { implicit request =>
      sections.get(section_id) match {
        case Some(section) => {
          datasets.findOneByFileId(section.file_id) match {
            case Some(dataset) => Redirect(routes.Datasets.dataset(dataset.id))
            case None => InternalServerError("Dataset not found")
          }
        }
        case None => InternalServerError("Section not found")
      }
  }

  /**
   * TODO where is this used?
  def upload = Action(parse.temporaryFile) { implicit request =>
    request.body.moveTo(new File("/tmp/picture"))
    Ok("File uploaded")
  }
   */

  /**
   * Controller flow that handles the new multi file uploader workflow for creating a new dataset. Requires name, description, 
   * and id for the dataset. The interface should validate to ensure that these are present before reaching this point, but
   * the checks are made here as well. 
   * 
   */
  def submit() = PermissionAction(Permission.CreateDataset)(parse.multipartFormData) { implicit request =>
    implicit val user = request.user
    Logger.debug("------- in Datasets.submit ---------")
    var dsName = request.body.asFormUrlEncoded.getOrElse("name", null)
    var dsDesc = request.body.asFormUrlEncoded.getOrElse("description", null)
    var dsLevel = request.body.asFormUrlEncoded.get("datasetLevel")
    var dsId = request.body.asFormUrlEncoded.getOrElse("datasetid", null)

    if (dsName == null || dsDesc == null) {
      //Changed to return appropriate data and message to the upload interface
      var retMap = Map("files" ->
        Seq(
          toJson(
            Map(
              "name" -> toJson("Mising Form Data"),
              "size" -> toJson(0),
              "error" -> toJson("Please ensure that there is a name and a description set.")
            )
          )
        )
      )
      Ok(toJson(retMap))
    }

    if (dsId == null) {
      //Changed to return appropriate data and message to the upload interface
      var retMap = Map("files" ->
        Seq(
          toJson(
            Map(
              "name" -> toJson("Dataset was not created correctly."),
              "size" -> toJson(0),
              "error" -> toJson("Dataset not created correctly. Please try again.")
            )
          )
        )
      )
      Ok(toJson(retMap))
    }

    user match {
      case Some(identity) => {
        var nameOfFile : String = null
        request.body.file("files[]").map { f =>
          nameOfFile = f.filename
        }
        //The reference for the new dataset
        datasets.get(UUID(dsId(0))) match {
          case Some(dataset) => {
            request.body.file("files[]").map { f =>
              var nameOfFile = f.filename
              var flags = ""
              if(nameOfFile.toLowerCase().endsWith(".ptm")){
                var thirdSeparatorIndex = nameOfFile.indexOf("__")
                if(thirdSeparatorIndex >= 0){
                  var firstSeparatorIndex = nameOfFile.indexOf("_")
                  var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
                  flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
                  nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
                }
              }

              Logger.debug("Dataset submit, new file - uploading file " + nameOfFile)

              // store file
              Logger.info("Adding file" + identity)
              val showPreviews = request.body.asFormUrlEncoded.get("datasetLevel").get(0)
              val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, identity, showPreviews)
              Logger.debug("Uploaded file id is " + file.get.id)
              Logger.debug("Uploaded file type is " + f.contentType)

              val uploadedFile = f
              file match {
                case Some(f) => {

                  val id = f.id
                  if(showPreviews.equals("FileLevel"))
                    flags = flags + "+filelevelshowpreviews"
                  else if(showPreviews.equals("None"))
                    flags = flags + "+nopreviews"
                  var fileType = f.contentType
                  if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")){
                    fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "dataset")
                    if(fileType.startsWith("ERROR: ")){
                      Logger.error(fileType.substring(7))
                      InternalServerError(fileType.substring(7))
                    }
                    if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") || fileType.equals("multi/files-ptm-zipped") ){
                      if(fileType.equals("multi/files-ptm-zipped")){
                        fileType = "multi/files-zipped";
                      }

                      var thirdSeparatorIndex = nameOfFile.indexOf("__")
                      if(thirdSeparatorIndex >= 0){
                        var firstSeparatorIndex = nameOfFile.indexOf("_")
                        var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
                        flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
                        nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
                        files.renameFile(f.id, nameOfFile)
                      }
                      files.setContentType(f.id, fileType)
                    }
                  }

                  current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}

                  // TODO RK need to replace unknown with the server name
                  val key = "unknown." + "file."+ fileType.replace(".", "_").replace("/", ".")

                  val host = Utils.baseUrl(request) + request.path.replaceAll("dataset/submit$", "")

                  //directly add the file to the dataset via the service
                  datasets.addFile(dataset.id, f)

                  val dsId = dataset.id
                  val dsName = dataset.name

                  current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, dsId, flags))}

                  val dateFormat = new SimpleDateFormat("dd/MM/yyyy")

                  //for metadata files
                  if(fileType.equals("application/xml") || fileType.equals("text/xml")){
                    val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
                    files.addXMLMetadata(f.id, xmlToJSON)
                    datasets.addXMLMetadata(dsId, f.id, xmlToJSON)

                    Logger.debug("xmlmd=" + xmlToJSON)

                    //index the file
                    current.plugin[ElasticsearchPlugin].foreach{
                      _.index("data", "file", id, List(("filename",f.filename), ("contentType", fileType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",dsId.toString()),("datasetName",dsName), ("xmlmetadata", xmlToJSON)))

                    }
                    // index dataset
                    current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dsId,
                      List(("name",dsName), ("description", dataset.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",f.id.toString),("fileName",f.filename), ("collId",""),("collName",""), ("xmlmetadata", xmlToJSON)))}
                  } else {
                    //index the file

                    current.plugin[ElasticsearchPlugin].foreach{_.index("data", "file", id, List(("filename",f.filename), ("contentType", fileType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",dsId.toString),("datasetName",dsName)))}

                    // index dataset
                    current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dsId,
                      List(("name",dsName), ("description", dataset.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",f.id.toString),("fileName",f.filename), ("collId",""),("collName","")))}
                  }

                  // index the file using Versus for content-based retrieval
                  current.plugin[VersusPlugin].foreach{ _.index(f.id.toString,fileType) }

                  // TODO RK need to replace unknown with the server name and dataset type
                  val dtkey = "unknown." + "dataset."+ "unknown"
                  current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dsId, dsId, host, dtkey, Map.empty, "0", dsId, ""))}

                  //add file to RDF triple store if triple store is used
                  if(fileType.equals("application/xml") || fileType.equals("text/xml")){
                    play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{
                      case "yes" => {
                        sparql.addFileToGraph(f.id)
                        sparql.linkFileToDataset(f.id, dsId)
                      }
                      case _ => {}
                    }
                  }

                  // Insert DTS Request to the database
                  val clientIP=request.remoteAddress
                  val serverIP= request.host
                  dtsrequests.insertRequest(serverIP,clientIP, f.filename, id, fileType, f.length,f.uploadDate)

                  //Correctly set the updated URLs and data that is needed for the interface to correctly
                  //update the display after a successful upload.
                  var retMap = Map("files" ->
                    Seq(
                      toJson(
                        Map(
                          "name" -> toJson(nameOfFile),
                          "size" -> toJson(uploadedFile.ref.file.length()),
                          "url" -> toJson(routes.Files.file(f.id).absoluteURL(false)),
                          "deleteUrl" -> toJson(api.routes.Files.removeFile(f.id).absoluteURL(false)),
                          "deleteType" -> toJson("POST")
                        )
                      )
                    )
                  )
                  Ok(toJson(retMap))
                }

                case None => {
                  Logger.error("---------- ERROR Could not retrieve file that was just saved.")
                  //No need to update the service anymore since the dataset has already been created and added earlier.
                  //Just send the notifications. MMF - 1/15
                  current.plugin[AdminsNotifierPlugin].foreach{
                    _.sendAdminsNotification(Utils.baseUrl(request), "Dataset","added",dataset.id.stringify, dataset.name)}
                  //Changed to return appropriate data and message to the upload interface
                  var retMap = Map("files" ->
                    Seq(
                      toJson(
                        Map(
                          "name" -> toJson(nameOfFile),
                          "size" -> toJson(uploadedFile.ref.file.length()),
                          "error" -> toJson("Problem in storing the uploaded file.")
                        )
                      )
                    )
                  )
                  Ok(toJson(retMap))
                }
              }
            }.getOrElse{
              var retMap = Map("files" ->
                Seq(
                  toJson(
                    Map(
                      "name" -> toJson("File not received"),
                      "size" -> toJson(0),
                      "error" -> toJson("The file was not correctly received by the server. Please try again.")
                    )
                  )
                )
              )
              Ok(toJson(retMap))
            }
          }
          case None => {
            var retMap = Map("files" ->
              Seq(
                toJson(
                  Map(
                    "name" -> toJson("Dataset ID Invalid."),
                    "size" -> toJson(0),
                    "error" -> toJson("Dataset with the specified ID was not found. Please try again.")
                  )
                )
              )
            )
            Ok(toJson(retMap))
          }
        }
      }
      case None => Redirect(routes.Datasets.list()).flashing("error" -> "You are not authorized to create new datasets.")
    }
  }
  def users(id: UUID) = PermissionAction(Permission.ViewDataset) { implicit request =>
    implicit val user = request.user

    datasets.get(id) match {
      case Some(dataset) => {
        var userList: List[User] = List.empty
        var userListSpaceRoleTupleMap = Map[UUID, List[Tuple2[String,String]]]() // Map( User-id -> List((Space-name,Role-name)) )

        // Setup userList, add all users of all spaces associated with the dataset
        dataset.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(spc) => userList = spaceService.getUsersInSpace(spaceId) ::: userList
            case None => Redirect(routes.Datasets.dataset(id)).flashing("error" -> s"Error: No spaces found for dataset $id.")
          }
        }
        userList = userList.distinct.sortBy(_.fullName.toLowerCase)

        // Setup userListSpaceRoleTupleMap
        userList.foreach( usr => userListSpaceRoleTupleMap = userListSpaceRoleTupleMap + (usr.id -> List()) ) // initialize, based upon userList's values
        dataset.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(spc) => {
              val usersInCurrSpace: List[User] = spaceService.getUsersInSpace(spaceId)
              if (usersInCurrSpace.nonEmpty) {

                usersInCurrSpace.foreach { usr =>
                  spaceService.getRoleForUserInSpace(spaceId, usr.id) match {
                    case Some(role) => userListSpaceRoleTupleMap += ( usr.id -> ((spc.name,role.name) :: userListSpaceRoleTupleMap(usr.id)) )
                    case None => Redirect(routes.Datasets.dataset(id)).flashing("error" -> s"Error: Role not found for dataset $id user $usr.")
                  }
                }

              }
            }
            case None => Redirect (routes.Datasets.dataset(id)).flashing ("error" -> s"Error: No spaces found for dataset $id.");
          }
        }
        // Clean-up, and sort space-names per user
        userListSpaceRoleTupleMap = userListSpaceRoleTupleMap filter (_._2.nonEmpty) // remove empty-list Values from Map (and corresponding Key)
        for(k <- userListSpaceRoleTupleMap.keys) userListSpaceRoleTupleMap += ( k -> userListSpaceRoleTupleMap(k).distinct.sortBy(_._1.toLowerCase) )

        if(userList.nonEmpty) {
          val currUserIsAuthor = user.get.identityId.userId.equals(dataset.author.identityId.userId)
          Ok(views.html.datasets.users(dataset, userListSpaceRoleTupleMap, currUserIsAuthor, userList))
        }
        else Redirect(routes.Datasets.dataset(id)).flashing("error" -> s"Error: No users found for dataset $id.")
      }
      case None => Redirect(routes.Datasets.dataset(id)).flashing("error" -> s"Error: Dataset $id not found.")
    }

  }

  def metadataSearch() = PermissionAction(Permission.ViewDataset) { implicit request =>
      implicit val user = request.user
      Ok(views.html.metadataSearch())
  }

  def generalMetadataSearch() = PermissionAction(Permission.ViewDataset) { implicit request =>
      implicit val user = request.user
      Ok(views.html.generalMetadataSearch())
  }
}

