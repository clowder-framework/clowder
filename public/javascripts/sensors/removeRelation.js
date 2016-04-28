function removeRelation(relation_id) {

    var url = jsRoutes.api.Relations['delete'](relation_id).url;
    var request = $.ajax({
        url: url,
        type: 'DELETE'
    });

    request.done(function (response, textStatus, jqXHR){
        console.log('successfully deleted relation');
        window.location.reload();
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        if (errorThrown == 'Unauthorized') {
          window.alert("You are not authorized to remove the relationship.");
        }
    });

    return false;
}

window['removeRelation'] = removeRelation;