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
