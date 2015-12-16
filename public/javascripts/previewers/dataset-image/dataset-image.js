(function($, Configuration) {
	var req = $.ajax({
		type: "GET",
		url: jsRoutes.api.Datasets.getPreviews(Configuration.dataset_id).url,
		dataType: "json"
	});

	req.done(function(data) {
        for (var i=0; i<data.length; ++i) {
            var file=data[i];
            if ('previews' in file) {
                var previews=file['previews'];
                for (var j=0; j<previews.length; ++j) {
                    var preview=previews[j];
                    if(preview['p_id'] == "Thumbnail" && preview['pv_route'] != "null") {
                        console.log(preview);
                        $("<link/>", {
                            rel: "stylesheet",
                            type: "text/css",
                            href: Configuration.path + "dataset-image.css"
                        }).appendTo(Configuration.div);

                        $(Configuration.div).append("<h4>Thumbnail</h4>" +
                            "<a id='ds_image_href' href='" + jsRoutes.controllers.Files.file(file['file_id']).url + "'>" +
                            "<img class='ds-image' id='ds_image_img' src='" + preview['pv_route'] + "'></img>" +
                            "</a>");

                        return;
                    }
                }
            }
        }
	});
}(jQuery, Configuration));
