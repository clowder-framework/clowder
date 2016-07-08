/**
 * Created by lmarini on 3/23/15.
 */
function detachFile(fileId, fileName,event){
    var request = $.ajax({
        type: 'POST',
        url: jsRoutes.api.Datasets.detachFile(@dataset.id, fileId, "True").url
    });
    request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);
        $(event.target.parentNode.parentNode).remove();
        var i = 1;
        var nextHRs = $("#previewedFileName_"+fileId).nextAll("hr");
        if(nextHRs.length > 0){
            nextHRs.first().remove();
        }
        else{
            var prevHRs = $("#previewedFileName_"+fileId).prevAll("hr");
            if(prevHRs.length > 0){
                prevHRs.first().remove();
            }
        }
        $("#previewedFileName_"+fileId).remove();
        $("#filePrevContainer_"+fileId).remove();
        $("#technicalMetadata_"+fileId).remove();
        $("#fileUserMetadata_"+fileId).remove();
    });
    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: "+textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a file from a dataset.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The file was not removed from the dataset due to : " + errorThrown, "error");
        }
    });
}