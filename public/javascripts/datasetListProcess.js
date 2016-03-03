function removeDataset(datasetId, isreload, url){
	var request = jsRoutes.api.Datasets.deleteDataset(datasetId).ajax({
		type: 'DELETE'
	});
	request.done(function (response, textStatus, jqXHR){
        if(isreload === true)
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