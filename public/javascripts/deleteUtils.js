// Functions to Confirm deleting or removing resources.
//
//Created by mo on 2/2/16.
function confirmDeleteResource(resourceType, resourceId, resourceName, isreload, url) {
    var msg = "Are you sure you want to delete the " + resourceType + " '" + resourceName + "'?";
    var modalHTML = confirmDeleteTemplate(msg, resourceType, resourceId, isreload, url);
    var confirmModal = $(modalHTML);
    confirmModal.modal();
    confirmModal.modal("show");
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

function confirmRemoveResourceFromResource(resourceFromType, resourceFromId, resourceType, resourceId, resourceName, isreload, url) {
    var msg = "Are you sure you want to remove the ";
    if (resourceFromType == "collection" && resourceType == "collection") {
        msg = msg + "child collection" + " '" + resourceName + "' from the parent " + resourceFromType + "?";
    } else {
        msg = msg + resourceType + " '" + resourceName + "' from the " + resourceFromType + "?";
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
    modalHTML += '</div>';
    modalHTML += '<div class="modal-footer">';
    modalHTML += '<button type="button" class="btn btn-link" data-dismiss="modal"><span class="glyphicon glyphicon-remove"></span> Cancel</button>';
    modalHTML += '<a type="button" class="btn btn-primary" id="OKModalButton" href="javascript:RemoveTemplate(\''+resourceFromType+'\',\''+ resourceFromId+'\',\''+ resourceType+'\',\''+ resourceId+'\',\''+ isreload+'\',\''+ url+'\')"><span class="glyphicon glyphicon-ok"></span> OK</a>';
    modalHTML += '</div>';
    modalHTML += '</div>';
    modalHTML += '</div>';
    modalHTML += '</div>';

    return modalHTML;
}

function confirmRemoveResourceFromResourceEvent(resourceFromType, resourceFromId, resourceType, resourceId, event) {
    var msg = "Are you sure you want to remove the " + resourceType + " from the " + resourceFromType + "?";
    var modalHTML = confirmRemoveTemplate(msg,resourceFromType,resourceFromId,resourceType,resourceId,event);
    var confirmModal = $(modalHTML);
    confirmModal.modal();
    confirmModal.modal("show");
}

function DeleteTemplate(resourceType, resourceId, isreload, url) {
    $('.modal').modal('hide');
    if (resourceType == "file") {
        removeFile(resourceId,isreload, url);
    } else if (resourceType == "dataset") {
        removeDataset(resourceId, isreload, url);
    } else if (resourceType == "collection") {
        removeCollection(resourceId, isreload, url);
    } else if (resourceType == "space") {
        removeSpace(resourceId, isreload, url);
    } else if(resourceType == "folder" ) {
        //no redirect provided
        removeFolder(resourceId, isreload );
    } else if(resourceType == "curation object" ) {
        removeCuration(resourceId, isreload, url);
    } else if(resourceType == "curation file" ) {
        //no redirect provided
        removeCurationFile(resourceId, isreload, url);
    } else if(resourceType == "curation folder" ) {
        //no redirect provided
        removeCurationFolder(resourceId, isreload, url);
    }
}

function RemoveTemplate(resourceFromType,resourceFromId,resourceType,resourceId,isreload,url) {
    $('.modal').modal('hide');
    if (resourceFromType == "collection") {
        if (resourceType == "collection") {
            removeChildCollectionFromParent(resourceFromId,resourceId,isreload,url);
        } else if (resourceType == "dataset") {
            //console.log(resourceFromId);
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