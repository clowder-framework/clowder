$(function() {
    $('.select').click(function() {

        var dataset_id = $(this).data("id");

        if ($("a[data-id='"+dataset_id+"'] span.glyphicon").hasClass('glyphicon-ok')) {

            var request = jsRoutes.api.Selected.remove().ajax({
                data: JSON.stringify({"dataset":dataset_id}),
                type: 'POST',
                contentType: "application/json"
            });

            request.done(function (response, textStatus, jqXHR){
                console.log("dataset " + dataset_id + " removed");
                var elm = document.getElementById(dataset_id+"-tile");
                elm.parentElement.removeChild(elm);
                updateSelectedCount(response.selected_count)
                updateMessage();
            });

            request.fail(function (jqXHR, textStatus, errorThrown){
                console.error("The following error occured: "+ textStatus, errorThrown);
                window.location = "../login"; // FIXME hardcoded
            });

        }
    });

    $('.select-file').click(function  () {
        var file_id = $(this).data("id");
        console.log('clicked a file', file_id)

        if ($("a[data-id='"+file_id+"'] span.glyphicon").hasClass('glyphicon-ok')) {

            var request = jsRoutes.api.Selected.remove().ajax({
                data: JSON.stringify({"file":file_id}),
                type: 'POST',
                contentType: "application/json"
            });

            request.done(function (response, textStatus, jqXHR){
                console.log("file " + file_id + " removed");
                var elm = document.getElementById(file_id+"-tile");
                console.log(elm);
                elm.parentElement.removeChild(elm);
                updateSelectedCount(response.selected_count)
                updateMessage();
            });

            request.fail(function (jqXHR, textStatus, errorThrown){
                console.error("The following error occured: "+ textStatus, errorThrown);
                window.location = "../login"; // FIXME hardcoded
            });

        }
    });
});