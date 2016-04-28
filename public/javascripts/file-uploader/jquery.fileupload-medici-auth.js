
//The original data to be submitted, for the initial file that handles the ajax call
//to create the dataset
var origData = null;

//Flag to determine if authentication is being checked
var authInProcess = false;

//Call needed for the new file upload page, in order to ensure that the user's authentication hasn't timed out.
//This callback is invoked on user add of files, to try to catch it as early as possible. This code exists in
//dataset-create-fileuploader.js as well.
//
//It requires the loading page to also bring in the javascript/errorRedirect.js 
//
$(function () {	                	                 
	//Callback for any submit call, whether it is the overall one, or individual files, in the multi-file-uploader
    $('#fileupload').bind('fileuploadadd', function (e, data) {
    	
    	if (authInProcess) {    		
    		return holdForAuthAdd();    		
    	}
    	else {
    		//No auth started yet, so we'll start
    		authInProcess = true;
    	}
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
            authInProcess = false;
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
            console.error("addCallback - fileUploader - The following error occured: " + textStatus, errorThrown);
            authInProcess = false;
            var errMsg = "You must be logged in to upload new files.";                                
            if (!checkErrorAndRedirect(jqXHR, errMsg)) {            	
            	console.log("Different error message on failure.");
            	alert("ERROR: " + jqXHR.responseText);
            }  
            return false;
        });    	
    });	    
});        


//Utility method to allow calls for files to be uploaded to wait until authentication is
//verified before finally proceeding with the add. In reality, the holds will be canceled on 
//an authentication failure, since the redirect to login will end them. This code exists in
//dataset-create-fileuploader.js as well.
function holdForAuthAdd(data) { 
	counter = 0;
	function checkAuth() {		
		if (authInProcess) {
			counter++;
			if (counter > 20) {
				setTimeout(checkAuth, 500);
			}
			else {
				return false;
			}
		}
		else {
			return true;
		}
	}
	checkAuth();	
}

//Call needed for the new file upload page, in order to ensure that the user's authentication hasn't timed out.
//This callback is invoked on user submit of files, to catch the case where files were added, but for some reason 
//a timeout occurred in between that and when they try to submit.
//
//It requires the loading page to also bring in the javascript/errorRedirect.js 
//
$(function () {	                	                 
	//Callback for any submit call, whether it is the overall one, or individual files, in the multi-file-uploader
  $('#fileupload').bind('fileuploadsubmit', function (e, data) {
  	
  	if (authInProcess) {    		
  		return holdForAuthSubmit();    		
  	}
  	else {
  		//No auth started yet, so we'll start
  		authInProcess = true;
  		origData = data;
  	}
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
          authInProcess = false;
          if (responseText == "No user found") {
	            //The weird corner case - log it and alert for now
	            console.log("Odd corner case in file uploader. Authenticated but no user found.");
	            //Return false
	            return false;
          }
          else {
          	//User present and authentication successful, so proceed to submit the files
          	origData.submit();
          	return true;
          }
                      
      });


      request.fail(function (jqXHR, textStatus, errorThrown){
          console.error("submitCallback - fileUploader - The following error occured: " + textStatus, errorThrown);
          authInProcess = false;
          var errMsg = "You must be logged in to upload new files.";                                
          if (!checkErrorAndRedirect(jqXHR, errMsg)) {            	
          	console.log("Different error message on failure.");
          	alert("ERROR: " + jqXHR.responseText);
          }  
          return false;
      });    	
  });	    
});        


//Utility method to allow calls for files to be uploaded to wait until authentication is
//verified before finally proceeding with their submit. In reality, the holds will be canceled on 
//an authentication failure, since the redirect to login will end them.
function holdForAuthSubmit(data) { 
	counter = 0;
	function checkAuth() {		
		if (authInProcess) {
			counter++;
			if (counter > 20) {
				setTimeout(checkAuth, 500);
			}
			else {
				return false;
			}
		}
		else {
			data.submit();		
			return true;
		}
	}
	checkAuth();	
}
