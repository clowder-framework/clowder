//Method to remove the CO and redirect back to staging area on completion
function removeCuration(id, isreload, url){

    var request = jsRoutes.controllers.CurationObjects.deleteCuration(id).ajax({
        type: 'DELETE'
    });

    request.done(function (response, textStatus, jqXHR){
        if(isreload === "true")
            window.location.href=url;
        else {
            var obj = $('#'+ id+'-tile').parent();
            $('#masonry').masonry( 'remove', obj );
            $('#masonry').masonry( 'layout' );
        }
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to delete a publication request from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The publication request was not deleted from the system due to : " + errorThrown, "error");
        }
    });
}

function retractCuration(curationId) {
    console.log("Retracting publication request: " + curationId);

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
        notify("The publication request could not be retracted due to: " + jqXHR.responseJSON.message, "error" );
    });
}

function removeCurationFile(id, currentFolder, curationid){
        var request = jsRoutes.api.CurationObjects.deleteCurationFile(curationid, currentFolder, id).ajax({
            type: 'DELETE'
        });

    request.done(function (response, textStatus, jqXHR) {
        $('#'+ id+'-listitem').remove();
        getFiles(curationid);
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a file from the publication request";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("File was not removed from the request due to : " + jqXHR.textResponse, "error");
        }
    });
}

function removeCurationFolder(id, parentCurationObject, parentId){
    var request = jsRoutes.api.CurationObjects.deleteCurationFolder(parentCurationObject, parentId, id).ajax({
        type: 'DELETE'
    });

    request.done(function (response, textStatus, jqXHR) {
        $('#'+ id+'-listitem').remove();
        getFiles(parentCuratonObject);
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a folder from the publication request";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("Folder was not removed due to : " + errorThrown, "error");
        }
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
    if(folderId === "") {
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
        console.error("The following error occurred: " + textStatus, errorThrown);
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

function getFiles(id) {
    var request = jsRoutes.api.CurationObjects.getCurationFiles(id).ajax({
        type: "GET",
        contentType : "application/json"
    }).done(function( response, textStatus, jqXHR ) {
        var totalSize = 0;
        $.each(response.cf, function (i, el) {
            totalSize += this.length;
        });
        var formatAll = [];
        $.each(response.cf, function (i, el) {
            formatAll.push( this.contentType.valueOf());
        });
        var formats = [];
        $.each(formatAll, function(i, el){
            if($.inArray(el, formats) === -1) formats.push(el);
        });
        if(totalSize < 1000000) {
            $("#size").replaceWith('<div id="size">Size: ' + Math.round(totalSize / 1024) + "KB</div>");
        } else {
            $("#size").replaceWith('<div id="size">Size: ' + Math.round(totalSize / 1024 /1024) + "MB</div>");
        }
        $( "#format" ).replaceWith('<div id="format">File Formats: '+formats.join(', ')+'</div>' );

    }).fail(function( ) {
        $( "#size" ).replaceWith("Unknown");
        $( "#format" ).replaceWith("Unknown");

    });

}