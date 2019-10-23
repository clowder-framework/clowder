package services.s3

import java.io.{File, FileOutputStream, IOException, InputStream}
import java.util.UUID

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.{GetObjectRequest, ObjectMetadata, PutObjectRequest}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.{AmazonClientException, AmazonServiceException, ClientConfiguration}
import com.google.inject.Inject
import org.apache.commons.io.IOUtils
import play.Logger
import play.api.Play
import services.ByteStorageService


import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.amazonaws.services.s3.transfer.Upload

/** Available configuration options for s3 storage */
object S3ByteStorageService {
  val ServiceEndpoint: String = "clowder.s3.serviceEndpoint"
  val BucketName: String = "clowder.s3.bucketName"
  val AccessKey: String = "clowder.s3.accessKey"
  val SecretKey: String = "clowder.s3.secretKey"
  val Region: String = "clowder.s3.region"
}

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
  *    clowder.s3.region - the region where your S3 bucket lives (currently unused)
  *
  *
  * @author Mike Lambert
  *
  */
class S3ByteStorageService @Inject()() extends ByteStorageService {

  /**
    * Grabs config parameters from Clowder to return a
    * AmazonS3 pointing at the configured service endpoint.
    */
  def s3Bucket(): AmazonS3 = {
    (Play.current.configuration.getString(S3ByteStorageService.ServiceEndpoint),
      Play.current.configuration.getString(S3ByteStorageService.AccessKey),
        Play.current.configuration.getString(S3ByteStorageService.SecretKey)) match {
          case (Some(serviceEndpoint), Some(accessKey), Some(secretKey)) => {
            val credentials = new BasicAWSCredentials(accessKey, secretKey)
            val clientConfiguration = new ClientConfiguration
            clientConfiguration.setSignerOverride("AWSS3V4SignerType")

            Logger.debug("Created S3 Client for " + serviceEndpoint)

            val region = Play.current.configuration.getString(S3ByteStorageService.Region) match {
              case Some(region) => region
              case _ => Regions.US_EAST_1.name()
            }

            return AmazonS3ClientBuilder.standard()
              // NOTE: Region is ignored for MinIO case?
              // TODO: Allow user to set region for AWS case?
              .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
              .withPathStyleAccessEnabled(true)
              .withClientConfiguration(clientConfiguration)
              .withCredentials(new AWSStaticCredentialsProvider(credentials))
              .build()
          }
          case _ => {
            val errMsg = "Bad S3 configuration: verify that you have set all configuration options."
            Logger.error(errMsg)
            throw new RuntimeException(errMsg)
          }
        }
  }

  /**
    * Store bytes to the specified path within the configured S3 bucket.
    *
    * @param inputStream stream of bytes to save to the bucket
    * @param ignored     unused parameter in this context
    * @return
    */
  def save(inputStream: InputStream, ignored: String): Option[(String, Long)] = {
    Play.current.configuration.getString(S3ByteStorageService.BucketName) match {
      case None => Logger.error("Failed deleting bytes: failed to find configured S3 bucketName.")
      case Some(bucketName) => {
        val xferManager = TransferManagerBuilder.standard().withS3Client(this.s3Bucket).build
        try {
          Logger.debug("Saving file to: /" + bucketName)

          // TODO: How to build up a unique path based on the file/uploader?
          val targetPath = UUID.randomUUID().toString
          val length = inputStream.available()

          // TODO: What can this be used for?
          val metadata = new ObjectMetadata()

          val xfer = xferManager.upload(bucketName, targetPath, inputStream, metadata)
          // loop with Transfer.isDone()
          //XferMgrProgress.showTransferProgress(xfer)
          //  or block with Transfer.waitForCompletion()
          xfer.waitForCompletion()
          xferManager.shutdownNow()

          Logger.debug("File saved to: /" + bucketName + "/" + targetPath)

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
    * @param ignored unused parameter in this context
    * @return
    */
  def load(path: String, ignored: String): Option[InputStream] = {
    Play.current.configuration.getString(S3ByteStorageService.BucketName) match {
      case None => Logger.error("Failed fetching bytes: failed to find configured S3 bucketName.")
      case Some(bucketName) => {
        Logger.debug("Loading file from: /" + bucketName + "/" + path)
        try {
          // Download object from S3 bucket
          val rangeObjectRequest = new GetObjectRequest(bucketName, path)
          val objectPortion = this.s3Bucket.getObject(rangeObjectRequest)

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
    * @param ignored unused parameter in this context
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
          this.s3Bucket.deleteObject(bucketName, path)

          // TODO: Perform an additional GET to verify deletion?

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

  /* Reusable handlers for various Exception types */
  def handleUnknownError(err: Exception = null) = {
    if (err != null) {
      Logger.error("An unknown error occurred in the S3ByteStorageService: " + err.toString)
    } else {
      Logger.error("An unknown error occurred in the S3ByteStorageService.")
    }
  }

  def handleIOE(err: IOException) = {
    Logger.error("IOException occurred in the S3ByteStorageService: " + err)
  }

  def handleACE(ace: AmazonClientException) = {
    Logger.error("Caught an AmazonClientException, which " + "means the client encountered " + "an internal error while trying to " + "communicate with S3, " + "such as not being able to access the network.")
    Logger.error("Error Message: " + ace.getMessage)
  }

  def handleASE(ase: AmazonServiceException) = {
    Logger.error("Caught an AmazonServiceException, which " + "means your request made it " + "to Amazon S3, but was rejected with an error response" + " for some reason.")
    Logger.error("Error Message:    " + ase.getMessage)
    Logger.error("HTTP Status Code: " + ase.getStatusCode)
    Logger.error("AWS Error Code:   " + ase.getErrorCode)
    Logger.error("Error Type:       " + ase.getErrorType)
    Logger.error("Request ID:       " + ase.getRequestId)
  }
}
