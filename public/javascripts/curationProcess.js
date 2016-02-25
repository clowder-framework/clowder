//Method to remove the CO and redirect back to staging area on completion
function removeCuration(id, isreload, url){

    var request = jsRoutes.controllers.CurationObjects.deleteCuration(curationId).ajax({
        type: 'DELETE'
    });

    request.done(function (response, textStatus, jqXHR){
        if(isreload == true)
            window.location.href=url;
        else {
            $('#'+ id+'-tile').remove();
        }
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to delete a curation object from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The curation object was not deleted from the system due to : " + errorThrown, "error");
        }
    });
}

function retractCuration(curationId) {
    console.log("Retracting curation Object: " + curationId);

    var request = jsRoutes.api.CurationObjects.retractCurationObject(curationId).ajax({
        type: 'Delete'
    });

    request.done(function(response, texStatus, jqXHR) {
        $('#'+curationId).removeClass('submitted');
        $('#'+curationId).addClass('in-curation');
        $('#'+curationId).html("In Curation");
        $('#ul_'+curationId).remove();
        notify(response.message, "success", false, 2000);
    });

    request.fail(function(jqXHR, textStatus, errorThrown) {
       console.error("The following error ocurred: " + textStatus, errorThrown);
        notify("The curation object could not be retracted due to: " + jqXHR.responseJSON.message, "error" );
    });
}

