package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import play.Logger;

public class StreamGobbler  extends Thread {

	protected InputStream is;
    protected String type;
    
    public StreamGobbler(InputStream is, String type)
    {
        this.is = is;
        this.type = type;
    }
    
    public void run()
    {
        try
        {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line=null;
            if(type.equals("INFO"))
            	while ( (line = br.readLine()) != null)
            		Logger.info(line);
            else if(type.equals("ERROR"))
            	while ( (line = br.readLine()) != null)
            		Logger.error(line);
            } catch (IOException ioe)
              {
                ioe.printStackTrace();  
              }
    }
	
}
