$(function() {
    $('.select').click(function() {

        var dataset = $(this).data("id");

        var request = jsRoutes.api.Selected.add().ajax({
            data: JSON.stringify({"dataset":dataset}),
            type: 'POST',
            contentType: "application/json"
        });

        request.done(function (response, textStatus, jqXHR){
            console.log("dataset " + dataset + " selected");
        });

        request.fail(function (jqXHR, textStatus, errorThrown){
            console.error("The following error occured: "+ textStatus, errorThrown);
            window.location = "../login"; // FIXME hardcoded
        });
    });
});