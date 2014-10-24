#!/usr/bin/python2.7
import requests
import urllib
import sys

exchange='medici'
rabbitmq='http://guest:guest@localhost:15672/api'

r = requests.get('%s/bindings' % rabbitmq)
if r.status_code != 200:
  print r.text
  sys.exit(-1)
bindings = r.json()

for binding in bindings:
  if binding['source'] == exchange:
    vhost = urllib.quote_plus(binding['vhost'])
    queue = urllib.quote_plus(binding['destination'])
    r = requests.get('%s/queues/%s/%s' % (rabbitmq, vhost, queue))
    if r.status_code != 200:
      print r.text
      sys.exit(-1)
    queue = r.json()
    print "%s :" % binding['destination']
    print "\tmessages waiting    : %d" % queue['messages_ready']
    print "\tmessages processing : %d" % queue['messages_unacknowledged']
    print "\thosts :"
    for consumer in queue['consumer_details']:
      details = consumer['channel_details']
      print "\t\t%s" % details['peer_host']
