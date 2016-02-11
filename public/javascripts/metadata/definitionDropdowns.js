/**
 * Register AutoComplete Dropdowns for Metadata Definitions
 *
 * @param inputElement - the target input box where autocomplete should appear
 * @param definitionData - the metadata definition (in JSON format) to register for this input box
 * @param selectElement - the select element can be referenced if you need to de-register events when
 *                      a new metadata definition is selected from the dropdown
 */
var registerMDAutocomplete = function(inputElement, definitionData, selectElement) {

  // call the internal function based on the type
  // if the metadata definition is a "list", we expect to find a function called this.list below
  // otherwise we will not autocomplete
  if (this[definitionData.type] && typeof this[definitionData.type] == 'function') {
    this[definitionData.type](inputElement, definitionData, selectElement);
  } else {
    if ($(inputElement).prop('autocomplete')) {
      $(inputElement).autocomplete("destroy");
    }
  }

  this.list = function(inputElement, definitionData) {
    $(inputElement).autocomplete({
      source: function(request, response) {
        var url;
        if (definitionData.query_parameter) {
          url = encodeURIComponent(definitionData.definitions_url + "?" + definitionData.query_parameter + "=" + request.term);
        } else {
          url = encodeURIComponent(definitionData.definitions_url);
        }
        $.ajax({
          url: jsRoutes.api.Metadata.getUrl(url).url,
          dataType: 'json',
          success: function(data) {
            var searchspace;
            if ('cat_data' in data) {
              searchspace = data.cat_data.concat(data.vars_data);
            } else {
              searchspace = data;
            }
            var searchwords = request.term.split(" ");
            $.each(searchwords, function() {
              searchspace = $.ui.autocomplete.filter(searchspace, this);
            });
            response(searchspace);
          }
        });
      }
    });
  };

  this.listjquery = this.list;

  this.listgeocode = function(inputElement, definitionData) {
    $(inputElement).autocomplete({
      source: function(request, response) {
        var url = encodeURIComponent(definitionData.definitions_url + "?" + definitionData.query_parameter + "=" + request.term);
        $.ajax({
          url: jsRoutes.api.Metadata.getUrl(url).url,
          dataType: 'json',
          success: function(data) {
            response(data);
          }
        });
      }
    });
  };

  this.scientific_variable = function(inputElement, definitionData) {
    $(inputElement).autocomplete({
      source: function(request, response) {
        var url = encodeURIComponent(definitionData.definitions_url + "?" + definitionData.query_parameter + "=" + request.term);
        $.ajax({
          url: jsRoutes.api.Metadata.getUrl(url).url,
          dataType: 'json',
          success: function(data) {
            // The vars list is in data.vars_data, and the categories in data.cat_data.
            // Assuming that "listjquery" will use a URL that returns filtered data,
            // we don't filter again. Returns cat_data with the vars list
            // if present, otherwise returns the original data.
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
  };

  this.datetime = function(inputElement, definitionData, selectElement) {
    // calling getScript makes sure that $.datepicker is available
    $.getScript("/assets/javascripts/jquery-ui-timepicker-addon.js", function(e) {

      // register an event listener to stop showing the datepicker if a different
      // metadata definition is later selected
      $(selectElement).on('change', function(event) {
        $(inputElement).datepicker('destroy');
      });

      // This widget uses the ISO 8601 format, such as 2016-01-01T10:00:00-06:00 or 2016-01-01T10:00:00Z.
      // This uses Trent Richardson's jQuery UI Timepicker add-on.
      // jQuery UI Datepicker options: http://api.jqueryui.com/datepicker/
      // jQuery UI Timepicker addon options: http://trentrichardson.com/examples/timepicker/
      $(inputElement).datetimepicker(
        { controlType: 'select',
          // Uses "select" instead of the default slider.
          // If we put "T" in the "separator" option instead of "timeFormat", the widget changes 2-digit year values xx we put directly into the field to 20xx; if we put "T" in timeFormat, then the year values are kept. So we used the latter.
          dateFormat: $.datepicker.ISO_8601,
          timeFormat: "'T'HH:mm:ssZ",
          separator: '',
          // Allows direct input.
          timeInput: true
        }
      );
    });
  };
};
