(function ($, Configuration) {
    $(document).ready(function () {
        // Get file metadata
        $.getJSON(jsRoutes.api.Files.getMetadataJsonLD(Configuration.id).url)
            .then(function (metadata) {
                var vegaMetadata = metadata.find(function (d) {
                    return !!d.content["vega5-spec"];
                });
                if (vegaMetadata) {
                    try {
                        var vegaSpecs = JSON.parse(vegaMetadata.content["vega5-spec"]);
                    } catch {
                        $(Configuration.tab).append('<div>Invalid JSON is provided for Vega specs</div>');
                        return;
                    }
                    // Load data, vega, and vega-lite scripts if `Vega` specs exist in the file metadata
                    $.when(
                        $.getScript("https://cdn.jsdelivr.net/npm/vega@5"),
                        $.getScript("https://cdn.jsdelivr.net/npm/vega-lite@4")
                    ).then(function () {
                        // vega-embed must be loaded after vega and vega-lite
                        $.getScript("https://cdn.jsdelivr.net/npm/vega-embed@6").then(function () {
                            // Set the file url as data source
                            if (vegaSpecs["$schema"].indexOf("vega-lite") > -1) {
                                // Vega Lite only accepts one source of data, so we set the JSON data directly as source.
                                vegaSpecs.data.url = Configuration.url;
                            } else {
                                // Vega expects a list of data sources.
                                // The previewer expects the first item to be used as data source for the uploaded file.
                                vegaSpecs.data[0].url = Configuration.url;
                            }

                            try {
                                vegaEmbed(Configuration.tab, vegaSpecs);
                            } catch (error) {
                                $(Configuration.tab).append('<div>Could not render Vega visualization. See the console for more details.</div>');
                                console.error(error);
                            }
                        });
                    });
                }
            });
    });
}(jQuery, Configuration));