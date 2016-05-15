// Functions and callback definitions that are used by the multi-file-uploader for adding files to dataset

$(function () {	                	                 
	//Callback for any submit call, whether it is the overall one, or individual files, in the multi-file-uploader
    $('#fileupload').bind('fileuploadsubmit', function (e, data) {    	    	    	    	
    	return uploadFileToExistingDataset(data);    
    });	    
});        

function uploadFileToExistingDataset(data) {
    $('#ds-files-delete').removeClass("hidden");
    return true;
}

function checkZeroFiles(id, inFolder) {
	var numFiles = $('#fileupload').fileupload('option').getNumberOfFiles();	
	if (numFiles === 0) {
        notify("No files have been added to the upload queue. Please select files to upload", "warning", true, 5000);
		return false;
	} else {
		$("#hiddenmt").attr("value", true);
		var request = jsRoutes.api.Events.addFileEvent(id, inFolder, numFiles).ajax({
			type: 'POST'
		});
		request.done(function (response, textStatus, jqXHR) {
			console.log("save multiple file events");
		});
	}
}

//Clear all selected items
function clearFiles() {
	$("#filelist option:selected").removeAttr("selected");
}