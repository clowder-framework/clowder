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

$(document).ready(function() {
    clearErrors();
    enableFields();
    resetDatasetItems();                        
});   

function disableFields() {
	$('#name').attr("disabled", true);
	$('#description').attr("disabled", true);
	$("input[name=radiogroup]").attr('disabled', true);                    	
}

function enableFields() {
	$('#name').attr("disabled", false);
    $('#description').attr("disabled", false);
    $("input[name=radiogroup]").attr('disabled', false);                    	
}

function clearErrors() {
	$('.error').hide();
}

function resetDatasetItems() {
	asynchStarted = false;
	id = "__notset";
	origData = null;
	//Ensure both tabs are shown
	$('#tab1anchor').show();
	$('#tab2anchor').show();
}

function clearFields() {
	$('#name').val("");
	$('#description').val("");
}

function resetValues() {
	enableFields();
	resetDatasetItems();
	clearFields();
	$('#everywhere').prop('checked', true);
}

//Utility method to allow calls for files to be uploaded to wait until the dataset ID is
//in place before finally proceeding with their submit.
function holdForId(data) {
   console.log("In hold for Id");
   setTimeout(function(){
      if (id == "__notset") {
    	  console.log("About to recurse");
    	  // recurse
          holdForId(data); 
      }
      else {
    	  console.log("holdForId - submitting data");
    	  data.submit();
      }                   	    
  }, 500);
}

$(function () {	                	                 
	//Callback for any submit call, whether it is the overall one, or individual files
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
    	console.log("submit callback")	                    	
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
        	data.context.find('button').prop('disabled', false);
        	return false;
        }
        
        //No field errors, so set the input values	                        
        var radios = document.getElementsByName("radiogroup");
        console.log('submit binding');                        
        for (var elem in radios) {
            if (radios[elem].checked) {
                $('#hiddenlevel').val(radios[elem].value);
            }
        }
        $('#hiddenname').val(name.val());
        $('#hiddendescription').val(desc.val());
        console.log("-- setting ID in submit callback, it is " + id);
        $('#hiddenid').val(id);
       
        if (id == "__notset") {
        	//Case for the primary file that is submitted. It will create the dataset and obtain the id.
        	console.log("About to call ajax for createEmptyDataset")
        	var jsonData = JSON.stringify({"name":name.val(), "description":desc.val()});
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
                //Now call the submit for the primary file that was submitted that triggered the dataset
                //creation.
                origData.submit();
            });


            request.fail(function (jqXHR, textStatus, errorThrown){
                console.error("The following error occured: " + textStatus, errorThrown);
                var errMsg = "You must be logged in to create a new dataset.";                                
                if (!checkErrorAndRedirect(jqXHR, errMsg)) {
                	$('#messageerror').html("Error in creating dataset. : " + errorThrown);
                	$('#messageerror').show();
                }  
            });
            //This block is the primary file, so don't submit yet, don't re-enable the buttons either.
            //The submission of this data will occur on the successful callback for the dataset creation.
            return false;
        }

    });
	
    $('#fileupload').bind('fileuploaddone', function (e, data) {	   
    	console.log("--- In DONE callback ---");
    });
});        