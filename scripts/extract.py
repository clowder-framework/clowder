#!/usr/bin/python -u
import sys
import json
import requests
import os
import time

host = 'http://kgm-d3.ncsa.illinois.edu:9000/'
key = 'r1ek3rs'
file = sys.argv[1]

#Upload file
if 'http://' in file:
	headers = {'Content-Type': 'application/json'}
	data = {}
	data["fileurl"] = file
	file_id = requests.post(host + 'api/extractions/upload_url?key=' + key, headers=headers, data=json.dumps(data)).json()['id']
else:
	file_id = requests.post(host + 'api/extractions/upload_file?key=' + key, files={'File' : (os.path.basename(file), open(file))}).json()['id']

#Poll until output is ready (optional)
while True:
	status = requests.get(host + 'api/extractions/' + file_id + '/status').json()
	if status['Status'] == 'Done': break
	time.sleep(1)

#Display extracted content
metadata = requests.get(host + 'api/extractions/' + file_id + '/metadata').json()
print json.dumps(metadata)
