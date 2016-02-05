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
    var spaceList = [];
    $('#spaceid').find(":selected").each(function(i, selected) {
        spaceList[i] = $(selected).val()
    });

    //Add errors and return false if validation fails. Validation comes from the host page, passing in the isNameRequired and isDescRequired
    //variables.
    var error = false;
    if (!name.val() && isNameRequired) {
    	$('#nameerror').show();
    	error = true;
    }
    if (!desc.val() && isDescRequired) {
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
    $('#hiddenspace').val(spaceList);

	//Submit the form
    $('#collectioncreate').submit();

    return true;
}

function createChildCollection() {
	//Remove error messages if present
	clearErrors();

	//Update the input we are adding to the form programmatically
	var name = $('#name');
    var desc = $('#description');

    console.log("parent collection id and parent spaces");
    console.log(parentCollectionId);
    console.log(parentSpaceIds);

    var parentColId = parentCollectionId;
    var parentSpaceList = parentSpaceIds;


    var space = $('#spaceid').find(":selected").val();
    var spaceList = [];
    $('#spaceid').find(":selected").each(function(i, selected) {
        spaceList[i] = $(selected).val()
    });

    var parent = $('#parentid').find(":selected").val();
    var collectionList = [];
    $('#parentid').find(":selected").each(function(i,selected) {
        collectionList[i] = $(selected).val()
    });


    //Add errors and return false if validation fails. Validation comes from the host page, passing in the isNameRequired and isDescRequired
    //variables.
    var error = false;
    if (!name.val() && isNameRequired) {
    	$('#nameerror').show();
    	error = true;
    }
    if (!desc.val() && isDescRequired) {
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
    $('#hiddenspace').val(spaceList);
    $('#hiddencollection').val(collectionList);

	//Submit the form
    $('#collectioncreate').submit();

    return true;
}