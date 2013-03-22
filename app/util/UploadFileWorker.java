package util;

import java.io.PipedInputStream;

import com.mongodb.casbah.gridfs.GridFS;
import com.mongodb.casbah.gridfs.GridFSInputFile;


public class UploadFileWorker extends Thread {

	PipedInputStream pis;

	public String contentType = "";

	private GridFS gridFS;

	public UploadFileWorker(PipedInputStream pis, GridFS gridFS) {
		super();
		this.pis = pis;
		this.gridFS = gridFS;
	}

	public void run() {
		try {
			// myApi.store(pis, path, contentType);
			GridFSInputFile file = gridFS.createFile(pis);
			System.out.println("File uploaded" + file.get("_id"));
			pis.close();
		} catch (Exception ex) {
			ex.printStackTrace();
			try {
				pis.close();
			} catch (Exception ex2) {
			}
		}
	}
}
