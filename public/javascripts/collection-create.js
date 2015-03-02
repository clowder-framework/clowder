//Clear the input fields that are associated with this panel
function clearFields() {
	$('#name').val("");
	$('#description').val("");
}

//Remove the error messages that are provided to the user
function clearErrors() {
	$('.error').hide();
}

//Call on Create button click. Move to create a collection as specified after validating input fields
function createCollection() {			
	//Remove error messages if present
	clearErrors();		    
						
	//Update the input we are adding to the form programmatically      
	var name = $('#name');
    var desc = $('#description');
    var space = $('#spaceid').find(":selected").val();
    
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
    	return false;
    }
    
    var encName = htmlEncode(name.val());
	var encDescription = htmlEncode(desc.val());
	$('#hiddenname').val(encName);
    $('#hiddendescription').val(encDescription);
    $('#hiddenspace').val(space);
    
	//Submit the form
    $('#collectioncreate').submit();
    
    return true;
}