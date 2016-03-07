IMAGES	=  toolserver
VERSION = 1.0
include ../../../devtools/Makefiles/Makefile.nds


testsrv:
	docker run --rm -it -p 8080:8080 -v /var/run/docker.sock:/var/run/docker.sock toolserver /usr/local/bin/toolserver

testcli:
	curl -H "Content-Type: application/json" -X POST -d '{"host":"http://localhost","dataset":"http://141.142.209.122/clowder/api/datasets/56a2c166e4b01a6c14f3040d/download","user":"mburnet2@illinois.edu","pw":"tSzx7dINA8RxFEKp7sX8"}' localhost:8080/tools/docker/ipython

testxfer:
	FILES.toolserver/usr/local/bin/clowder-xfer 'http://141.142.209.122/clowder/api/datasets/56a2c166e4b01a6c14f3040d/download' 'mburnet2@illinois.edu' 'tSzx7dINA8RxFEKp7sX8' /tmp/xfer.zip

testlog:
	curl -H "Content-Type: application/json" -X GET localhost:8080/logs
