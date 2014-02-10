#!/usr/bin/env python
import argparse
import urllib2
import json
import os
import requests
from urlparse import urljoin

host = 'http://localhost:9000'
key  = 'r1ek3rs'
headers={'Content-Type': 'application/json'}

def main():
    """Search geostreams"""
    search()

def search():
    """ Upload one file at a time """
    url= urljoin(host, 'api/geostreams/search?key=' +  key)
    print url
    r = requests.get(url,headers=headers)
    r.raise_for_status()
    print r.json()[0]['start_time']
    return r.json()

if __name__ == '__main__':
    main()