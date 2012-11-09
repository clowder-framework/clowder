package repository
import java.io.File
import java.io.InputStream

/**
 * Client service to access file store.
 * 
 * @author Luigi Marini
 *
 */
trait FileServiceComponent {
  this: DiskFileRepositoryComponent => 
    
  val fileService: FileService
  
  class FileService {
    def save(inputStream: InputStream, filename: String) = fileRepository.save(inputStream, filename)
    def get(id: String):Option[(InputStream,String)] = fileRepository.get(id)
  }
}