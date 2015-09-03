
//Clear the input fields that are associated with this panel
function clearFields() {
    $('#name').val("");
    $('#description').val("");
}

//Call on Create button click. Move to create a curation object as specified after validating input fields
function createCuration() {
    //Remove error messages if present
    $('.error').hide();

    //Update the input we are adding to the form programmatically
    var name = $('#name');
    var desc = $('#description');

    var space = document.getElementById("spaceid");

    var i = space.selectedIndex;
    var spaceId =  space.options[i].value;


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

    if(spaceId.search("elect the space") > 0) {
        $('#spaceerror').show();
        error = true;
    }


    if (error) {
        return false;
    }

    var encName = htmlEncode(name.val());
    var encDescription = htmlEncode(desc.val());
    $('#hiddenname').val(encName);
    $('#hiddendescription').val(encDescription);
    $('#hiddenspace').val(spaceId);

    //Submit the form
    $('#curationcreate').submit();

    return true;
}