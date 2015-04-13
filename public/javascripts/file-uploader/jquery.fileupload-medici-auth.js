//Call needed for the new file upload page, in order to ensure that the user's authentication hasn't timed out.
//
//It requires the loading page to also bring in the javascript/errorRedirect.js 
//

$(function () {	                	                 
	//Callback for any submit call, whether it is the overall one, or individual files, in the multi-file-uploader
    $('#fileupload').bind('fileuploadsubmit', function (e, data) {    	    	
    	//First, check if the id value is set and if an asych call is started. 
    	if (asynchStarted && id == "__notset") {	                    		
            //If so, wait for the ID to be set.
    		holdForId(data);
    		return false;
    	}
    	else {
    		  //In this case either asynch has started or the ID is set, so trip the flag 
    		  //and set the original data in the first case, and this is basically a no-op
    		  //in the latter case.	                    		  
    		  asynchStarted = true;
    		  origData = data;
    	}
    	
    	return createEmptyDataset(data);    
    });	    
});        
