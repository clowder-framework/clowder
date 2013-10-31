package fileutils;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import play.Logger;

import org.apache.commons.io.FileUtils;


public class FilesUtils {

	public static String getMainFileTypeOfZipFile(File compressedFile, String filename, String containerType){
		
		String mainFileType = "multi/files-zipped";
		
		try {
			if(filename.startsWith("MEDICI2DATASET_") && containerType.equals("dataset"))
				return "multi/files-zipped";
			
			ZipFile zipFile = new ZipFile(compressedFile);			
			Enumeration zipEntries = zipFile.entries();
			
            while (zipEntries.hasMoreElements()) {                            	
                String fileName = ((ZipEntry)zipEntries.nextElement()).getName();
                if(fileName.endsWith(".x3d")){
                	zipFile.close();
                	mainFileType = "model/x3d-zipped";
                	return mainFileType;
                }
                if(fileName.endsWith(".obj")){
                	zipFile.close();
                	mainFileType = "model/obj-zipped";
                	return mainFileType;
                }
                if(fileName.endsWith(".sfmdataset")){
                	zipFile.close();
                	mainFileType = "model/sfm-zipped";
                	return mainFileType;
                } 
            }
            zipFile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			return ("ERROR: " + e.getMessage());
		}		
		return mainFileType;
	}
	
	public static void dumpFile(File fileToDump, String fileId, String fileName){
		new FilesUtils().new DumpFile(fileToDump, fileId, fileName).run();		
	}
	
	class DumpFile implements Runnable{
		
		private final File fileToDump;
		private final String fileId;
		private final String fileName;
		
		public DumpFile(File fileToDump, String fileId, String fileName){
			this.fileToDump = fileToDump;
			this.fileId = fileId;
			this.fileName = fileName;
		}
		
		public void run() {
			try{
				String fileSep = System.getProperty("file.separator");
				String fileDumpDir = play.Play.application().configuration().getString("filedump.dir");
				if(!fileDumpDir.endsWith(fileSep))
					fileDumpDir = fileDumpDir + fileSep;
				fileDumpDir = fileDumpDir + fileId.charAt(fileId.length()-3) + fileSep + fileId.charAt(fileId.length()-2)+fileId.charAt(fileId.length()-1)
							+ fileSep + fileId + fileSep + fileName;
						
				FileUtils.copyFile(fileToDump, new File(fileDumpDir));
			}catch(Exception e){
				Logger.error("Could not save a dump of the file");
			}
		}
		
	}
	
	
	
}