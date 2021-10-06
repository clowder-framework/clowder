package services.s3

import java.io.{IOException, InputStream}
import models.UUID
import com.amazonaws.auth.{AWSCredentialsProviderChain, AWSStaticCredentialsProvider, BasicAWSCredentials, DefaultAWSCredentialsProviderChain}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.{CreateBucketRequest, GetObjectRequest, HeadBucketRequest, ObjectMetadata}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.{AmazonClientException, ClientConfiguration}
import com.google.inject.Inject
import play.Logger
import play.api.Play
import services.ByteStorageService
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.amazonaws.services.s3.transfer.Upload
import services.s3.S3ByteStorageService.{handleACE, handleASE, handleIOE, handleUnknownError, s3}


/**
 * A ByteStorageService for Clowder that enables use of S3-compatible
 * object stores to serve as the file backing for Clowder. This allows
 * you to use an S3 bucket on AWS or Minio to store your files.
 *
 *
 * Available Configuration Options:
 *    clowder.s3.serviceEndpoint - Host/port of the service to use for storage
 *    clowder.s3.bucketName - the name of the bucket that should be used to store files
 *    clowder.s3.accessKey - access key with which to access the bucket
 *    clowder.s3.secretKey - secret key associated with the access key above
 *    clowder.s3.region - the region where your S3 bucket lives
 *    clowder.s3.depth - the number of sub-paths to insert (default: 3)
 *        NOTE: this will randomly distribute files into smaller subdirectories and is recommended for performance reasons
 *
 * @author Mike Lambert
 *
 */

/** Available configuration options for s3 storage */
object S3ByteStorageService {
  val ServiceEndpoint: String = "clowder.s3.serviceEndpoint"
  val BucketName: String = "clowder.s3.bucketName"
  val AccessKey: String = "clowder.s3.accessKey"
  val SecretKey: String = "clowder.s3.secretKey"
  val Region: String = "clowder.s3.region"

  Logger.underlying().info("Starting up static S3ByteStorageService...")

