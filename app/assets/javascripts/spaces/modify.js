/**
 * Created by lmarini on 2/5/15.
 */
function changeSpace() {
    console.log('changing space');
    var modalTemplate = Handlebars.getTemplate('/assets/templates/spaces/assign');
    var html = modalTemplate({resource_type : "Collection"});
    $('.container').append(html);
    $('#spaces-assign').modal();
}

window['changeSpace'] = changeSpace;