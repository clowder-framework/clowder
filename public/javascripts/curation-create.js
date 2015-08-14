
//Clear the input fields that are associated with this panel
function clearFields() {
    $('#name').val("");
    $('#description').val("");
}

//Call on Create button click. Move to create a curation object as specified after validating input fields
function createCuration() {
    //Remove error messages if present
    //////////////clearErrors();

    //Update the input we are adding to the form programmatically
    var name = $('#name');
    var desc = $('#description');

    var datasets = document.getElementById('datasetid');
    var Datasets = "";

    for (var i = 0; i < datasets.options.length; i++) {
        if (datasets.options[i].selected ) {
            Datasets = Datasets  +datasets.options[i].value + ",";
        }
    }

    var collections = document.getElementById('collectionid');
    var Collections = "";

    for (var i = 0; i < collections.options.length; i++) {
        if (collections.options[i].selected ) {
            Collections = Collections  + collections.options[i].value + ",";
        }
    }

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
    $('#hiddendatasets').val(Datasets);
    $('#hiddencollections').val(Collections);

    //Submit the form
    $('#curationcreate').submit();

    return true;
}