//This file is referenced by newDataset.scala.html
//
//It contains functions and callback definitions that are used by the create dataset workflow, and the multi-file-uploader.
//

//Flag to signal that dataset creation has started                
var asynchStarted = false;
//The id of the new dataset, to share among the file uploads
var id = "__notset";
//The original data to be submitted, for the initial file that handles the ajax call
//to create the dataset
var origData = null;

//On page load, ensure that everything is in a clean state
$(document).ready(function() {
    clearErrors();
    enableFields();
    resetDatasetItems();                        
});   

//Disable common input fields
function disableFields() {
	$('#name').attr("disabled", true);
	$('#description').attr("disabled", true);
	$("input[name=radiogroup]").attr('disabled', true);                    	
}

//Enable common input fields
function enableFields() {
	$('#name').attr("disabled", false);
    $('#description').attr("disabled", false);
    $("input[name=radiogroup]").attr('disabled', false);                    	
}

//Remove the error messages that are provided to the user
function clearErrors() {
	$('.error').hide();
}

//Reset the interface to a state where user input can be provided again
function resetDatasetItems() {
	asynchStarted = false;
	id = "__notset";
	origData = null;
	//Ensure both tabs are shown
	$('#tab1anchor').show();
	$('#tab2anchor').show();
	$('#status').hide();
}

//Empty the common input elements
function clearFields() {
	$('#name').val("");
	$('#description').val("");
}

//Reset the common elements to enabled and the dataset specific variables to their starting state
function resetValues() {
	enableFields();
	resetDatasetItems();
	clearFields();
	$('#everywhere').prop('checked', true);
}

//Tab 1 - Upload New Files related functions below

//Utility method to allow calls for files to be uploaded to wait until the dataset ID is
//in place before finally proceeding with their submit.
function holdForId(data) {   
   setTimeout(function(){
	   //Only proceed to hold and submit if the asynchStarted value remains true.
	   //Otherwise, it has been reset and there is no reason to continue to hold.
	   if (asynchStarted) {
	      if (id == "__notset") {
	    	  // recurse
	          holdForId(data); 
	      }
	      else {     	  
	    	  data.submit();
	      }
	   }
  }, 500);
}

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

function createEmptyDataset(data) {
 	
	//Remove error messages if present
	clearErrors();
	
	//Disable input elements
	disableFields();
	
	//Hide the other tab
	$('#tab2anchor').hide();
	
	//Update the input we are adding to the form programmatically      
	var name = $('#name');
    var desc = $('#description');
    
    //Add errors and return false if validation fails
    var error = false;
    if (!name.val()) {
    	$('#nameerror').show();
    	error = true;
    }
    if (!desc.val()) {                                
        $('#descerror').show();
        error = true;
    }
    if (error) {
    	if (data != null) {
	    	//On error, re-enable things to allow the user to fix items
	    	data.context.find('button').prop('disabled', false);
    	}
    	enableFields();
    	//Also, reset the dataset elements, since the workflow is starting over.
    	resetDatasetItems();
    	return false;
    }
    
    //No field errors, so set the input values	                        
    var radios = document.getElementsByName("radiogroup");                        
    for (var elem in radios) {
        if (radios[elem].checked) {
            $('#hiddenlevel').val(radios[elem].value);
        }
    }
    
    var encName = htmlEncode(name.val());
	var encDescription = htmlEncode(desc.val());
    
    $('#hiddenname').val(encName);
    $('#hiddendescription').val(encDescription);
    //Set the ID as it currently stands
    $('#hiddenid').val(id);
   
    if (id == "__notset") {
    	//Case for the primary file that is submitted. It will create the dataset and obtain the id.        	
    	var jsonData = JSON.stringify({"name":encName, "description":encDescription});
        var request = null;		                         	                        
        request = jsRoutes.api.Datasets.createEmptyDataset().ajax({
            data: jsonData,
            type: 'POST',
            contentType: "application/json",
        });
    	                        	                        
        request.done(function (response, textStatus, jqXHR){	                            
            //Sucessful creation of the dataset. Set the id so that all files can
            //proceed to finish their submit.
            id = response["id"];
            console.log("Successful response from createEmptyDataset. ID is " + id);
            $('#hiddenid').val(id);   
            
            if (asynchStarted) {
	            //Now call the submit for the primary file that was submitted that triggered the dataset
	            //creation.
	            origData.submit();
            }
            
            $('#status').html("Creation successful. Go to the <a href=\"/datasets/" + id + "\">Dataset</a>");
            $('#status').show();
        });


        request.fail(function (jqXHR, textStatus, errorThrown){
            console.error("The following error occured: " + textStatus, errorThrown);
            var errMsg = "You must be logged in to create a new dataset.";                                
            if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            	$('#messageerror').html("Error in creating dataset. : " + errorThrown);
            	$('#messageerror').show();
            	if (data != null) {
	            	//On error, re-enable things to allow the user to fix items
	            	data.context.find('button').prop('disabled', false);
            	}
            	enableFields();
            	//Also, reset the dataset elements, since the workflow is starting over.
            	resetDatasetItems();
            }  
        });
        //This block is the primary file, so don't submit yet, don't re-enable the buttons either.
        //The submission of this data will occur on the successful callback for the dataset creation.
        return false;
    }
    return true;
}

