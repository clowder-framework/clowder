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