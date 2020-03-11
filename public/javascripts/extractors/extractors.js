function removeExtractor(extractorId){

    console.log('removing extractor');

    var request = jsRoutes.api.Extractions.deleteExtractor(extractorId).ajax({
        type: 'DELETE'
    });

    request.done(function (response, textStatus, jqXHR){
        window.location.href=jsRoutes.controllers.Extractors.listAllExtractions;
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "Somethingwent wrong.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The extractor was not deleted from the system due to : " + errorThrown, "error");
        }
    });
}