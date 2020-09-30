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

function fetchExtractorInfo(extractorName) {
    console.log('fetching extractor info: ', extractorName);

    var request = jsRoutes.api.Extractions.getExtractorInfo(extractorName).ajax({
        type: 'GET'
    });

    request.done(function (response, textStatus, jqXHR){
        console.log("fetched extractor info:", response);
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "Something went wrong deleting extractor.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The extractor was not deleted from the system due to : " + errorThrown, "error");
        }
    });

    return request;
}

function updateExtractorInfo(extractorInfo) {
    var extractorName = extractorInfo['name']
    console.log('updating extractor info: ', extractorName);
    var request = jsRoutes.api.Extractions.addExtractorInfo().ajax({
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(extractorInfo)
    });

    request.done(function (response, textStatus, jqXHR){
        redirectToSelectExtractors();
        console.log("Updated extractor info: " + extractorName);
    });

    request.fail(function (jqXHR, textStatus, errorThrown) {
        console.error("The following error occurred: " + textStatus, errorThrown);
        var errMsg = "Something went wrong updating extractor.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The extractor was not updated due to : " + errorThrown, "error");
        }
    });

    // Return false to cancel browser's default handling of href
    return false;
}

function changeMaturity(extractorName, newMaturity) {
    console.log('updating extractor maturity: ', extractorName + ' -> ' + newMaturity);
    var fetchReq = fetchExtractorInfo(extractorName);

    fetchReq.done(function (extractorInfo, textStatus, jqXHR) {
        extractorInfo['maturity'] = newMaturity;
        updateExtractorInfo(extractorInfo);
    });

    return false;
}