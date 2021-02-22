# Vega 5 Previewer

Visualizes data with the given [Vega](https://vega.github.io/vega/) or [Vega Lite](https://vega.github.io/vega-lite/)
specs from the metadata

## How to use:

> Supports Vega v5 and Vega Lite v 4

> Note: Currently, this previewer only works with JSON and CSV data. Check out the examples on [Vega website](https://vega.github.io/vega/examples/) to see how data can be structured.

This previewer is only for files. After uploading your data json or csv file, add `vega5-spec` to its metadata and set its value to
the specs you want to use for visualization.
You need to include a complete and valid vega specs. The only attribute you can omit in the specs is `data.url`
for `vega-lite` and `data[0].url` for `vega`. The url attribute is automatically set to the file url by the previewer.
In the case of `vega`, the previewer assumes the first item in `data` list is the source for the data that come from the file. You can
have more data sources in that list, either pointing to other remote data or a transformation of the file data source.

You can create and test your specs in [Vega Editor](https://vega.github.io/editor/) before adding it to the file
metadata.

After adding the specs to the file metadata, refresh the page. If the specs are valid for the data, then the
visualization should load.

## Example:

Upload the following data as a csv file to a dataset in Clowder:

| date | amount      | program | state    |
|------|-------------|---------|----------|
| 2010 | 4534553     | P1      | Illinois |
| 2012 | 356554      | P1      | Illinois |
| 2013 | 234234      | P1      | Illinois |
| 2014 | 234654      | P1      | Illinois |
| 2015 | 32434       | P1      | Illinois |
| 2011 | 5656        | P1      | Illinois |
| 2011 | 3806535.05  | P2      | Illinois |
| 2010 | 3063068.92  | P2      | Illinois |
| 2013 | 2249368.86  | P2      | Illinois |
| 2014 | 2110818.23  | P2      | Illinois |
| 2015 | 1801392.49  | P2      | Illinois |
| 2009 | 1443174.99  | P2      | Illinois |
| 2016 | 1415779.09  | P2      | Illinois |
| 2012 | 1187657.01  | P2      | Illinois |
| 2008 | 763992.9    | P2      | Illinois |
| 2017 | 140875.29   | P2      | Illinois |
| 2007 | 15076.35    | P2      | Illinois |
| 2013 | 12318347.12 | P1      | Indiana  |
| 2014 | 11855957.24 | P1      | Indiana  |
| 2016 | 10131630.65 | P1      | Indiana  |
| 2011 | 9641385.14  | P1      | Indiana  |
| 2012 | 9579557.84  | P1      | Indiana  |
| 2010 | 8432728.5   | P2      | Indiana  |
| 2014 | 5945924     | P2      | Indiana  |
| 2016 | 5619332     | P2      | Indiana  |

Then add the following vega specs under `vega5-spec` metadata for the uploaded file and refresh the page:

```{json}
{
  "$schema": "https://vega.github.io/schema/vega-lite/v4.json",
  "mark": "bar",
  "data": {
    "format": {
      "type": "csv"
    }
  },
  "selection": {
    "program": {
      "type": "multi",
      "fields": [
        "program"
      ],
      "bind": "legend"
    }
  },
  "encoding": {
    "row": {
      "field": "state",
      "title": "States"
    },
    "x": {
      "field": "amount",
      "title": "Amount",
      "type": "quantitative",
      "axis": {
        "format": ",.0f"
      }
    },
    "y": {
      "field": "date",
      "title": "Year"
    },
    "color": {
      "field": "program",
      "scale": {
        "scheme": "category10"
      },
      "title": "Program",
      "legend": {
        "orient": "top"
      }
    },
    "opacity": {
      "condition": {
        "selection": "program",
        "value": 1
      },
      "value": 0.2
    },
    "tooltip": {
      "field": "Amount",
      "format": ",.01f"
    }
  }
}
```
