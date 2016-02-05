// Functions to Confirm deleting resources.
//
//Created by mo on 2/2/16.
function confirmDeleteResource(resourceType, resourceId, resourceName, url) {
    var msg = "Are you sure you want to delete the " + resourceType + " '" + resourceName + "'?";

    var modalHTML = confirmDeleteTemplate(msg);

    $(document).on("click", "#OKModalButton", function(event) {
        confirmModal.modal("hide");
        if (resourceType == "file") {
            removeFileAndRedirect(resourceId, url);
        } else if (resourceType == "dataset") {
            removeDatasetAndRedirect(resourceId, url);
        } else if (resourceType == "collection") {
            removeCollectionAndRedirect(resourceId, url);
        } else if (resourceType == "space") {
            removeSpaceAndRedirect(resourceId, url);
        }
    });

    var confirmModal = $(modalHTML);
    confirmModal.modal("show");
}

function confirmDeleteTemplate(message) {
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
    modalHTML += '<button type="button" class="btn btn-primary" id="OKModalButton"><span class="glyphicon glyphicon-ok"></span> OK</button>';
    modalHTML += '</div>';
    modalHTML += '</div>';
    modalHTML += '</div>';
    modalHTML += '</div>';

    return modalHTML;
}
