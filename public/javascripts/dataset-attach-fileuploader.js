// Functions and callback definitions that are used by the multi-file-uploader for adding files to dataset
$(function () {
	//Callback for any submit call, whether it is the overall one, or individual files, in the multi-file-uploader
    $('#fileupload').bind('fileuploadsubmit', function (e, data) {
    	return uploadFileToExistingDataset(data);    
    });
	//Callback for when all uploads are complete in a sequence
	$('#fileupload').bind('fileuploadstop', function (e, data) {
		// setTimeout to give the table a moment to update URLs before querying them
		setTimeout(function(){
			// TODO: Gotta be a better way... parent node stuff?
			if (window.location.href.indexOf("addFiles") > -1 || window.location.href.indexOf("uploadFiles") > -1)
				var dsid = window.location.href.substring(window.location.href.indexOf("/datasets/")+"/datasets/".length).split("/")[0];
			else if (window.location.href.indexOf("createStep2") > -1)
				var dsid = window.location.href.substring(window.location.href.indexOf("?id=")+"?id=".length);

			var fileids = new Array();
			var rowlist = document.getElementById("filelist_table").rows;
			for (var row of rowlist) {
				var fileid = row.innerHTML.split('data-url="')[1].split('">')[0]
				fileid = fileid.substring(fileid.indexOf("/api/files/")+"/api/files/".length).replace("/remove","");
				fileids.push(fileid);
			}

			var request = jsRoutes.api.Extractions.addNewFilesetEvent(dsid, fileids).ajax({
				type: 'POST'
			});

			//Note - need to make the "replace" calls below more generic.
			request.done(function (response, textStatus, jqXHR){
				console.log("File set submitted");
			});

			request.fail(function (jqXHR, textStatus, errorThrown){
				console.error("The following error occured: "+textStatus, errorThrown);
				var errMsg = "You must be logged in to add a dataset to a collection.";
				if (!checkErrorAndRedirect(jqXHR, errMsg)) {
					notify("The fileset message was not generated due to : " + errorThrown, "error");
				}
			});
		}, 1000);
	});
});        

function uploadFileToExistingDataset(data) {
    $('#ds-files-delete').removeClass("hidden");
    return true;
}

function checkZeroFiles(id, inFolder) {
    //the number of un-uploaded files
	var numFiles = $('.template-upload').length;	
	if (numFiles === 0) {
        notify("No files have been added to the upload queue. Please select files to upload", "warning", true, 5000);
		return false;
	} else {
		$("#hiddenmt").attr("value", true);
		var request = jsRoutes.api.Datasets.addFileEvent(id, inFolder, numFiles).ajax({
			type: 'POST'
		});
		request.done(function (response, textStatus, jqXHR) {
			console.log("save multiple file events");
			$("#hiddenmt").attr("value", false);
		});
	}
}

//Clear all selected items
function clearFiles() {
	$("#filelist option:selected").removeAttr("selected");
}