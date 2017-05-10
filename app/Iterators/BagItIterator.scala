package Iterators

import java.io.{ByteArrayInputStream, InputStream}
import java.security.{DigestInputStream, MessageDigest}
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.zip.{ZipEntry, ZipOutputStream}

import models.User
import org.apache.commons.codec.binary.Hex

class BagItIterator(pathToFolder : String, collection : Option[models.Collection], zip : ZipOutputStream,
                    md5Bag : scala.collection.mutable.HashMap[String, MessageDigest],
                    md5Files : scala.collection.mutable.HashMap[String, MessageDigest],
                    totalBytes : Long, user : Option[User]) extends Iterator[Option[InputStream]] {

  var file_type = 0

  var bytes : Long = 0L

  private def addBagItTextToZip(pathToFolder : String, totalbytes: Long, totalFiles: Long, zip: ZipOutputStream, collection: models.Collection, user: Option[models.User]) = {
    zip.putNextEntry(new ZipEntry(pathToFolder+"/bagit.txt"))
    val softwareLine = "Bag-Software-Agent: clowder.ncsa.illinois.edu\n"
    val baggingDate = "Bagging-Date: "+(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")).format(Calendar.getInstance.getTime)+"\n"
    val baggingSize = "Bag-Size: " + totalbytes + "\n"
    val payLoadOxum = "Payload-Oxum: "+ totalbytes + "." + totalFiles +"\n"
    val senderIdentifier="Internal-Sender-Identifier: "+collection.id+"\n"
    val senderDescription = "Internal-Sender-Description: "+collection.description+"\n"
    var s:String = ""
    if (user.isDefined) {
      val contactName = "Contact-Name: " + user.get.fullName + "\n"
      val contactEmail = "Contact-Email: " + user.get.email.getOrElse("") + "\n"
      s = softwareLine+baggingDate+baggingSize+payLoadOxum+contactName+contactEmail+senderIdentifier+senderDescription
    } else {
      s = softwareLine+baggingDate+baggingSize+payLoadOxum+senderIdentifier+senderDescription
    }

    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  // If no collection provided, assume this is for Selected download
  private def addBagItTextToZip(pathToFolder : String, totalbytes: Long, totalFiles: Long, zip: ZipOutputStream, user: Option[models.User]) = {
    zip.putNextEntry(new ZipEntry(pathToFolder+"/bagit.txt"))
    val softwareLine = "Bag-Software-Agent: clowder.ncsa.illinois.edu\n"
    val baggingDate = "Bagging-Date: "+(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")).format(Calendar.getInstance.getTime)+"\n"
    val baggingSize = "Bag-Size: " + totalbytes + "\n"
    val payLoadOxum = "Payload-Oxum: "+ totalbytes + "." + totalFiles +"\n"

    val s:String = if (user.isDefined) {
      val senderIdentifier="Internal-Sender-Identifier: user "+user.get.id+"\n"
      val senderDescription = "Internal-Sender-Description: User Dataset Selections\n"
      val contactName = "Contact-Name: " + user.get.fullName + "\n"
      val contactEmail = "Contact-Email: " + user.get.email.getOrElse("") + "\n"
      softwareLine+baggingDate+baggingSize+payLoadOxum+contactName+contactEmail+senderIdentifier+senderDescription
    } else {
      val senderIdentifier="Internal-Sender-Identifier: unknown user\n"
      val senderDescription = "Internal-Sender-Description: User Dataset Selections\n"
      softwareLine+baggingDate+baggingSize+payLoadOxum+senderIdentifier+senderDescription
    }

    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  private def addBagInfoToZip(pathToFolder : String ,zip : ZipOutputStream) : Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(pathToFolder+"/bag-info.txt"))
    val s : String = "BagIt-Version: 0.97\n"+"Tag-File-Character-Encoding: UTF-8\n"
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  private def addManifestMD5ToZip(pathToFolder : String, md5map : Map[String,MessageDigest] ,zip : ZipOutputStream) : Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(pathToFolder+"/manifest-md5.txt"))
    var s : String = ""
    md5map.foreach{
      case (filePath,md) => {
        val current = Hex.encodeHexString(md.digest())+" "+filePath+"\n"
        s = s + current
      }
    }
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  private def addTagManifestMD5ToZip(pathToFolder : String, md5map : Map[String,MessageDigest],zip : ZipOutputStream) : Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(pathToFolder+"/tagmanifest-md5.txt"))
    var s : String = ""
    md5map.foreach{
      case (filePath,md) => {
        val current = Hex.encodeHexString(md.digest())+" "+filePath+"\n"
        s = s + current
      }
    }
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  def setBytes(totalBytes : Long) = {
    bytes = totalBytes
  }

  var is : Option[InputStream] = None

  def hasNext() = {
    if (file_type < 4) {
      true
    } else
      false
  }

  def next = {
    file_type match {
      case 1 => {
        is = collection match {
          case Some(coll) => {
            addBagItTextToZip(pathToFolder,bytes,0,zip,coll,user)
          }
          case None => {
            addBagItTextToZip(pathToFolder,bytes,0,zip,user)
          }
        }

        val md5 = MessageDigest.getInstance("MD5")
        md5Bag.put("bagit.txt",md5)
        file_type = 2
        Some(new DigestInputStream(is.get, md5))

      }
      case 0 => {
        is = addBagInfoToZip(pathToFolder,zip)
        val md5 = MessageDigest.getInstance("MD5")
        md5Bag.put("bag-info.txt",md5)
        file_type = 1
        Some(new DigestInputStream(is.get, md5))

      }
      case 2 => {
        is = addManifestMD5ToZip(pathToFolder,md5Files.toMap[String,MessageDigest],zip)
        val md5 = MessageDigest.getInstance("MD5")
        md5Bag.put("manifest-md5.txt",md5)
        file_type = 3
        Some(new DigestInputStream(is.get, md5))
      }
      case 3 => {
        is = addTagManifestMD5ToZip(pathToFolder,md5Bag.toMap[String,MessageDigest],zip)
        val md5 = MessageDigest.getInstance("MD5")
        md5Bag.put("tagmanifest-md5.txt",md5)
        file_type = 4
        Some(new DigestInputStream(is.get, md5))
      }
      case _ => {
        None
      }
    }
  }
}