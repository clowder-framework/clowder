/**
 * Created by lmarini on 2/5/15.
 */
function changeSpace(collection_id) {

    var request = jsRoutes.api.Spaces.list().ajax({
        type: 'GET'
    });

    request.done(function (response, textStatus, jqXHR){
        showModal(response, collection_id);
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
    });
}

function showModal(spaces, collection_id) {
    var modalTemplate = Handlebars.getTemplate('/assets/templates/spaces/assign');
    var html = modalTemplate({resource_type : "Collection", spaces: spaces});
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
        console.log('adding collection ' + collection_id + ' to space ' + space_id);
        addCollectionToSpace(collection_id, space_id);
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
        console.log('added collection ' + collection_id + ' to space ' + space_id);
        $('#spaces-assign').modal('hide');
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
    });
}

window['changeSpace'] = changeSpace;