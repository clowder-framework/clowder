(function ($, Configuration) {
    // var url = '/api'+Configuration.url+'.html';
    var url = Configuration.url;
    var id = Configuration.tab.substring(1) + "_html_previewer";
    $(Configuration.tab).append("<div id='" + id + "' style='margin: 10px; border: 1px #F5F5F5 solid;'></div>");
    $.ajax({
        url: url,
        success: function (data) {
            $('#'+id).append(data);
        }});
}(jQuery, Configuration));