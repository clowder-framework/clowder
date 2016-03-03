// Functions to Confirm deleting resources.
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
    modalHTML += '<div class="modal-dialog" role="document">';
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


function DeleteTemplate(resourceType, resourceId, isreload, url) {
    $('#confirm-delete').remove();
    if (resourceType == "file") {
        removeFile(resourceId,isreload, url);
    } else if (resourceType == "dataset") {
        console.log(resourceId, isreload, url);
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