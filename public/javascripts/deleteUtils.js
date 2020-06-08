// Functions to Confirm deleting or removing resources.
//
//Created by mo on 2/2/16.
function confirmDeleteResource(resourceType, resourceTypeName, resourceId, resourceName, isreload, url) {
    var msg = "Are you sure you want to delete the " + resourceTypeName + " '" + resourceName.replace("<", "&#60;").replace(">", "&#62;") + "'?";
    if (resourceType == "space") {
        msg = msg + " When you delete a " + resourceTypeName + ", the collections, datasets, folders and files will still be available.";
    } else if (resourceType == "collection") {
        msg = msg + " When you delete a collection, the datasets, folders and files will still be available.";
    } else if (resourceType == "dataset") {
        msg += " When you delete a dataset you also delete the folders and files contained within it.";
    } else if (resourceType == "folder") {
        msg += " When you delete a folder you also delete the files contained within it.";
    }
    var modalHTML = confirmDeleteTemplate(msg, resourceType, resourceId, isreload, url);
    var confirmModal = $(modalHTML);
    confirmModal.modal();
    confirmModal.modal("show");
}

function confirmDeleteSelected() {
    if ($('#selected-count')[0].innerHTML.indexOf("Manage Selections (<b>0</b>)") == -1) {
        var msg = "Are you sure you want to delete the selected datasets? When you delete a dataset you also delete the folders and files contained within it.";

        var modalHTML = '<div id="confirm-delete" class="modal fade" role="dialog">';
        modalHTML += '<div class="modal-dialog">';
        modalHTML += '<div class="modal-content">';
        modalHTML += '<div class="modal-header">';
        modalHTML += '<button type="button" class="close" data-dismiss="modal">&times;</button>';
        modalHTML += '<h4 class="modal-title">Confirm</h4>';
        modalHTML += '</div>';
        modalHTML += '<div class="modal-body">';
        modalHTML += '<p>' + msg + '</p>';
        modalHTML += '</div>';
        modalHTML += '<div class="modal-footer">';
        modalHTML += '<button type="button" class="btn btn-link" data-dismiss="modal"><span class="glyphicon glyphicon-remove"></span> Cancel</button>';
        modalHTML += '<a type="button" class="btn btn-primary" id="OKModalButton" href="javascript:deleteAllSelections()"><span class="glyphicon glyphicon-ok"></span> OK</a>';
        modalHTML += '</div>';
        modalHTML += '</div>';
        modalHTML += '</div>';
        modalHTML += '</div>';

        var confirmModal = $(modalHTML);
        confirmModal.modal();
        confirmModal.modal("show");
    } else {
        notify("No datasets currently selected", "warning")
    }
}

function confirmDeleteTemplate(message, resourceType, resourceId, isreload, url) {
    var modalHTML = '<div id="confirm-delete" class="modal fade" role="dialog">';
    modalHTML += '<div class="modal-dialog">';
    modalHTML += '<div class="modal-content">';
    modalHTML += '<div class="modal-header">';
    modalHTML += '<button type="button" class="close" data-dismiss="modal">&times;</button>';
    modalHTML += '<h4 class="modal-title">Confirm</h4>';
    modalHTML += '</div>';
    modalHTML += '<div class="modal-body">';
    modalHTML += '<p>' + message + '</p>';
    modalHTML += '</div>';
    modalHTML += '<div class="modal-footer">';
    modalHTML += '<button type="button" class="btn btn-link" data-dismiss="modal"><span class="glyphicon glyphicon-remove"></span> Cancel</button>';
    modalHTML += '<a type="button" class="btn btn-primary" id="OKModalButton" href="javascript:DeleteTemplate(\''+resourceType+'\',\''+ resourceId+'\',\''+ isreload+'\',\''+ url+'\')"><span class="glyphicon glyphicon-ok"></span> OK</a>';
    modalHTML += '</div>';
    modalHTML += '</div>';
    modalHTML += '</div>';
    modalHTML += '</div>';

    return modalHTML;
}