  val s3: AmazonS3 = {
    // NOTE: Region is ignored for MinIO case
    val s3client = (Play.current.configuration.getString(S3ByteStorageService.ServiceEndpoint), Play.current.configuration.getString(S3ByteStorageService.Region)) match {
      case (Some(serviceEndpoint), Some(region)) => {
        Logger.info("Creating S3 Client with custom endpoint and region.")
        AmazonS3ClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      }
      case (Some(serviceEndpoint), None) => {
        Logger.info("Creating S3 Client with custom endpoint. (using default region)")
        AmazonS3ClientBuilder.standard()
          .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, Regions.DEFAULT_REGION.getName))
      }
      case (None, Some(region)) => {
        Logger.info("Creating S3 Client with custom region: " + region)
        AmazonS3ClientBuilder.standard().withRegion(region)
      }
      case (None, None) => AmazonS3ClientBuilder.standard()
    }

    (Play.current.configuration.getString(S3ByteStorageService.AccessKey),
      Play.current.configuration.getString(S3ByteStorageService.SecretKey)) match {
      case (Some(accessKey), Some(secretKey)) => {
        val credentials = new BasicAWSCredentials(accessKey, secretKey)
        val clientConfiguration = new ClientConfiguration
        clientConfiguration.setSignerOverride("AWSS3V4SignerType")

        Logger.info("Creating S3 Client with custom credentials.")

        s3client.withClientConfiguration(clientConfiguration)
          .withCredentials(
            new AWSCredentialsProviderChain(
              new AWSStaticCredentialsProvider(credentials),
              DefaultAWSCredentialsProviderChain.getInstance))
          .build()
      }
      case (None, None) => {
        Logger.info("Creating S3 Client with default credentials.")

        s3client.withCredentials(DefaultAWSCredentialsProviderChain.getInstance).build()
      }

      case _ => {
        val errMsg = "Bad S3 configuration: accessKey and secretKey are both required if one is given. Falling back to default credentials..."
        Logger.warn(errMsg)

        s3client.withCredentials(DefaultAWSCredentialsProviderChain.getInstance).build()
      }
    }
  }

  // Ensure that bucket exists and that we have access to it before continuing
  Play.current.configuration.getString(S3ByteStorageService.BucketName) match {
    case Some(bucketName) => {
      try {
        // Validate configuration by checking for bucket existence on startup
        s3.headBucket(new HeadBucketRequest(bucketName))
        Logger.debug("Confirmed access to configured S3 bucket. S3ByteStorageService loading is complete.")
      } catch {
        case sdke @ (_: AmazonClientException | _: AmazonServiceException) => {
          if (sdke.getMessage.contains("Status Code: 404")) {
            Logger.warn("Configured S3 bucket does not exist, attempting to create it now...")
            try {
              // Bucket does not exist - create the bucket
              s3.createBucket(new CreateBucketRequest(bucketName))
              Logger.debug("Created configured S3 bucket. S3ByteStorageService loading is complete.")
            } catch {
              // Bucket could not be created - abort
              case _: Throwable => throw new RuntimeException("Bad S3 configuration: Bucket does not exist and could not be created.")
            }
          } else if (sdke.getMessage.contains("Status Code: 403")) {
            // Bucket exists, but you do not have permission to access it
            throw new RuntimeException("Bad S3 configuration: You do not have access to the configured bucket.")
          } else {
            // Unknown error - print status code for further investigation
            val errMsg = sdke.getLocalizedMessage
            Logger.error(errMsg)
            throw new RuntimeException("Bad S3 configuration: an unknown error has occurred - " + errMsg)
          }
        }
        case _: Throwable => throw new RuntimeException("Bad S3 configuration: an unknown error has occurred.")
      }
    }
    case _ => throw new RuntimeException("Bad S3 configuration: verify that you have set all configuration options.")
  }

  /* Reusable handlers for various Exception types */
  def handleUnknownError(err: Exception = null) = {
    if (err != null) {
      Logger.error("An unknown error occurred in the S3ByteStorageService: " + err.toString)
    } else {
      Logger.error("An unknown error occurred in the S3ByteStorageService.")
    }
  }

  /* Reusable handlers for various Exception types */
  def handleIOE(err: IOException) = {
    Logger.error("IOException occurred in the S3ByteStorageService: " + err)
  }

  /* Reusable handlers for various Exception types */
  def handleACE(ace: AmazonClientException) = {
    Logger.error("Caught an AmazonClientException, which " + "means the client encountered " + "an internal error while trying to " + "communicate with S3, " + "such as not being able to access the network.")
    Logger.error("Error Message: " + ace.getMessage)
  }

  /* Reusable handlers for various Exception types */
  def handleASE(ase: AmazonServiceException) = {
    Logger.error("Caught an AmazonServiceException, which " + "means your request made it " + "to Amazon S3, but was rejected with an error response" + " for some reason.")
    Logger.error("Error Message:    " + ase.getMessage)
    Logger.error("HTTP Status Code: " + ase.getStatusCode)
    Logger.error("AWS Error Code:   " + ase.getErrorCode)
    Logger.error("Error Type:       " + ase.getErrorType)
    Logger.error("Request ID:       " + ase.getRequestId)
  }
}

/**
  *
  * A ByteStorageService for Clowder that enables use of S3-compatible
  * object stores to serve as the file backing for Clowder. This allows
  * you to use an S3 bucket on AWS or Minio to store your files.
  */
class S3ByteStorageService @Inject()() extends ByteStorageService {

