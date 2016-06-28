function moveFromDatasetToDataset(folderFromId, datasetFromId, fileId) {
    var selectedId = $("#datasetAddSelect").val();
    if (!selectedId) return false;

    var url;
    if (folderFromId) {
        url = jsRoutes.api.Folders.moveFileToDataset(selectedId,folderFromId,fileId);
    } else {
        //url = jsRoutes.api.Datasets.moveFileBetweenDatasets(datasetFromId, selectedId, fileId);
    }
    var request = url.ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        console.log(selectedId, folderFromId, fileId);
        $("#datasetAddSelect").select2("val", "");

        window.location.reload(false);
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to move a file to a dataset.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The file was not moved to the dataset due to the following : " + errorThrown, "error");
        }
    });
}

function moveFromToDataset(folderFromId, datasetFromId, fileId) {
    var selectedId = $("#datasetAddSelect").val();
    if (!selectedId) return false;
    var selectedName = $("#datasetAddSelect option:selected").text();
    selectedName = selectedName.replace(/\n/g, "<br>");

    var request = jsRoutes.api.Datasets.attachExistingFile(selectedId, fileId).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR) {
        var o =$.parseJSON(jqXHR.responseText);
        $("#datasetAddSelect").select2("val", "");

        if (folderFromId) {
            removeFileFromFolderAndRedirect(datasetFromId, folderFromId, fileId, "true", null);
        } else {
            removeFileFromDatasetAndRedirect(datasetFromId, fileId, "true", null);
        }
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to move a file to a dataset.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The file was not moved to the dataset due to the following : " + errorThrown, "error");
        }
    });
}

function addToDataset(fileId) {
    var selectedId = $("#datasetAddSelect").val();
    if (!selectedId) return false;
    var selectedName = $("#datasetAddSelect option:selected").text();
    selectedName = selectedName.replace(/\n/g, "<br>");

    var request = jsRoutes.api.Datasets.attachExistingFile(selectedId, fileId).ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR) {
        var o =$.parseJSON(jqXHR.responseText);
        var txt = '<div id="col_'+selectedId+'" class="row bottom-padding">' +
            '<div class="col-md-2"></div>' +
            '<div class="col-md-10">' +
            '<div><a href="'+jsRoutes.controllers.Datasets.dataset(selectedId).url+'" id='+selectedId+' class ="dataset">'+selectedName+'</a> ' +
            '</div>';
        txt = txt + '</div>';
        txt = txt + '</div>';
        $("#datasetsList").append(txt);
        $("#datasetAddSelect").select2("val", "");
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to add a file to a dataset.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The file was not added to the dataset due to the following : " + errorThrown, "error");
        }
    });
    return false;
}

function removeFromDataset(datasetId, fileId, event){
    var request = jsRoutes.api.Datasets.detachFile(datasetId, fileId, "True").ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        $('#dat_'+datasetId).remove();
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a file from a dataset.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The file was not removed from the dataset due to : " + errorThrown, "error");
        }
    });
    return false;
}

//Method to remove the file from dataset and redirect back to a specific URL on completion
function removeFileFromDatasetAndRedirect(datasetId, fileId, isreload, url) {
    var request = jsRoutes.api.Datasets.detachFile(datasetId, fileId, "True").ajax({
        type: 'POST'
    });

    request.done(function (response, textStatus, jqXHR){
        console.log(datasetId, fileId, isreload, url);
        if(isreload === "true")
            if(url) {
                window.location.href=url;
            } else {
                window.location.reload(false);
            }
        else {
            $('#dat_' + datasetId).remove();
        }
    });
    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a file from a dataset.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The file was not removed from the dataset due to : " + errorThrown, "error");
        }
    });
}