function confirmRemoveResourceFromResource(resourceFromType, resourceFromTypeName, resourceFromId, resourceType, resourceId, resourceName, isreload, url) {
    var msg = "Are you sure you want to remove the ";
    if (resourceFromType == "collection" && resourceType == "collection") {
        msg = msg + "child collection" + " '" + resourceName + "' from the parent " + resourceFromTypeName + "?";
    } else {
        msg = msg + resourceType + " '" + resourceName + "' from the " + resourceFromTypeName + "?";
    }
    var modalHTML = confirmRemoveTemplate(msg,resourceFromType,resourceFromId,resourceType,resourceId,isreload,url);
    var confirmModal = $(modalHTML);
    confirmModal.modal();
    confirmModal.modal("show");
}

function confirmRemoveTemplate(message,resourceFromType,resourceFromId,resourceType,resourceId,isreload,url) {
    var modalHTML = '<div id="confirm-delete" class="modal fade" role="dialog">';
    modalHTML += '<div class="modal-dialog">';
    modalHTML += '<div class="modal-content">';
    modalHTML += '<div class="modal-header">';
    modalHTML += '<button type="button" class="close" data-dismiss="modal">&times;</button>';
    modalHTML += '<h4 class="modal-title">Confirm</h4>';
    modalHTML += '</div>';
    modalHTML += '<div class="modal-body">';
    modalHTML += '<p>' + message + '</p>';

    if (resourceFromType == "space" && resourceType == "collection"){
        modalHTML += '<p><input type="checkbox" name="removedatasets" id="removedatasets" checked> Remove datasets of this collection from space.</input></p></div>'
    }
    else {
        modalHTML += '</div>';
    }

    modalHTML += '<div class="modal-footer">';
    modalHTML += '<button type="button" class="btn btn-link" data-dismiss="modal"><span class="glyphicon glyphicon-remove"></span> Cancel</button>';
    modalHTML += '<a type="button" class="btn btn-primary" id="OKModalButton" href="javascript:RemoveTemplate(\''+resourceFromType+'\',\''+ resourceFromId+'\',\''+ resourceType+'\',\''+ resourceId+'\',\''+ isreload+'\',\''+ url+'\',\''+resourceFromType+'\')"><span class="glyphicon glyphicon-ok"></span> OK</a>';
    modalHTML += '</div>';
    modalHTML += '</div>';
    modalHTML += '</div>';
    modalHTML += '</div>';

    return modalHTML;
}

function confirmRemoveResourceFromResourceEvent(resourceFromType, resourceFromTypeName, resourceFromId, resourceType, resourceId, event) {
    var msg = "Are you sure you want to remove the " + resourceType + " from the " + resourceFromTypeName + "?";
    var modalHTML = confirmRemoveTemplate(msg,resourceFromType,resourceFromId,resourceType,resourceId,event);
    var confirmModal = $(modalHTML);
    confirmModal.modal();
    confirmModal.modal("show");
}



function DeleteTemplate(resourceType, resourceId, isreload, url, resourceFromType) {
    $('.modal').modal('hide');
    if (resourceType == "file") {
        removeFile(resourceId,isreload, url);
    } else if (resourceType == "dataset") {
        removeDataset(resourceId, isreload, url);
    } else if (resourceType == "collection") {
        removeCollection(resourceId, isreload, url, resourceFromType);
    } else if (resourceType == "space") {
        removeSpace(resourceId, isreload, url);
    } else if(resourceType == "folder" ) {
        //no redirect provided
        removeFolder(resourceId, isreload );
    } else if(resourceType == "curation object") {
        removeCuration(resourceId, isreload, url);
    } else if(resourceType == "curation file") {
        //no redirect provided
        removeCurationFile(resourceId, isreload, url);
    } else if(resourceType == "curation folder" ) {
        //no redirect provided
        removeCurationFolder(resourceId, isreload, url);
    } else if(resourceType == "extractor") {
        //redirect to extractors
        removeExtractor(resourceId)
    }
}

