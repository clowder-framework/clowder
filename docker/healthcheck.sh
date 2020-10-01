#!/bin/bash

curl -s --fail http://localhost:9000/api/status || exit 1
