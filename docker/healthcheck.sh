#!/bin/bash

### Add trailing backslash if not in context
[[ "${CLOWDER_CONTEXT}" != */ ]] && CLOWDER_CONTEXT="${CLOWDER_CONTEXT}/"

curl -s --fail http://localhost:9000${CLOWDER_CONTEXT:-/}healthz || exit 1
