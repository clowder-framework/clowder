//Method to download all files on a dataset
function download(datasetId){
  var request = jsRoutes.api.Datasets.download(datasetId).ajax({
    type: 'GET'
  });

  request.done(function (response, textStatus, jqXHR){
    console.log('Download finished.');
  });

  request.fail(function (jqXHR, textStatus, errorThrown){
    console.error("The following error occured: " + textStatus, errorThrown);
    var errMsg = "You must be logged in to download this dataset.";
    if (!checkErrorAndRedirect(jqXHR, errMsg)) {
      notify("The dataset was not downloaded from the system due to : " + errorThrown, "error");
    }
  });
}