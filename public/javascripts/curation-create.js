
//Clear the input fields that are associated with this panel
function clearFields() {
    $('#name').val("");
    $('#description').val("");
    $('.creators .creator-delete a').click();
    $('.creators .control-group input').each(function (index) {
        $(this).val("");
    });
}

// Call on Create button click. Move to create a curation object as specified
// after validating input fields
function createCuration() {
    document.getElementById('curationcreate').setAttribute('action', 'spaces/'+$('#spaceid option:selected').val() +'/submit');
    return changeCuration();
}

// Call on Create button click. Move to create a curation object as specified
// after validating input fields
function updateCuration() {
    document.getElementById('curationcreate').setAttribute('action', 'spaces/'+$('#spaceid option:selected').val()+'/update');
    return changeCuration();
}

// Common code for create and update
function changeCuration() {
    // Remove error messages if present
    $('.error').hide();

    // Update the input we are adding to the form programmatically
    var name = $('#name');
    var desc = $('#description');
    var spaceId=$('#spaceid option:selected').val();


    // Add errors and return false if validation fails. Validation comes from
	// the host page, passing in the isNameRequired and isDescRequired
    // variables.
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
    var creators = [];
    $('.authname').each(function (index) {
        if($(this).attr('data-creator').trim() != "") {
            creators.push(encodeURIComponent($(this).attr('data-creator').trim()));
        }
    });

    if(creators.length == 0) {
        $('#creatorerror').show();
        error = true
    }

    if (error) {
        return false;
    }

    var encName = htmlEncode(name.val());
    var encDescription = htmlEncode(desc.val());
    $('#hiddenname').val(encName);
    $('#hiddendescription').val(encDescription);
    $('#hiddencreators').val(creators);

    // Submit the form
    $('#curationcreate').submit();

    return true;
}

function cancelEdit(id){
    window.location.href = "/spaces/curations/"+ id ;
}

