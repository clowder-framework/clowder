function redirectToSelectExtractors() {
    window.location.href = jsRoutes.controllers.Extractors.selectExtractors().url;
}

function removeExtractor(extractorName){

    console.log('removing extractor', extractorName);

    var request = jsRoutes.api.Extractions.deleteExtractor(extractorName).ajax({
        type: 'DELETE'
    });

    request.done(function (response, textStatus, jqXHR){
        redirectToSelectExtractors();
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "Something went wrong deleting extractor.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The extractor was not deleted from the system due to : " + errorThrown, "error");
        }
    });
}


function changeMaturity(extractorName, newMaturity) {
    console.log('updating maturity: ', extractorName + " -> " + newMaturity);
     var request = jsRoutes.api.Extractions.changeMaturity(extractorName, newMaturity).ajax({ type: 'PUT' })

    request.done(function (response, textStatus, jqXHR){
        redirectToSelectExtractors();
        console.log("Updated maturity: " + extractorName + " -> " + newMaturity);
    });

    request.fail(function (jqXHR, textStatus, errorThrown) {
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "Something went wrong updating extractor.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The extractor was not updated due to : " + errorThrown, "error");
        }
    });

    // Return false to cancel browser's default handling
    return false;
}