//Call needed for the new file upload page, in order to ensure that the user's authentication hasn't timed out.
//
//It requires the loading page to also bring in the javascript/errorRedirect.js 
//

$(function () {	                	                 
	//Callback for any submit call, whether it is the overall one, or individual files, in the multi-file-uploader
    $('#fileupload').bind('fileuploadsubmit', function (e, data) {    	    	
    	//Perform authentication check
    	var request = null;		                         	                        
        request = jsRoutes.api.Users.getUser().ajax({
            type: 'GET',
            contentType: "application/json"
        });
    	                        	                        
        request.done(function (response, textStatus, jqXHR){	                            
            //Sucessful call, so authenticated. Need to simply ensure that we have a user. It always should be there in
        	//this case, but log the odd corner case.
            var responseText = jqXHR.responseText;
            console.log("jqXHR.responseText for file uploader auth check is " + responseText);               
            
            if (responseText == "No user found") {
	            //The weird corner case - log it and alert for now
	            console.log("Odd corner case in file uploader. Authenticated but no user found.");
	            //Return false
	            return false;
            }
            else {
            	//User present and authentication successful, so proceed to submit the files
            	return true;
            }
                        
        });


        request.fail(function (jqXHR, textStatus, errorThrown){
            console.error("The following error occured: " + textStatus, errorThrown);
            var errMsg = "You must be logged in to upload new files.";                                
            if (!checkErrorAndRedirect(jqXHR, errMsg)) {            	
            	console.log("Different error message on failure.");
            	alert("ERROR: " + jqXHR.responseText);
            }  
            return false;
        });
    	
    	/*
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
    	*/    
    });	    
});        
