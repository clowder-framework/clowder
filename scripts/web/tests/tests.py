#!/usr/bin/python -u
import sys
import json
import requests
import os
import time
import smtplib
import socket
import time
import urllib2
import getopt
import netifaces as ni
from pymongo import MongoClient

def main():
	"""Run extraction bus tests."""
	host = ni.ifaddresses('eth0')[2][0]['addr']
	port = '9000'
	key = 'r1ek3rs'
	all_failures = False

	#Arguments
	opts, args = getopt.getopt(sys.argv[1:], 'h:p:k:a')

	for o, a in opts:
		if o == '-h':
			host = a
		elif o == '-p':
			port = a
		elif o == '-k':
			key = a
		elif o == '-a':
			all_failures = True
		else:
			assert False, "unhandled option"

	print 'Testing: ' + host + '\n'

	#Remove previous outputs
	for output_filename in os.listdir('tmp'):
		if(output_filename[0] != '.'):
			os.unlink('tmp/' + output_filename)

	#Read in tests
	with open('tests.txt', 'r') as tests_file:
		lines = tests_file.readlines()
		count = 0;
		mailserver = smtplib.SMTP('localhost')
		failure_report = ''
		t0 = time.time()

		for line in lines:
			if line and not line.startswith('#'):
				parts = line.split(' ', 1)
				input_filename = parts[0]
				outputs = parts[1].split(',')

				for output in outputs:
					count += 1
					POSITIVE = True
					output = output.strip();

					#Check for negative tests
					if(output[0] == '!'):
						POSITIVE = False
						output = output[1:]

					#Check for input files
					if(output[0] == '"'):
						output = output[1:-1]
					else:
						if output.startswith("http://"):
							output = urllib2.urlopen(output).read(1000).strip()
						else:
							output = open(output).read(1000).strip()
					
					#Print out test
					if POSITIVE:	
						print(input_filename + ' -> "' + output + '"'),
					else:
						print(input_filename + ' -> !"' + output + '"'),

					#Run test
					metadata = extract(host, port, key, input_filename, 60)
					#print metadata
				
					#Write derived data to a file for later reference
					output_filename = 'tmp/' + str(count) + '_' + os.path.splitext(os.path.basename(input_filename))[0] + '.txt'

					with open(output_filename, 'w') as output_file:
						output_file.write(metadata)
						
					os.chmod(output_filename, 0776)		#Give web application permission to overwrite

					#Check for expected output
					if not POSITIVE and metadata.find(output) is -1:
						print '\t\033[92m[OK]\033[0m\n'
					elif metadata.find(output) > -1:
						print '\t\033[92m[OK]\033[0m\n'
					else:
						print '\t\033[91m[Failed]\033[0m\n'

						report = 'Test-' + str(count) + ' failed.  Expected output "'
								
						if not POSITIVE:
							report += '!'

						report += output + '" was not extracted from:\n\n' + input_filename + '\n\n'
						failure_report += report;

						#Send email notifying watchers	
						if all_failures:
							with open('failure_watchers.txt', 'r') as watchers_file:
								watchers = watchers_file.read().splitlines()
								
								message = 'From: \"' + host + '\" <devnull@ncsa.illinois.edu>\n'
								message += 'To: ' + ', '.join(watchers) + '\n'
								message += 'Subject: DTS Test Failed\n\n'
								message += report
								message += 'Report of last run can be seen here: \n\n http://' + socket.getfqdn() + '/dts/tests/tests.php?dts=' + host + '&run=false&start=true\n'

								for watcher in watchers:
									mailserver.sendmail('', watcher, message)

		dt = time.time() - t0
		print 'Elapsed time: ' + timeToString(dt)

    #Save to mongo
		client = MongoClient()
		db = client['tests']
		collection = db['dts']
		document = {'time': int(round(time.time()*1000)), 'elapsed_time': dt}
		collection.insert(document)

		#Send a final report of failures
		if failure_report:
			with open('failure_watchers.txt', 'r') as watchers_file:
				watchers = watchers_file.read().splitlines()
	
				message = 'From: \"' + host + '\" <devnull@ncsa.illinois.edu>\n'
				message += 'To: ' + ', '.join(watchers) + '\n'
				message += 'Subject: DTS Test Failure Report\n\n'
				message += failure_report			
				message += 'Report of last run can be seen here: \n\n http://' + socket.getfqdn() + '/dts/tests/tests.php?dts=' + host + '&run=false&start=true\n\n'
				message += 'Elapsed time: ' + timeToString(dt)

				for watcher in watchers:
					mailserver.sendmail('', watcher, message)
		else:
			with open('pass_watchers.txt', 'r') as watchers_file:
				watchers = watchers_file.read().splitlines()

				message = 'From: \"' + host + '\" <devnull@ncsa.illinois.edu>\n'
				message += 'To: ' + ', '.join(watchers) + '\n'
				message += 'Subject: DTS Tests Passed\n\n';
				message += 'Elapsed time: ' + timeToString(dt)

				for watcher in watchers:
					mailserver.sendmail('', watcher, message)

		mailserver.quit()

def extract(host, port, key, file, wait):
	"""Pass file to Medici extraction bus."""
	#Upload file
	headers = {'Content-Type': 'application/json'}
	data = {}
	data["fileurl"] = file
	file_id = requests.post('http://' + host + ':' + port + '/api/extractions/upload_url?key=' + key, headers=headers, data=json.dumps(data)).json()['id']

	#Poll until output is ready (optional)
	while wait > 0:
		status = requests.get('http://' + host + ':' + port + '/api/extractions/' + file_id + '/status').json()
		if status['Status'] == 'Done': break
		time.sleep(1)
		wait -= 1

	#Display extracted content (TODO: needs to be one endpoint!!!)
	metadata = json.dumps(requests.get('http://' + host + ':' + port + '/api/extractions/' + file_id + '/metadata').json())
	metadata += '\n'
	metadata += json.dumps(requests.get('http://' + host + ':' + port + '/api/files/' + file_id + '/technicalmetadatajson').json())

	return metadata

def timeToString(t):
	"""Return a string represntation of the give elapsed time"""
	h = int(t / 3600);
	m = int((t % 3600) / 60);
	s = int((t % 3600) % 60);
			
	if h > 0:
		return str(round(h + m / 60.0, 2)) + ' hours';
	elif m > 0:
		return str(round(m + s / 60.0, 2)) + ' minutes';
	else:
		return str(s) + ' seconds';

if __name__ == '__main__':
	main()