function RemoveTemplate(resourceFromType,resourceFromId,resourceType,resourceId,isreload,url,removeDatasets) {
    //console.log(resourceFromType, resourceFromId, resourceType, resourceId, isreload, url);
    $('.modal').modal('hide');
    //console.log(url);
    console.log(removeDatasets);
    if (resourceFromType == "collection") {
        if (resourceType == "collection") {
            removeChildCollectionFromParent(resourceFromId,resourceId,url);
        } else if (resourceType == "dataset") {
            removeDatasetFromCollectionAndRedirect(resourceFromId,resourceId,isreload,url);
        }
    } else if (resourceFromType == "space") {
        if (resourceType == "dataset") {
            removeDatasetFromSpaceAndRedirect(resourceFromId,resourceId,isreload,url);
        } else if (resourceType == "collection") {
            removeCollectionFromSpaceAndRedirect(resourceFromId,resourceId,isreload,url);
        }
    }
}

function RemoveTemplateEvent(resourceFromType,resourceFromId,resourceType,resourceId,event) {
    $('.modal').modal('hide');
    if (resourceFromType == "collection") {
        if (resourceType == "dataset") {
            removeCollection(resourceFromId,resourceId,event);
        }
    } else if (resourceFromType == "space") {
        if (resourceType == "dataset") {
            removeDatasetFromSpace(resourceFromId,resourceId,event);
        } else if (resourceType == "collection") {
            removeCollectionFromSpace(resourceFromId,resourceId,event);
        }
    }
}

function confirmClearTrash(resourceType, url) {
    var msg = "Are you sure you want to clear ";
    if (resourceType == "collection"){
        msg = msg+ " all collections from the trash?"
    } else if (resourceType == "dataset"){
        msg = msg + " all datasets from the trash?"

    }
    var modalHTML = confirmTrashTemplate(msg,resourceType,url)
    var confirmModal = $(modalHTML);
    confirmModal.modal();
    confirmModal.modal("show");
}

function confirmTrashTemplate(message, resourceType,url) {

    var modalHTML = '<div id="confirm-delete" class="modal fade" role="dialog">';
    modalHTML += '<div class="modal-dialog">';
    modalHTML += '<div class="modal-content">';
    modalHTML += '<div class="modal-header">';
    modalHTML += '<button type="button" class="close" data-dismiss="modal">&times;</button>';
    modalHTML += '<h4 class="modal-title">Confirm</h4>';
    modalHTML += '</div>';
    modalHTML += '<div class="modal-body">';
    modalHTML += '<p>' + message + '</p>';
    modalHTML += '</div>';
    modalHTML += '<div class="modal-footer">';
    modalHTML += '<button type="button" class="btn btn-link" data-dismiss="modal"><span class="glyphicon glyphicon-remove"></span> Cancel</button>';
    modalHTML += '<a type="button" class="btn btn-primary" id="OKModalButton" href="javascript:ClearTrashTemplate(\''+resourceType+'\',\''+ url+'\')"><span class="glyphicon glyphicon-ok"></span> OK</a>';
    modalHTML += '</div>';
    modalHTML += '</div>';
    modalHTML += '</div>';
    modalHTML += '</div>';

    return modalHTML;
}

function ClearTrashTemplate(resourceType,url){
    console.log('got to clear trash template!!!');
    $('.modal').modal('hide');
    if (resourceType == "collection"){
        clearTrashCollections(url);
    } else if (resourceType == "dataset"){
        clearTrashDatasets(url);
    }
}

function clearTrashCollections(url){
    var request = jsRoutes.api.Collections.emptyTrash().ajax({
        type: 'DELETE'
    });
    request.done(function (response, textStatus, jqXHR){
        window.location.href=url;
    });
    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: "+textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a collection from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The trash was not removed due to : " + errorThrown, "error");
        }
    });
}

function clearTrashDatasets(url){
    var request = jsRoutes.api.Datasets.emptyTrash().ajax({
        type: 'DELETE'
    });
    request.done(function (response, textStatus, jqXHR){
        window.location.href=url;
    });
    request.fail(function (jqXHR, textStatus, errorThrown) {
        console.error("The following error occured: "+textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset trash was not cleared due to : " + errorThrown, "error");
        }
    });
}