function checkZeroFiles() {
	var numFiles = $('#fileupload').fileupload('option').getNumberOfFiles();
	//Only needs to be checked if asynch hasn't already been started, otherwise dataset already created.
	if (!asynchStarted && id == "__notset") {
		if (numFiles == 0) {
			//Here, attempt to create the dataset since the user has simply clicked the button with no files added.
			createEmptyDataset(null);
		}
	}
}

//Existing file upload functions below

//Clear all selected items
function clearFiles() {
	$("#filelist option:selected").removeAttr("selected");
}

//Call on Create button click. Move to create a dataset as specified, and attach any files if they are specified.
function attachFiles() {			
	//Remove error messages if present
	clearErrors();
	
	//Disable input elements
	disableFields();
	
	//Hide the other tab
	$('#tab1anchor').hide();
	
	//Update the input we are adding to the form programmatically      
	var name = $('#name');
    var desc = $('#description');
    
    //Add errors and return false if validation fails
    var error = false;
    if (!name.val()) {
    	$('#nameerror').show();
    	error = true;
    }
    if (!desc.val()) {                                
        $('#descerror').show();
        error = true;
    }
    if (error) {
    	enableFields();
    	$('#tab1anchor').show();
    	return false;
    }
    
    var ids = $("#filelist option:selected").map(function(){ return this.value }).get().join(",");
    //Create the empty dataset, but pass the existing file ids if present, so it knows to append those afterwards
	console.log("About to call createEmptyDataset for existing files, ids length is " + ids.length)
	
	var encName = htmlEncode(name.val());
	var encDescription = htmlEncode(desc.val());
	var jsonData = null;
	if (ids.length == 0) {
		jsonData = JSON.stringify({"name":encName, "description":encDescription});
	}
	else {
		jsonData = JSON.stringify({"name":encName, "description":encDescription, "existingfiles":ids});
	}	
	var datasetId = null;
    var request = null;		                         	                        
    request = jsRoutes.api.Datasets.createEmptyDataset().ajax({
        data: jsonData,
        type: 'POST',
        contentType: "application/json",
    });
	                        	                        
    request.done(function (response, textStatus, jqXHR){	    
    	//Successful creation and file attachment. Update the staus label accordingly.
        datasetId = response["id"];
        console.log("Successful response from createEmptyDataset existing files. ID is " + datasetId);
        $('#status').html("Creation successful. Go to the <a href=\"/datasets/" + datasetId + "\">Dataset</a>");
        $('#status').show();
    });


    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to create a new dataset.";                                
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
        	$('#messageerror').html("Error in creating dataset with exising files. : " + errorThrown);
        	$('#messageerror').show();
        }  
    });
    
    return false;
}