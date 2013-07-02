#!/usr/bin/env python
import argparse
import urllib2
import json
import os
import requests
from urlparse import urljoin

def main():
    """ Traverse directory and upload files"""
    args = parse_arguments()
    for path in travers_dir(args.path):
        id = upload(args.host, args.key, path)
        file_path = os.path.join(path[0],path[1])
        file_url = urljoin(args.host, 'files')
        print "File uploaded: %s -> %s" % (file_path, file_url)

def parse_arguments():
    """ Must specify a directory path, a Medici url and a service key"""
    parser = argparse.ArgumentParser(description='Upload nested folders to medici')
    parser.add_argument('--path', required=True)
    parser.add_argument('--host', required=True)
    parser.add_argument('--key', required=True)
    return parser.parse_args()

def upload(host, key, path):
    """ Upload one file at a time """
    url= urljoin(host, 'api/files?key=' +  key)
    os.chdir(path[0])
    f = open(path[1], 'rb')
    r = requests.post(url, files={"File" : f})
    r.raise_for_status()
    return r.json()['id']

def travers_dir(dir):
    """ Traverse directory and return directory / file name tuple"""
    paths = []
    for root, dirs, files in os.walk(dir):
        for f in files:
            paths.append((root, f))
    return paths

if __name__ == '__main__':
    main()