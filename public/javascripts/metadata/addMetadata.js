function loadExternalResource(field_label, field_description, field_id, field_type, fields, space_id, modalTemplate,
                              footerTemplate, id, selector_prefix) {
    var html = modalTemplate({'field_label': field_label, 'field_description': field_description,
        'field_id': field_id, 'field_type': field_type});
    var footerHtml = "";
    var is_advanced = selector_prefix.search("advanced") > -1;

    if(typeof space_id !== 'undefined' && showSpaceInfo) {
        var request2 = jsRoutes.api.Spaces.get(space_id).ajax({
            type: 'GET',
            contentType: "application/json"
        });
        request2.done(function (response, textStatus, jqXHR) {
            var spaceName= response.name;
            footerHtml = footerTemplate({'space_name': spaceName, 'space_url': jsRoutes.controllers.Spaces.getSpace(space_id).url, 'uri':field_id });
            if (is_advanced) {
                $(selector_prefix).append(html + footerHtml);
            } else {
                $("#" + id + " "+ selector_prefix + " #selected_field").html(html + footerHtml);
            }
        });
        request2.fail(function (jqXHR, textStatus, errorThrown){
            if (is_advanced) {
                $(selector_prefix).append(html);
            } else {
                $("#" + id + " "+ selector_prefix + " #selected_field").html(html);
            }
        });
    } else {
        if (is_advanced) {
            $(selector_prefix).append(html);
        } else {
            $("#" + id + " "+ selector_prefix + " #selected_field").html(html);
        }
    }
    if (field_type === "annotation") {
        // Find the field with the specific uri.
        var field = $.grep(fields, function(e){ return e.json.uri == field_id; });
        $(id).find(selector_prefix).find("#field-value [data-id=\'" + field_id + "\']").catcomplete({
            minLength: 3,
            source: function( request, response ) {

                var useSyn = $("#" + id +" #useSynonyms").prop("checked");
                var query_param = field[0].json.query_parameter;
                var url = encodeURIComponent(field[0].json.definitions_url + "?" + query_param + "=" + request.term + "&useSynonyms=" + useSyn);
                $.ajax({
                    url: jsRoutes.api.Metadata.getUrl(url).url,
                    //dataType: "jsonp",
                    dataType: "json",
                    //data: { term: request.term, useSynonyms: useSyn },
                    success: function( data ) {
                        // The vars list is in data.vars_data, and the categories in data.cat_data. Assuming that "listjquery" will use a URL that returns filtered data, we don't filter again. Returns cat_data with the vars listif present, otherwise returns the original data.
                        if ('cat_data' in data) {
                            var res = data.cat_data.concat(data.vars_data);
                            response(res);
                        } else {
                            response(data);
                        }
                    }
                });
            }
        });

        // make call to external service
        var request = $.ajax({
            url: field[0].json.definitions_url,
            type: 'GET',
            contentType: "application/json"
        });

        request.done(function (response, textStatus, jqXHR) {
            var vocabulary = response;
            // modalTemplate was assigned above using the map.
            var html = modalTemplate({'field_id': field_id, 'options': vocabulary});
            var selector = "#" + id + " "+ selector_prefix+ " #standard-name[data-id=\'" + field_id + "\']";
            $(selector).html(html);

            // chosen pulldown configuration
            $(selector).chosen({
                no_results_text: "Not found. Press enter to add ",
                add_search_option: true,
                search_contains: true,
                width: "100%",
                placeholder_text_single: "Select field"});
        });

        request.fail(function (jqXHR, textStatus, errorThrown){
            console.error("The following error occured: " + textStatus, errorThrown);
            notify("Could not retrieve external vocabulary: " + errorThrown, "error");
        });

    } else if (field_type === "scientific_variable") {
        // Find the field with the specific uri.
        var field = $.grep(fields, function(e){ return e.json.uri == field_id; });
        $("#" + id ).find(selector_prefix).find("#field-value [data-id=\'" + field_id + "\']").catcomplete({
            minLength: 3,
            source: function( request, response ) {

                var useSyn = $("#" + id +" #useSynonyms").prop("checked");
                var query_param = field[0].json.query_parameter;
                var url = encodeURIComponent(field[0].json.definitions_url + "?" + query_param + "=" + request.term + "&useSynonyms=" + useSyn);
                $.ajax({
                    url: jsRoutes.api.Metadata.getUrl(url).url,
                    //dataType: "jsonp",
                    dataType: "json",
                    //data: { term: request.term, useSynonyms: useSyn },
                    success: function( data ) {
                        // The vars list is in data.vars_data, and the categories in data.cat_data. Assuming that "listjquery" will use a URL that returns filtered data, we don't filter again. Returns cat_data with the vars listif present, otherwise returns the original data.
                        if ('cat_data' in data) {
                            var res = data.cat_data.concat(data.vars_data);
                            response(res);
                        } else {
                            response(data);
                        }
                    }
                });
            }
        });
        $("#" + id ).find(selector_prefix).find("#unit-value [data-id=\'" + field_id + "\']").autocomplete({
            minLength: 1,
            source: function( request, response ) {
                var url = encodeURIComponent(field[0].json.definitions_url);
                $.ajax({
                    url: jsRoutes.api.Metadata.getUrl(url).url,
                    dataType: "json",
                    success: function( data ) {
                        if (! ('unit_data' in data)) {
                            response(Array("Error: no unit_data field in the returned result."));
                        } else {
                            var searchspace = data.unit_data;
                            var searchwords = request.term.split(" ");
                            $.each(searchwords, function() {
                                searchspace = $.ui.autocomplete.filter(searchspace, this);
                            });
                            response(searchspace);
                        }
                    }
                });
            }
        });
    } else if (field_type === "datetime") {
        // This widget uses the ISO 8601 format, such as 2016-01-01T10:00:00-06:00 or 2016-01-01T10:00:00Z.
        // This uses Trent Richardson's jQuery UI Timepicker add-on.
        // jQuery UI Datepicker options: http://api.jqueryui.com/datepicker/
        // jQuery UI Timepicker addon options: http://trentrichardson.com/examples/timepicker/
        $("#" + id ).find(selector_prefix).find("[data-id=\'" + field_id + "\']").datetimepicker(
            { controlType: 'select',
                // Uses "select" instead of the default slider.
                // If we put "T" in the "separator" option instead of "timeFormat", the widget changes 2-digit year values xx we put directly into the field to 20xx; if we put "T" in timeFormat, then the year values are kept. So we used the latter.
                dateFormat: $.datepicker.ISO_8601,
                timeFormat: "'T'HH:mm:ssZ",
                separator: '',
                // Allows direct input.
                timeInput: true
            });
    } else if (field_type === "listjquery") {
        // Find the field with the specific uri.
        var field = $.grep(fields, function(e){ return e.json.uri == field_id; });
        var selector = "#" + id + " "+ selector_prefix+ " [data-id=\'" + field_id + "\']";
        $(selector).autocomplete({
            minLength: 3,
            source: function( request, response ) {
                // Get the query parameter from the saved json, not hardcoded.
                var query_param = field[0].json.query_parameter;
                var url = encodeURIComponent(field[0].json.definitions_url + "?" + query_param + "=" + request.term);
                $.ajax({
                    url: jsRoutes.api.Metadata.getUrl(url).url,
                    dataType: "json",
                    success: function( data ) {
                        var searchspace = data;
                        var searchwords = request.term.split(" ");
                        $.each(searchwords, function() {
                            searchspace = $.ui.autocomplete.filter(searchspace, this);
                        });
                        response(searchspace);
                    }
                });
            }
        });
    } else if (field_type === "listgeocode") {
        // Find the field with the specific uri.
        var field = $.grep(fields, function(e){ return e.json.uri == field_id; });
        var selector = "#" + id + " "+ selector_prefix+ " [data-id=\'" + field_id + "\']";
        $(selector).autocomplete({
            minLength: 3,
            source: function( request, response ) {
                // Sets a variable query parameter in $.ajax.data below.
                var query_param = field[0].json.query_parameter;
                var url = encodeURIComponent(field[0].json.definitions_url + "?" + query_param + "=" + request.term);
                $.ajax({
                    url: jsRoutes.api.Metadata.getUrl(url).url,
                    //dataType: "jsonp",
                    dataType: "json",
                    //data: query_data,
                    success: function( data ) {
                        // Assuming that the remote service returns filtered data, no need to filter again.
                        response(data);
                    }
                });
            }
        });
    } else if (field_type === "list") {
        // find field with the specific uri
        var field = $.grep(fields, function(e){ return e.json.uri == field_id; });
        var selector = "#" + id + " "+ selector_prefix+ " [data-id=\'" + field_id + "\']";

        // make call to external service
        var request = jsRoutes.api.Metadata.getDefinition(field[0].id).ajax({
            type: 'GET',
            contentType: "application/json"
        });

        request.done(function (response, textStatus, jqXHR) {
            var vocabulary = JSON.parse(response);
            // modalTemplate was assigned above using the map.
            var html = modalTemplate({'field_label': field_label, 'field_description': field_description,
                'field_id': field_id, 'field_type': field_type, 'options': vocabulary});
            var selector = "#" + id + " "+ selector_prefix+ " [data-id=\'" + field_id + "\']";
            $(selector).html(html + footerHtml);
            // chosen pulldown configuration

            $(selector).chosen({
                no_results_text: "Not found. Press enter to add ",
                add_search_option: true,
                search_contains: true,
                width: "100%",
                placeholder_text_single: "Select field"});
        });

        request.fail(function (jqXHR, textStatus, errorThrown){
            console.error("The following error occured: " + textStatus, errorThrown);
            notify("Could not retrieve external vocabulary: " + errorThrown, "error");
        });
    }

}