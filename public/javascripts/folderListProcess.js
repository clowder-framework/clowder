function removeFolder(parentDataset, folderId) {
    var request = jsRoutes.api.Folders.deleteFolder(parentDataset, folderId).ajax({
        type: 'DELETE'
    });
    request.done(function (response, textStatus, jqXHR) {
        $('#'+response.folderId+'-listitem').remove();
        notify("Folder removed successfully", "success", false, 2000 );
    });
    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: "+textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a folder from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The folder was not removed due to : " + errorThrown, "error");
        }

    });
}

var cur_names=[];
var cur_hrefs=[];
function updateFolderName(parentDataset, folderId) {
    cur_names[folderId] = $('#'+folderId+'-name').text();
    cur_hrefs[folderId] = $('#'+folderId+'-name a').attr('href');
    var class_name = "edit_title_"+folderId;
    $('<span class="inline '+class_name+'"></span>').insertAfter($('#'+folderId+'-name'));
    $('.'+class_name).append('<input type="text" id = "title_input_'+folderId+'" class="form-control" required/>');
    $('.'+class_name).append('<div class="hiddencomplete" aria-hidden="true" id="title-error_'+folderId+'"> <span class="error"> Name is required </span> </div>');
    $('.'+class_name).append('<button id="update_title_'+folderId+'" class= "btn btn-sm btn-primary btn-margins" onclick="saveFolderName(\''+parentDataset+'\',\''+folderId+'\')"> <span class="glyphicon glyphicon-send"></span> Save </button>');
    $('.'+class_name).append('<button id="cancel_title_'+folderId+'" onclick="cancelFolderName(\''+folderId+'\')" class="btn btn-sm edit-tab btn-default btn-margins"> <span class="glyphicon glyphicon-remove"></span>Cancel </button>');
    $('#title_input_'+folderId).val(cur_title);
    $('#'+folderId+'-name').text("");
    $('#h-edit-'+folderId).addClass("hiddencomplete");
    $('#h-edit-'+folderId).css("display", "none");
}

function saveFolderName(parentDataset, folderId) {
    if($('#title_input_'+folderId).val().length < 1) {
        $('#title-error_'+folderId).show();
        return false;
    }

    var name =$('#title_input_'+folderId).val();
    var encName = htmlEncode(name);
    jsonData = JSON.stringify({"name": encName});

    var request = jsRoutes.api.Folders.updateFolderName(parentDataset, folderId).ajax({
        data: jsonData,
        type: 'PUT',
        contentType: "application/json"
    });
    request.done(function(response, textStatus, jqXHR){
        $('#'+folderId+'-name').html('<a href="'+cur_hrefs[folderId]+'">'+encName.replace(/\n/g, "<br>")+'</a>');
        $('.edit_title_'+folderId).remove();
        $('#'+folderId+'-name').css("display", "inline");
        $('#h-edit-'+folderId).removeClass("inline");
        $('#h-edit-'+folderId).css("display", "");
        $('#folder-title-'+folderId).mouseleave();
        notify("Folder name updated successfully", "success", false, 2000 );
    });
    request.fail(function(jqXHR, textStatus, errorThrown) {
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "Yoy must be logged in to update the information about a folder.";
        if(!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The folder information was not updated due to: "+ errorThrown, "error");
        }
    });
}

function cancelFolderName(folderId) {
    $('#'+folderId+'-name').html('<a href="'+cur_hrefs[folderId]+'">'+cur_names[folderId]+'</a>');
    $('.edit_title_'+folderId).remove();
    $('#'+folderId+'-name').css("display", "inline");
    $('#h-edit-'+folderId).removeClass("inline");
    $('#h-edit-'+folderId).css("display", "");
    $('#folder-title-'+folderId).mouseleave();
}