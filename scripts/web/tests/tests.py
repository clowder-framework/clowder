#!/usr/bin/python -u
import sys
import json
import requests
import os
import time
import smtplib
import socket

host = 'http://kgm-d3.ncsa.illinois.edu:9000/'
key = 'r1ek3rs'

def main():
	"""Run extraction bus tests."""
	with open('tests.txt', 'r') as tests_file:
		#Read in tests
		lines = tests_file.readlines()
		count = 0;
		mailserver = smtplib.SMTP('localhost')

		for line in lines:
			if not line.startswith('#'):
				parts = line.split('\t')
				input_filename = parts[0]
				outputs = parts[1].split(',')

				for output in outputs:
					output = output.strip();
					count += 1

					print(input_filename + ' -> "' + output + '"'),

					#Run test
					metadata = extract(host, key, input_filename)
				
					#Write derived data to a file for later reference
					output_filename = os.path.splitext(os.path.basename(input_filename))[0] + '.txt'

					with open('tmp/' + str(count) + '_' + output_filename, 'w') as output_file:
						output_file.write(metadata)

					#Check for expected output
					if output[0] is '!' and metadata.find(output) is -1:
						print '\t\033[92m[OK]\033[0m'
					elif metadata.find(output) > -1:
						print '\t\033[92m[OK]\033[0m'
					else:
						print '\t\033[91m[Failed]\033[0m'

						#Send email notifying watchers	
						with open('watchers.txt', 'r') as watchers_file:
							watchers = watchers_file.readlines()
		
							for watcher in watchers:
								watcher = watcher.strip()

								message = 'Subject: DTS Test Failed\n\n'
								message += 'Test-' + str(count) + ' failed.  Expected output "' + output + '" was not extracted from:\n\n' + input_filename + '\n\n'
								message += 'Report of last run can be seen here: \n\n http://' + socket.getfqdn() + '/dts/tests/tests.php?run=false&start=true\n'
								
								mailserver.sendmail('', watcher, message)

		mailserver.quit()

def extract(host, key, file):
	"""Pass file to Medici extraction bus."""
	#Upload file
	headers={'Content-Type': 'application/json'}
	data={}
	data["fileurl"] = file
	file_id = requests.post(host + 'api/extractions/upload_url?key=' + key, headers=headers, data=json.dumps(data)).json()['id']

	#Poll until output is ready (optional)
	while True:
		status = requests.get(host + 'api/extractions/' + file_id + '/status').json()
		if status['Status'] == 'Done': break
		time.sleep(1)

	#Display extracted content
	metadata = requests.get(host + 'api/extractions/' + file_id + '/metadata').json()
	return json.dumps(metadata)

if __name__ == '__main__':
	main()
