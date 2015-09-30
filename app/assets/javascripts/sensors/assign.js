function associateWithSensor(resource_type, item_id, dashboard_url) {

    var request = jsRoutes.api.Geostreams.searchSensors().ajax({
        type: 'GET',
        contentType: "application/json",
        dataType: 'json'
    });

    request.done(function (response, textStatus, jqXHR){
        showModal(response, resource_type, item_id, dashboard_url);
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
    });

    return false;
}

function showModal(sensors, resource_type, item_id, dashboard_url) {
    var modalTemplate = Handlebars.getTemplate('../assets/templates/sensors/assign'); // TODO make it portable to other contexts
    var html = modalTemplate({resource_type : resource_type, sensors: sensors});
    $('.container').append(html);
    $('#sensors-assign').modal();

    $('.list-group li').click(function(e) {
        e.preventDefault();
        $that = $(this);
        $that.parent().find('li').removeClass('active');
        $that.addClass('active');
    });

    $('#sensors_add').unbind().click(function() {
        var sensor_id = $('.list-group-item.active').data("sensor-id");
        var sensor_name = $('.list-group-item.active').data("sensor-name");
        console.log('Associating ' + resource_type + ': ' + item_id + ' to sensor ' + sensor_id + " / " + sensor_name);
        associateItemWithSensor(resource_type, item_id, sensor_id, sensor_name, dashboard_url);
    });

    return false;
}

function associateItemWithSensor(resource_type, item_id, sensor_id, sensor_name, dashboard_url) {

    var request = jsRoutes.api.Relations.add().ajax({
        type: 'POST',
        data: JSON.stringify(
            {"source": {"id":item_id,"resourceType":resource_type.toLowerCase()},
                "target": {"id":sensor_id.toString(),"resourceType":"sensor"}}),
        dataType: "json",
        contentType: "application/json; charset=utf-8"
    });

    request.done(function (response, textStatus, jqXHR){
        console.log('Done associating ' + resource_type + ' ' + item_id + ' to sensor ' + sensor_id + " / " + sensor_name);

        // append new element to list
        $('#sensors-list').append('<li><a href="' + dashboard_url + sensor_name + '/">' +sensor_name+'</a></li>');

        // close modal
        $('#sensors-assign').modal('hide');
        window.location.reload();
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
    });

    return false;

}



window['associateWithSensor'] = associateWithSensor;