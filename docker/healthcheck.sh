#!/bin/bash

### Add trailing backslash if not in context
[[ "${CLOWDER_CONTEXT}" != */ ]] && CLOWDER_CONTEXT="${CLOWDER_CONTEXT}/"

curl -s --fail http://localhost:8000${CLOWDER_CONTEXT:-/}api/status || exit 1
