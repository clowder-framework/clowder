(function ($, Configuration) {
    $(document).ready(function () {
        // Get file metadata
        $.getJSON("/api/files/" + Configuration.id + "/metadata.jsonld")
            .then(function (metadata) {
                var vegaMetadata = metadata.find(function (d) {
                    return !!d.content.Vega5;
                });
                if (vegaMetadata) {
                    try {
                        var vegaSpecs = JSON.parse(vegaMetadata.content.Vega5);
                    } catch {
                        $(Configuration.tab).append('<div>Invalid JSON is provided for Vega specs</div>');
                        return;
                    }
                    // Load data, vega, and vega-lite scripts if `Vega` specs exist in the file metadata
                    $.when(
                        $.getScript("https://cdn.jsdelivr.net/npm/vega@5"),
                        $.getScript("https://cdn.jsdelivr.net/npm/vega-lite@4"),
                        $.getJSON(Configuration.url).then(function (data) {
                            return data;
                        })
                    ).then(function (_, _, data) {
                        // vega-embed must be loaded after vega and vega-lite
                        $.getScript("https://cdn.jsdelivr.net/npm/vega-embed@6").then(function () {
                            if (vegaSpecs["$schema"].indexOf("vega-lite") > -1) {
                                // Vega Lite only accepts one source of data, so we set the JSON data directly as source.
                                vegaSpecs.data = {values: data};
                            } else {
                                // Vega expects a list of data sources.
                                // The previewer expects the first item to be an object in the following form: {"name": "<data-source-name>", ...}.
                                // It then sets the JSON data as values of the first data source.
                                vegaSpecs.data[0].values = data
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