  /**
    * Store bytes to the specified path within the configured S3 bucket.
    *
    * @param inputStream stream of bytes to save to the bucket
    * @param prefix      collection name prefix to prepend to path
    * @param length      length (in bytes) of the stream
    * @return
    */
  def save(inputStream: InputStream, prefix: String, length: Long): Option[(String, Long)] = {
    Play.current.configuration.getString(S3ByteStorageService.BucketName) match {
      case None => Logger.error("Failed saving bytes: failed to find configured S3 bucketName.")
      case Some(bucketName) => {
        val xferManager: TransferManager = TransferManagerBuilder.standard().withS3Client(s3).build
        try {
          Logger.debug("Saving file to: /" + bucketName + "/" + prefix)

          val id = UUID.generate.stringify
          val separator = java.io.File.separatorChar

          var targetPath = prefix
          var folders = id
          var depth = Play.current.configuration.getInt("clowder.s3.depth").getOrElse(3)

          // id seems to be same at the start but more variable at the end
          while (depth > 0 && folders.length > 4) {
            depth -= 1
            targetPath += separator + folders.takeRight(2)
            folders = folders.dropRight(2)
          }

          // use full id again as the filename
          targetPath += separator + id

          // Set Content-Length header before uploading to save memory
          // NOTE: If not, entire stream buffers in-memory and can cause OOM
          val metadata = new ObjectMetadata()
          metadata.setContentLength(length)

          // Build up a unique path based on the file/uploader?
          val xfer: Upload = xferManager.upload(bucketName, targetPath, inputStream, metadata)
          // loop with Transfer.isDone()
          //XferMgrProgress.showTransferProgress(xfer)
          //  or block with Transfer.waitForCompletion()
          xfer.waitForCompletion()
          xferManager.shutdownNow()

          Logger.debug("File saved to: /" + bucketName + "/" + prefix + "/" + targetPath)

          return Option((targetPath, length))

          // TODO: Verify transferred bytes with MD5?
        } catch {
          case ase: AmazonServiceException => handleASE(ase)
          case ace: AmazonClientException => handleACE(ace)
          case ioe: IOException => handleIOE(ioe)
          case _: Throwable => handleUnknownError(_)
        }
      }
    }

    // Return None (in case of failure)
    None
  }

  /**
    * Given a path, retrieve the bytes located at that path inside the configured S3 bucket.
    *
    * @param path    the path of the file to load from the bucket
    * @param ignored collection name prefix (ignored in this context)
    * @return
    */
  def load(path: String, ignored: String): Option[InputStream] = {
    Play.current.configuration.getString(S3ByteStorageService.BucketName) match {
      case None => Logger.error("Failed fetching bytes: failed to find configured S3 bucketName.")
      case Some(bucketName) => {
        Logger.debug("Loading file from: /" + bucketName + "/" + path)
        try {
          // Download object from S3 bucket
          // NOTE: path should already contain the prefix
          val rangeObjectRequest = new GetObjectRequest(bucketName, path)
          val objectPortion = s3.getObject(rangeObjectRequest)

          return Option(objectPortion.getObjectContent)
        } catch {
          case ase: AmazonServiceException => handleASE(ase)
          case ace: AmazonClientException => handleACE(ace)
          case ioe: IOException => handleIOE(ioe)
          case _: Throwable => handleUnknownError(_)
        }
      }
    }

    // Return None (in case of failure)
    None
  }

  /**
    * Given a path, delete the file located at the path within the configured S3 bucket.
    *
    * @param path    the path of the file inside the bucket
    * @param ignored collection name prefix (ignored in this context)
    * @return
    */
  def delete(path: String, ignored: String): Boolean = {
    Play.current.configuration.getString(S3ByteStorageService.BucketName) match {
      case None => Logger.error("Failed deleting bytes: failed to find configured S3 bucketName.")
      case Some(bucketName) => {
        // delete the bytes
        Logger.debug("Removing file at: /" + bucketName + "/" + path)
        try {
          // Delete object from S3 bucket
          // NOTE: path should already contain the prefix
          s3.deleteObject(bucketName, path)
          return true
        } catch {
          case ase: AmazonServiceException => handleASE(ase)
          case ace: AmazonClientException => handleACE(ace)
          case ioe: IOException => handleIOE(ioe)
          case _: Throwable => handleUnknownError(_)
        }
      }
    }

    // Return false (in case of failure)
    return false
  }
}
