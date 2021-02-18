# Vega 5 Previewer

Visualizes data with the given [Vega](https://vega.github.io/vega/) or [Vega Lite](https://vega.github.io/vega-lite/)
specs from the metadata

## How to use:

> Supports Vega v5 and Vega Lite v 4

> Note: Currently, this previewer only works with JSON and CSV data. Check out the examples on [Vega website](https://vega.github.io/vega/examples/) to see how data can be structured.

This previewer is only for files. After uploading your data json or csv file, add `Vega5` to its metadata and set its value to
the specs you want to use for visualization.

You need to include a complete and valid vega specs. The only attribute you can omit in the specs is `data.url`
for `vega-lite` and `data[0].url` for `vega`. The url attribute is automatically set to the file url by the previewer.
In the case of `vega`, the previewer assumes the first item in `data` list is the source for the data that come from the file. You can
have more data sources in that list, either pointing to other remote data or a transformation of the file data source.

You can create and test your specs in [Vega Editor](https://vega.github.io/editor/) before adding it to the file
metadata.

After adding the specs to the file metadata, refresh the page. If the specs are valid for the data, then the
visualization should load.
