function removeDataset(datasetId, isreload, url){
	var request = jsRoutes.api.Datasets.deleteDataset(datasetId).ajax({
		type: 'DELETE'
	});
	request.done(function (response, textStatus, jqXHR){
        if(isreload === "true")
			window.location.href=url;
		else {
			$('#'+ datasetId+'-listitem').remove();
			var obj = $('#'+ datasetId+'-tile');
			if($('#masonry').length > 0) {
				$('#masonry').masonry('remove', obj);
				$('#masonry').masonry('layout');
			}
			if($('#masonry-datasets').length > 0) {
				$('#masonry-datasets').masonry('remove', obj);
				$('#masonry-datasets').masonry('layout');
			}
		}
    });
	request.fail(function (jqXHR, textStatus, errorThrown) {
		console.error("The following error occured: "+textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not removed due to : " + errorThrown, "error");
        }
	});	
}

function restoreDataset(id,isreload,newurl){
    var url = jsRoutes.api.Datasets.restoreDataset(id)
    var request = url.ajax({
        type: 'PUT'
    });
    request.done(function (response, textStatus, jqXHR){
        console.log('success');
        console.log('response',response)
        console.log(newurl);
        window.location.href=newurl;

    });
    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: "+textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a collection from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The collection was not restored due to : " + errorThrown, "error");
        }
    });
}

//Method to remove the dataset and redirect back to a specific URL on completion
function removeDatasetAndRedirect(datasetId, url){
	if(url === undefined) reloadPage = "/datasets";
	
	var request = jsRoutes.api.Datasets.deleteDataset(datasetId).ajax({
		type: 'DELETE'
	});

	request.done(function (response, textStatus, jqXHR){		
		window.location.href=url;
	});
	
	request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to delete a dataset from the system.";        
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not deleted from the system due to : " + errorThrown, "error");
        }
	});	
}

//Method to detach all files from first, and then remove the dataset and redirect back to a specific URL on completion
function detachAndRemoveDatasetAndRedirect(datasetId, url){
	if(url === undefined) reloadPage = "/datasets";
	
	var request = jsRoutes.api.Datasets.detachAndDeleteDataset(datasetId).ajax({
		type: 'POST'
	});

	request.done(function (response, textStatus, jqXHR){
		console.log("Response " + response);		
		window.location.href=url;
	});
	
	request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to detach files and then delete a dataset from the system.";        
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not deleted from the system due to : " + errorThrown, "error");
        }
	});	
}
var accessStatus;
function setOriginAccess() {
	accessStatus = $("input[name='access']:checked").val();
}

function updateAccessApi(id, access) {
	$('.modal').modal('hide');
	var request = jsRoutes.api.Datasets.updateAccess(id, access).ajax({
		type: 'PUT'
	});

	request.done(function(response, textStatus, jsXHR){
		accessStatus = access;
		notify("Dataset is set to "+ access, "success", false, 2000);
	});
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occurred: " + textStatus, errorThrown);
		var errMsg = "You must be logged in to update the dataset.";
		if (!checkErrorAndRedirect(jqXHR, errMsg)) {
			notify("Error in updating the dataset : " + errorThrown, "error");
		}
	});
}

function cancelAccessChange(){
	console.log("cancel");
	$('.modal').modal('hide');
	document.getElementById('access-'+accessStatus).checked= true;
}


function confirmTemplate(message, resourceId, access) {
	var modalHTML = '<div id="confirm-access" class="modal fade" role="dialog">';
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
	modalHTML += '<a type="button" class="btn btn-link"  href="javascript:cancelAccessChange()"><span class="glyphicon glyphicon-remove"></span> Cancel</a>';
	modalHTML += '<a type="button" class="btn btn-primary"  href="javascript:updateAccessApi(\''+ resourceId+'\',\''+ access+'\')"><span class="glyphicon glyphicon-ok"></span> OK</a>';
	modalHTML += '</div>';
	modalHTML += '</div>';
	modalHTML += '</div>';
	modalHTML += '</div>';

	return modalHTML;
}
