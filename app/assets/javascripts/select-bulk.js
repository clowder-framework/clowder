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
});