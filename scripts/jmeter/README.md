# JMeter Clowder Tests

This directory includes a simple [JMeter](https://jmeter.apache.org/) test plan to exercise the service and API.
JMeter includes both a GUI to create and run the test plans as well as a CLI. When stress testing, use the CLI.

Before running the test you will need to set `X-API-KEY` to your own Clowder API key. Change the web server protocol, 
server name, and port, in HTTP Request Defaults and the file path `File.path` you want to use to test in `Upload file`.

You can set the concurrency by changing the number of threads `ThreadGroup.num_threads` in Scenario 1.
This scenario includes the following steps:
- Create dataset
- Upload file to dataset
- Create folder
- Move file to folder
- Update file name
- Upload file metadata

There is a 1s pause between each call. Make that shorter or disable if stress testing.

To run the test from the command line use the following command:

`jmeter -n -t jmeter-clowder.jmx -l jmeter-out -e -o jmeter-out-html`

The file `jmeter-out` will include the status of each call. 
The `jmeter-out-html` will include an html page with summaries and visualizations of that output.