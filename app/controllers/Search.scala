package controllers

import play.Logger
import services._
import scala.collection.JavaConversions.mapAsScalaMap
import edu.illinois.ncsa.isda.lsva.ImageMeasures
import edu.illinois.ncsa.isda.lsva.ImageDescriptors.FeatureType
import scala.collection.mutable.{ HashMap, ListBuffer }
import util.DistancePriorityQueue
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current
import api.{ Permission, WithPermission }
import javax.inject.Inject
import scala.concurrent.Future
import scala.Some
import util.SearchResult
import models.UUID
import org.elasticsearch.action.search.SearchResponse

/**
 * Text search.
 *
 * @author Luigi Marini
 */
class Search @Inject() (
  datasets: DatasetService,
  files: FileService,
  collections: CollectionService,
  queries: MultimediaQueryService,
  previews: PreviewService) extends SecuredController {

  /**
   * Search results.
   */
  def search(query: String) = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    implicit val user = request.user
    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) => {
        Logger.debug("Searching for: " + query)
        var listOfFiles = ListBuffer.empty[models.File]
        var listOfdatasets = ListBuffer.empty[models.Dataset]
        var listOfcollections = ListBuffer.empty[models.Collection]
        var mapdatasetIds = new scala.collection.mutable.HashMap[String, ListBuffer[(String, String)]]
        var mapcollectionIds = new scala.collection.mutable.HashMap[String, ListBuffer[(String, String)]]
        if (query != "") {
          var result: Option[SearchResponse] = if (query.contains(":")) {
            var fields = query.split(":")
            if (fields(0).isEmpty()) {
              Logger.debug("field(0) is empty and field 1 =" + query.replaceAll("([+:/\\\\])", "\\\\$1"))
              current.plugin[ElasticsearchPlugin].map { _.search("data", query.replaceAll("([+:/\\\\])", "\\\\$1")) }
            } else {
              Logger.debug("field(0)=" + fields(0) + "  fields(1)= " + fields(1).replaceAll("([+:/\\\\])", "\\\\$1"))
              val fieldsArray = fields(0).split(",")
              current.plugin[ElasticsearchPlugin].map { _.search("data", fieldsArray, fields(1).replaceAll("([+:/\\\\])", "\\\\$1")) }
            }
          } else
            current.plugin[ElasticsearchPlugin].map { _.search("data", query.replaceAll("([+:/\\\\])", "\\\\$1")) }

          result match {
            case Some(searchResponse) => {
              for (hit <- searchResponse.getHits().getHits()) {
                Logger.debug("Computing search result " + hit.getId())
                Logger.info("Fields: ")
                for ((key, value) <- mapAsScalaMap(hit.getFields())) {
                  Logger.info(value.getName + " = " + value.getValue())
                }

                if (hit.getType() == "file") {
                  files.get(UUID(hit.getId())) match {
                    case Some(file) => {
                      Logger.debug("FILES:hits.hits._id: Search result found file " + hit.getId());
                      Logger.debug("FILES:hits.hits._source: Search result found dataset " + hit.getSource().get("datasetId"))
                      //Logger.debug("Search result found file " + hit.getId()); files += file

                      var datasetsList = ListBuffer(): ListBuffer[(String, String)]
                      if ((hit.getSource().get("datasetId") != null) && (hit.getSource().get("datasetName") != null)) {
                        val datasetsIdsList = hit.getSource().get("datasetId").toString().split(" %%% ").toList
                        val datasetsNamesList = hit.getSource().get("datasetName").toString().split(" %%% ").toList.iterator
                        for (currentDatasetId <- datasetsIdsList) {
                          datasetsList = datasetsList :+ (currentDatasetId, datasetsNamesList.next())
                        }
                      }

                      mapdatasetIds.put(hit.getId(), datasetsList)
                      listOfFiles += file
                    }
                    case None => Logger.debug("File not found " + hit.getId())
                  }
                } else if (hit.getType() == "dataset") {
                  Logger.debug("DATASETS:hits.hits._source: Search result found dataset " + hit.getSource().get("name"))
                  Logger.debug("DATASETS:Dataset.id=" + hit.getId());
                  //Dataset.findOneById(new ObjectId(hit.getId())) match {

                  datasets.get(UUID(hit.getId())) match {
                    case Some(dataset) => {
                      Logger.debug("Search result found dataset" + hit.getId())

                      var collectionsList = ListBuffer(): ListBuffer[(String, String)]
                      Logger.debug("src: " + hit.getSource().toString())
                      if ((hit.getSource().get("collId") != null) && (hit.getSource().get("collName") != null)) {
                        val collectionsIdsList = hit.getSource().get("collId").toString().split(" %%% ").toList
                        val collectionsNamesList = hit.getSource().get("collName").toString().split(" %%% ").toList.iterator
                        for (currentCollectionId <- collectionsIdsList) {
                          collectionsList = collectionsList :+ (currentCollectionId, collectionsNamesList.next())
                        }
                      }
                      mapcollectionIds.put(hit.getId(), collectionsList)

                      listOfdatasets += dataset
                    }
                    case None => {
                      Logger.debug("Dataset not found " + hit.getId())
                      Redirect(routes.Datasets.dataset(UUID(hit.getId)))

                    }
                  }
                } else if (hit.getType() == "collection") {
                  Logger.debug("COLLECTIONS:hits.hits._source: Search result found collection " + hit.getSource().get("name"))
                  Logger.debug("COLLECTIONS:Collection.id=" + hit.getId());
                  //Dataset.findOneById(new ObjectId(hit.getId())) match {
                  collections.get(UUID(hit.getId())) match {
                    case Some(collection) =>
                      Logger.debug("Search result found collection" + hit.getId());
                      var collectionThumbnail: Option[String] = None
                      try {
                        for (dataset <- collection.datasets) {
                          if (!dataset.thumbnail_id.isEmpty) {
                            collectionThumbnail = dataset.thumbnail_id
                            throw ThumbnailFound
                          }
                        }
                      } catch {
                        case ThumbnailFound =>
                      }
                      val collectionWithThumbnail = collection.copy(thumbnail_id = collectionThumbnail)
                      listOfcollections += collectionWithThumbnail
                    case None => {
                      Logger.debug("Collection not found " + hit.getId())
                      Redirect(routes.Collections.collection(UUID(hit.getId)))
                    }
                  }
                }
                Ok(views.html.searchResults(query, listOfFiles.toArray, listOfdatasets.toArray, listOfcollections.toArray, mapdatasetIds, mapcollectionIds))
              }
            }
            case None => {
              Logger.debug("Search returned no results")
            }
          }
        }

        Logger.debug("newquery: " + query.replaceAll("([/\\\\])", "\\\\$1"))
        Ok(views.html.searchResults(query, listOfFiles.toArray, listOfdatasets.toArray, listOfcollections.toArray, mapdatasetIds, mapcollectionIds))

      }
      case None => {
        Logger.debug("Search plugin not enabled")
        Ok(views.html.pluginNotEnabled("Text search"))
      }
    }

  }

  def multimediasearch() = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    Logger.debug("Starting multimedia search interface")
    implicit val user = request.user
    Ok(views.html.multimediasearch())
  }

  /**
   * Search MultimediaFeatures.
   */
  /*def searchMultimediaIndex(section_id: UUID) = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    Logger.debug("Searching multimedia index " + section_id.stringify)
    // TODO handle multiple previews found
    val preview = previews.findBySectionId(section_id)(0)
    queries.findFeatureBySection(section_id) match {
      case Some(feature) => {
        // setup priority queues
        val queues = new HashMap[String, DistancePriorityQueue]
        val representations = feature.features.map { f =>
          queues(f.representation) = new DistancePriorityQueue(20)
        }
        // push into priority queues
        feature.features.map { f =>
          Logger.debug("Computing " + f.representation)
          val cursor = queries.listAll()
          while (cursor.hasNext) {
            val mf = cursor.next          
            Logger.debug("Found multimedia features " + mf.id + " for section " + section_id)
            mf.features.find(_.representation == f.representation) match {
              case Some(fd) => {
                val distance = ImageMeasures.getDistance(FeatureType.valueOf(fd.representation), f.descriptor.toArray, fd.descriptor.toArray)
                if (!distance.isNaN()) {
                  Logger.trace(f.representation + "/" + fd.representation + " Distance between " + feature.section_id + " and " + mf.id + " is " + distance)
                  queues.get(f.representation).map { q =>
                    val popped = q.insertWithOverflow(SearchResult(mf.section_id.get.toString, distance, None))
                    if (popped != null) Logger.trace("Popped distance off the queue " + popped)
                  }
                } else {
                  Logger.debug("Distance NaN " + f.descriptor + " and " + fd.descriptor + " is " + distance)
                }
              }
              case None =>
                Logger.error("Matching descriptor not found")
            }
          }
        }

        val items = new HashMap[String, ListBuffer[SearchResult]]
        queues map {
          case (key, queue) =>
            val list = new ListBuffer[SearchResult]
            while (queue.size > 0) {
              val element = queue.pop()
              val previewsBySection = previews.findBySectionId(UUID(element.section_id))
              if (previewsBySection.size == 1) {
                Logger.trace("Appended search result " + key + " " + element.section_id + " " + element.distance + " " + previewsBySection(0).id.toString)
                list.prepend(SearchResult(element.section_id, element.distance, Some(previewsBySection(0).id.toString)))
              } else {
                Logger.error("Found more/less than one preview " + preview)
              }
            }
            items += key -> list
        }
        Ok(views.html.searchMultimediaIndex(preview, items))
      }
      case None => InternalServerError("feature not found")
    }
  }*/

  def callSearchMultimediaIndexView(section_id: UUID) = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    Logger.debug("Searching multimedia index " + section_id.stringify)
    // TODO handle multiple previews found
    val preview = previews.findBySectionId(section_id)(0)
    Ok(views.html.searchMultimediaIndex(section_id, preview))
  }

  def advanced() = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    Logger.debug("Starting Advanced Search interface")
    Ok(views.html.advancedsearch())
  }

  def SearchByText(query: String) = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    Logger.debug("Searching for" + query)
    Ok("")
  }

  /*
   * GET the query file from a URL and compare within the database and show the result   
   * */
  def searchbyURL(queryURL: String) = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    Async {
      implicit val user = request.user
      current.plugin[VersusPlugin] match {
        case Some(plugin) => {
          val futureFutureListResults = for {
            indexList <- plugin.getIndexesAsFutureList()
          } yield {
            val resultListOfFutures = indexList.map {
              index =>
                plugin.queryIndexForURL(queryURL, index.id).map {
                  queryResult =>
                    (index, queryResult)
                }
            }
            //convert list of futures into a Future[list]
            scala.concurrent.Future.sequence(resultListOfFutures)
          } //End yield- outer for    	           
          for {
            futureListResults <- futureFutureListResults
            listOfResults <- futureListResults
          } yield {
            //get the last part of the image url, send it to the view
            val lastSlash = queryURL.lastIndexOf("/")
            val fileName = queryURL.substring(lastSlash + 1)
            Ok(views.html.multimediaSearchResults(fileName, None, None, listOfResults))
          }
        } //case some

        case None => {
          Future(Ok("No Versus Service"))
        }
      } //match            
    } //Async
  }

  /**
   * Finds similar objects(images, pdfs, etc) in Multiple index for a temporary file
   * Input file is NOT in db, just uploaded by user.
   */
  def findSimilarToQueryFile(fileID: UUID, typeToSearch: String, sectionsSelected: List[String]) = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    Async {
      implicit val user = request.user
      //query file is a new/temp file, it will be stored in MultimediaQueryService
      //in controllers/Files -> uploadSelectQuery
      queries.get(fileID) match {
        case Some((inputStream, filename, contentType, length)) => {
          current.plugin[VersusPlugin] match {
            case Some(plugin) => {
              val indexesToSearchFuture = for {
                indexesForContent <- plugin.getIndexesForContentTypeAsFutureList(contentType)
                indexesForType <- plugin.getIndexesForType(typeToSearch, sectionsSelected)
              } yield indexesForContent.intersect(indexesForType)

              val futureFutureListResults = for {
                indexesToSearch <- indexesToSearchFuture
              } yield {
                val resultListOfFutures = indexesToSearch.map(
                    index => plugin.queryIndexForNewFile(fileID, index.id).map(queryResult => (index, queryResult))
                )

                //convert list of futures into a Future[list]
                scala.concurrent.Future.sequence(resultListOfFutures)
              } //End yield    		

              for {
                futureListResults <- futureFutureListResults
                listOfResults <- futureListResults
              } yield {
                // string thumbnail
                // will change to UUID once models.File.thumbnail_id is changed to UUID
                val thumb_id: String = queries.getFile(fileID).flatMap(_.thumbnail_id).map(_.stringify).getOrElse("")
                Ok(views.html.multimediaSearchResults(filename, Some(fileID), Some(thumb_id), listOfResults))
              }
            } //end of case Some(plugin)   

            case None => {
              Future(Ok("No Versus Service"))
            }
          } //current.plugin[VersusPlugin] match  
        } //case Some((inputStream...

        case None => {
          Logger.debug("File with id " + fileID + " not found")
          Future(Ok("File with id " + fileID + " not found"))
        }
      } //end of queries.get(imageID) match 
    } //Async
  }

  /**
   * Finds similar objects(images, pdfs, etc) in Multiple index for a given file (file is already in db)
   *
   */
  def findSimilarToExistingFile(inputFileId: UUID) = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    Async {
      implicit val user = request.user
      //file will be stored in FileService
      files.getBytes(inputFileId) match {
        case Some((inputStream, filename, contentType, length)) => {
          current.plugin[VersusPlugin] match {
            case Some(plugin) => {
              val futureFutureListResults = for {
                indexList <- plugin.getIndexesForContentTypeAsFutureList(contentType)
              } yield {
                val resultListOfFutures = indexList.map {
                  index =>
                    plugin.queryIndexForExistingFile(inputFileId, index.id).map {
                      queryResult => (index, queryResult)
                    }
                }
                //convert list of futures into a Future[list]
                scala.concurrent.Future.sequence(resultListOfFutures)
              } //End yield- outer for    	

              for {
                futureListResults <- futureFutureListResults
                listOfResults <- futureListResults
              } yield {
                //get  string thumbnail id for this file and pass on to view
                val thumb_id = files.get(inputFileId).flatMap(_.thumbnail_id).getOrElse("")
                Ok(views.html.multimediaSearchResults(filename, Some(inputFileId), Some(thumb_id), listOfResults))
              }
            } //end of case Some(plugin)                   

            case None => {
              Future(Ok("No Versus Service"))
            }
          } //current.plugin[VersusPlugin] match  
        } //case Some((inputStream...

        case None => {
          Logger.debug("Could not find similar for file id " + inputFileId)
          Future(Ok("Could not find similar for file id " + inputFileId))
        }
      } //end of files.getBytes(inputFileId) match 
    } //Async
  }

  /**
   * Checks that input has file id, index id, and weight, and that they all are correct.
   * Returns
   * 	boolean: true if errors are present
   * 	String: error message
   * 	List[Double] : weights
   */
  def validateInput(input: Map[String, Seq[String]]): (Boolean, String, List[Double]) = {
    var inputErrors = false
    var errorMessage = ""
    var weightsList = List(0.0)
    if (!input.contains("FileID") || !input.contains("IndexID") || !input.contains("Weight")) {
      inputErrors = true
      return (inputErrors, "Not all fields are present", List(0.0))
    }
    //now deal with weights	 		  
    try {
      weightsList = input("Weight").map(w => w.toDouble).toList
    } catch {
      case e: Exception => return (true, "Weights must be double values between 0.0 and 1.0", List(0.0))
    }

    var sum = 0.0
    for (w <- weightsList) {
      if (w < 0) return (true, "Weights must be double values between 0.0 and 1.0", List(0.0))
      sum += w
    }
    if (sum != 1) return (true, "sum of weights must be 1", List(0.0))

    //no errors, return list of weights 
    (false, "", weightsList)
  }

  /**
   * For a list of maps and a corresponding list of weights, finds linear combination
   * of the maps.
   */
  def mergeMaps(maps: List[scala.collection.immutable.HashMap[String, Double]],
    weights: List[Double]): scala.collection.immutable.HashMap[String, Double] = {
    //get a list of non-zero weights and a list of corresponding maps
    var nonzeroWeightsBuf = ListBuffer.empty[Double]
    var nonzeroMapsBuf = ListBuffer.empty[scala.collection.immutable.HashMap[String, Double]]
    //go through all weights and only keep maps that have nonzero weights
    for (i <- 0 to weights.length - 1) {
      if (weights(i) != 0) {
        nonzeroWeightsBuf += weights(i)
        nonzeroMapsBuf += maps(i)
      }
    }
    val nonzeroWeights = nonzeroWeightsBuf.toList
    val nonzeroMaps = nonzeroMapsBuf.toList

    //If after removing zero-weight maps we have just one map left - nothing to merge, return the map. 
    //Otherwise, merge maps.
    if (nonzeroMaps.length == 1) {
      nonzeroMaps(0)
    } else {
      //merge the first two maps
      var mergedMap = mergeTwoMaps(nonzeroMaps(0), nonzeroMaps(1), nonzeroWeights(0), nonzeroWeights(1))
      //merge the rest of the maps
      for (ind <- 2 to nonzeroWeights.length - 1) {
        mergedMap = mergeTwoMaps(mergedMap, nonzeroMaps(ind), 1.0, nonzeroWeights(ind))
      }
      mergedMap
    }
  }
  /**
   * For two maps and two corresponding weights, will find linear combinations of corresponding values,
   * 	using the weights provided
   */
  def mergeTwoMaps(mapOne: collection.immutable.HashMap[String, Double],
    mapTwo: collection.immutable.HashMap[String, Double],
    w1: Double, w2: Double): scala.collection.immutable.HashMap[String, Double] = {

    mapOne.merged(mapTwo)({ case ((file, proxOne), (_, proxTwo)) => (file, w1 * proxOne + w2 * proxTwo) })

  }

  /**
   * Pass a list of indexes and a list of weights to this method. Will calculate the weighted combination
   * of the indexes.
   */
  def findSimilarWeightedIndexes() =
    SecuredAction(parse.multipartFormData,
      authorization = WithPermission(Permission.SearchDatasets)) {
        implicit request =>
          Async {
            implicit val user = request.user
            //using a helper method to validate input and get weights
            val (inputErrors, errorMessage, weights) = validateInput(request.body.dataParts)

            if (inputErrors == false) {
              //fileId in dataParts is a sequence of just one element
              val fileId = UUID(request.body.dataParts("FileID").head)
              val indexIDs = request.body.dataParts("IndexID").map(i => UUID(i)).toList

              //query file will be stored in MultimediaQueryService
              //in controllers/Files -> uploadSelectQuery
              queries.get(fileId) match {
                case Some(fileInfo) => {
                  val filename = fileInfo._2
                  current.plugin[VersusPlugin] match {
                    case Some(plugin) => {
                      //get file and a list of indexes from request, query this file against each of these indexes 
                      val queryResults = indexIDs.map(indId => plugin.queryIndexSorted(fileId.stringify, indId.stringify))
                      //change a list of futures into a future list
                      val futureListResults = scala.concurrent.Future.sequence(queryResults)

                      for {
                        maps <- futureListResults
                      } yield {
                        //Calling helper method to merge all the maps. The magic happens here.
                        val mergedMaps = mergeMaps(maps, weights)

                        val mergedResult = for {
                          (fileURL, prox) <- mergedMaps
                        } yield {
                          //fileURL = http://localhost:9000/api/files/54bebcb919aff1ea8c8145ac/blob?key=r1ek3rs
                          //get id of the result
                          val result_id = UUID(plugin.getIdFromVersusURL(fileURL))

                          //find file name and thumbnail id for the result
                          //TODO: add processing in case results are not whole files but _sections_ of files
                          val oneFileName = files.get(result_id).map(_.filename).getOrElse("")
                          val oneThumbnlId = files.get(result_id).flatMap(_.thumbnail_id).getOrElse("")
                          (result_id, oneFileName, oneThumbnlId, prox)
                        }
                        //sort by combined proximity values
                        val sortedMergedResults = mergedResult.toList sortBy { _._4 }
                        //get an option of thumbnail id for this image and pass on to view
                        val thumb_id = queries.getFile(fileId).flatMap(_.thumbnail_id)
                        Ok(views.html.multimediaSearchResultsCombined(filename, thumb_id, sortedMergedResults))
                      } //end of yield   					
                    } //end of case Some(plugin)   
                    case None => {
                      Future(Ok("No Versus Service"))
                    }
                  } //current.plugin[VersusPlugin] match  
                } //case Some((inputStream...

                case None => {
                  Logger.debug("File with id " + fileId + " not found")
                  Future(Ok("File with id " + fileId + " not found"))
                }
              } //end of queries.get(imageID) match 
            } //end of if no validation errors
            else { Future(Ok("Form validation errors: " + errorMessage)) }
          } //Async
      }

  //  def Filterby(id: String) = TODO

  def uploadquery() = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    request.body.file("picture").map { picture =>
      import java.io.File
      picture.ref.moveTo(new File("/tmp/picture"))
      Ok("File uploaded")
    }.getOrElse {
      Redirect(routes.Application.index).flashing(
        "error" -> "Missing file")
    }
  }
}
