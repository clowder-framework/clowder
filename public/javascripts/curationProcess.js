//Method to remove the CO and redirect back to staging area on completion
function removeCuration(spaceId, curationId){
    console.log(spaceId);

    var request = jsRoutes.controllers.CurationObjects.deleteCuration(curationId).ajax({
        type: 'DELETE'
    });

    request.done(function (response, textStatus, jqXHR){
        window.location.href= "/spaces/"+ spaceId +"/stagingArea";
        console.log(response);
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

function updatePageAndFolder(idx, folderId){
    if(folderId != "") {
        location.hash = "folderId="+folderId+"&page="+idx;
    } else {
        location.hash="page="+idx;
    }
    $(window).trigger("hashchange");
}

function getUpdatedFilesAndFolders(curationObject, limit) {
    parseHash();
    if(folderId == "") {
        folderId = "None";
    }

    var request = jsRoutes.controllers.CurationObjects.getUpdatedFilesAndFolders(curationObject, folderId, limit, pageIndex).ajax({
        type: 'GET'
    });

    request.done(function(response, textStatus, jsXHR){
        $('#files').html("");
        $('#files').html(response);
    });
    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to see files and folders.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("Error in getting more files and folders. : " + errorThrown, "error");
        }
    });

}

function parseHash() {
    var hash = location.hash.split('#')[1];
    var folderSet = false;
    var pageSet = false;
    if(hash != undefined) {
        var values = hash.split('&');
        for (var i = 0; i < values.length; i++){
            var temp = values[i].split('=');
            if(temp[0] == 'folderId') {
                folderId = temp[1];
                folderSet = true
            }
            if(temp[0] == 'page'){
                pageIndex = temp[1];
                pageSet = true
            }
        }
    }
    if(!folderSet) {
        folderId ='';
    }
    if(!pageSet) {
        pageIndex = 0;
    }
}