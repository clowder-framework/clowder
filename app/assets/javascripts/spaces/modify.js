/**
 * Created by lmarini on 2/5/15.
 */
resource_type_enum = {
    DATASET : 0,
    COLLECTION : 1
}


function changeSpace(resource_id, resource_type) {

    var request = jsRoutes.api.Spaces.list().ajax({
        type: 'GET'
    });

    request.done(function (response, textStatus, jqXHR){
        showModal(response, resource_id, resource_type);
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
    });
}

function showModal(spaces, resource_id, resource_type) {
    var modalTemplate = Handlebars.getTemplate('/assets/templates/spaces/assign');
    var html = modalTemplate({spaces: spaces});
    $('.container').append(html);
    $('#spaces-assign').modal();

    $('.list-group li').click(function(e) {
        e.preventDefault();
        $that = $(this);
        $that.parent().find('li').removeClass('active');
        $that.addClass('active');
    });

    $('#spaces_add').click(function() {
        var space_id = $('.list-group-item.active').data("space-id");
        console.log(resource_type + " =? " + resource_type_enum.DATASET);
        if(resource_type == resource_type_enum.DATASET) {
            console.log('Adding dataset ' + resource_id + ' to space ' + space_id);
            addDatasetToSpace(resource_id, space_id);
        } else if(resource_type == resource_type_enum.COLLECTION) {
            console.log('Adding collection ' + resource_id + ' to space ' + space_id);
            addCollectionToSpace(resource_id, space_id);
        } else {
            console.error("Resource type not recognized when adding to space");
        }

    });
}

function addCollectionToSpace(collection_id, space_id) {

    var request = jsRoutes.api.Spaces.addCollection(space_id).ajax({
        type: 'POST',
        data: JSON.stringify({'collection_id': collection_id}),
        dataType: "json",
        contentType: "application/json; charset=utf-8"
    });

    request.done(function (response, textStatus, jqXHR){
        console.log('Added collection ' + collection_id + ' to space ' + space_id);
        $('#spaces-assign').modal('hide');
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
    });
}

function addDatasetToSpace(dataset_id, space_id) {

    var request = jsRoutes.api.Spaces.addDataset(space_id).ajax({
        type: 'POST',
        data: JSON.stringify({'dataset_id': dataset_id}),
        dataType: "json",
        contentType: "application/json; charset=utf-8"
    });

    request.done(function (response, textStatus, jqXHR){
        console.log('Added dataset ' + dataset_id + ' to space ' + space_id);
        $('#spaces-assign').modal('hide');
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
    });
}

window['changeSpace'] = changeSpace;