
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

    if(spaceId.search("Select the space") == 0) {
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

    document.getElementById('curationcreate').setAttribute('action', 'spaces/'+spaceId +'/submit');

    //Submit the form
    $('#curationcreate').submit();

    return true;
}

function cancelEdit(id){
    window.location.href = "/spaces/curations/"+ id ;
}

//Call on Create button click. Move to create a curation object as specified after validating input fields
function updateCuration(id) {
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

    if(spaceId.search("Select the space") == 0) {
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
    //document.getElementById('curationcreate').removeAttribute('action');
    document.getElementById('curationcreate').setAttribute('action', 'spaces/'+spaceId+'/update');

    //Submit the form
    $('#curationcreate').submit();

    return true;
}