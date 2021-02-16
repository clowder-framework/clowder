# Vega 5 Previewer

Visualizes data with the given [Vega](https://vega.github.io/vega/) or [Vega Lite](https://vega.github.io/vega-lite/) specs from the metadata

## How to use:

> Supports Vega v5 and Vega Lite v 4

> Note: Currently, this previewer only works with JSON data. Check out the examples on [Vega website](https://vega.github.io/vega/examples/) to see how data can be structured.

This previewer is only for files. After uploading your data json file, add `Vega5` to its metadata and
set its value to the specs you want to use for visualization.

If you are using `vega-lite` specs, the `data` attribute is automatically added to the specs.
For `vega` specs, you need to include the `data` attribute as a list with at least one item.
The first item must be an object with a `name` attribute, which is referenced in the rest of the specs.
Its `values` are set automatically from the JSON data. See Vega documentation for more info on the specs.

You can create and test your specs in [Vega Editor](https://vega.github.io/editor/)
before adding it to the file metadata.

After adding the specs to the file metadata, refresh the page. If the specs are valid for the data, then the visualization should load.