$(function() {
    $('.select').click(function() {

        var dataset_id = $(this).data("id");

        if ($("a[data-id='"+dataset_id+"'] span.glyphicon").hasClass('glyphicon-plus')) {

            var request = jsRoutes.api.Selected.add().ajax({
                data: JSON.stringify({"dataset":dataset_id}),
                type: 'POST',
                contentType: "application/json"
            });

            request.done(function (response, textStatus, jqXHR){
                console.log("dataset " + dataset_id + " selected");
                $("a[data-id='"+dataset_id+"'] span.glyphicon").removeClass('glyphicon-plus');
                $("a[data-id='"+dataset_id+"'] span.glyphicon").addClass('glyphicon-ok');
            });

            request.fail(function (jqXHR, textStatus, errorThrown){
                console.error("The following error occured: "+ textStatus, errorThrown);
                window.location = "../login"; // FIXME hardcoded
            });

        } else if ($("a[data-id='"+dataset_id+"'] span.glyphicon").hasClass('glyphicon-ok')) {

            var request = jsRoutes.api.Selected.remove().ajax({
                data: JSON.stringify({"dataset":dataset_id}),
                type: 'POST',
                contentType: "application/json"
            });

            request.done(function (response, textStatus, jqXHR){
                console.log("dataset " + dataset_id + " selected");
                $("a[data-id='"+dataset_id+"'] span.glyphicon").removeClass('glyphicon-ok');
                $("a[data-id='"+dataset_id+"'] span.glyphicon").addClass('glyphicon-plus');
            });

            request.fail(function (jqXHR, textStatus, errorThrown){
                console.error("The following error occured: "+ textStatus, errorThrown);
                window.location = "../login"; // FIXME hardcoded
            });

        }
    });
});