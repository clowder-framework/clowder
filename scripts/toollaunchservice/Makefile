IMAGES	=  toolserver
VERSION = 1.0


testsrv:
	eval $(docker run --rm -it toolserver usage docker)

testcli:
	curl -H "Content-Type: application/json" -X POST -d '{"host":"http://localhost","dataset":"http://141.142.209.122/clowder/api/datasets/56a2c166e4b01a6c14f3040d/download","user":"USERNAME","pw":"PASSWORD"}' localhost:8080/tools/docker/ipython

testxfer:
	FILES.toolserver/usr/local/bin/clowder-xfer 'http://141.142.209.122/clowder/api/datasets/56a2c166e4b01a6c14f3040d/download' 'USERNAME' 'PASSWORD' /tmp/xfer.zip

testlog:
	curl -H "Content-Type: application/json" -X GET localhost:8080/logs
