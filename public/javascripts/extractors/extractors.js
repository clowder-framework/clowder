
function cancelSubmission(id, submit_id, extractor_id, extractType) {
    console.log("cancel submit: " + id + ", submit id: " + submit_id);
    var dataBody = {'extractor': extractor_id};
    var apicall = null

    if (extractType == "dataset") {// dataset submission
        apicall = jsRoutes.api.Extractions.cancelDatasetExtractionSubmission
    } else if(extractType == "file"){
        apicall = jsRoutes.api.Extractions.cancelFileExtractionSubmission
    }
    var request = apicall(id, submit_id).ajax({
        data: JSON.stringify(dataBody),
        type: 'POST',
        contentType: "application/json",
    });

    request.done(function (response, textStatus, jqXHR){
        notify("Cancellation request has been submitted", "success");
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
    });
}

function removeExtractor(extractorName){

    console.log('removing extractor', extractorName);

    var request = jsRoutes.api.Extractions.deleteExtractor(extractorName).ajax({
        type: 'DELETE'
    });

    request.done(function (response, textStatus, jqXHR){
        var url = jsRoutes.controllers.Extractors.selectExtractors().url;
        window.location.href=url;
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "Something went wrong deleting extractor.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The extractor was not deleted from the system due to : " + errorThrown, "error");
        }
    });
}