//This file is referenced by addToExistingDataset.scala.html
//
//It contains functions and callback definitions that are used by the the multi-file-uploader and the existing file attach workflow.
//

//On page load, ensure that everything is in a clean state. Change the buttons to reflect that this is attach and not create.
$(document).ready(function() {
    clearErrors();
    hideStatus();
    $('#existingcreate').html(" Attach Files");
    $('#uploadcreate').html(" Attach Files");
});   

//Remove the error messages that are provided to the user
function clearErrors() {
	$('.error').hide();
}

//Hide the status items
function hideStatus() {
	$('#status').hide();
}

//Tab 1 - Upload New Files related functions below

$(function () {	                	                 
	//Callback for any submit call, whether it is the overall one, or individual files, in the multi-file-uploader
    $('#fileupload').bind('fileuploadsubmit', function (e, data) {    	    	    	    	
    	return uploadFileToExistingDataset(data);    
    });	    
});        

function uploadFileToExistingDataset(data) {
 	
	//Remove error messages if present
	clearErrors();   
    
    //No field errors, so set the input values	                        
    var radios = document.getElementsByName("radiogroup");                        
    for (var elem in radios) {
        if (radios[elem].checked) {
            $('#hiddenlevel').val(radios[elem].value);
        }
    }
     
    var id = $("#hiddenid").val();
    $('#status').html("View the <a href=\"/datasets/" + id + "\">Dataset</a>");
    $('#status').show();
    
    return true;
}

function checkZeroFiles() {
	var numFiles = $('#fileupload').fileupload('option').getNumberOfFiles();	
	if (numFiles == 0) {
		//If no files are specified, show the error. The uploader won't submit anyway, but return false just the same.
		$('#messageerror').html("No files have been added to the upload queue. Please add files to attach.");
    	$('#messageerror').show();
		return false;
	}	
}

//Tab 2 Existing file upload functions below

//Clear all selected items
function clearFiles() {
	$("#filelist option:selected").removeAttr("selected");
}

//Call on Create button click. Move to create a dataset as specified, and attach any files if they are specified.
function attachFiles() {			
	//Remove error messages if present
	clearErrors();		
    
	var id = $("#hiddenid").val();
    var ids = $("#filelist option:selected").map(function(){ return this.value }).get().join(",");    		
	var jsonData = null;
	var request = null;	
		
	//In this case, the process is simply attaching files
	if (ids.length == 0) {
		hideStatus();
		//No files selected, show error.
		$('#messageerror').html("No files selected to attach. Please select some files.");
    	$('#messageerror').show();
    	return false;
	}
	
	jsonData = JSON.stringify({"datasetid":id, "existingfiles":ids});
	request = jsRoutes.api.Datasets.attachMultipleFiles().ajax({
        data: jsonData,
        type: 'POST',
        contentType: "application/json",
    });
	                        	                        
    request.done(function (response, textStatus, jqXHR){	    
    	//Successful attachment of multiple files
        console.log("Successful response from attachMultipleFiles.");
        $('#status').html("Attach files successful. Go to the <a href=\"/datasets/" + id + "\">Dataset</a>");
        $('#status').show();
    });


    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to attach files to a dataset.";                                
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
        	$('#messageerror').html("Error in attaching exising files to a dataset. " + errorThrown);
        	$('#messageerror').show();
        }  
    });
	
    
    return false;
}