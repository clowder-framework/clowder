#!/bin/bash

if [ -z "$1" ]; then
    echo "parameter is empty(password)"
fi

IFS='%'
serviceName=$(echo $1 | tr '[:upper:]' '[:lower:]')
firstName=$serviceName
lastName="SERVICE"
fullName="$firstName $lastName"
emailaddress="devnull+$serviceName@ncsa.illinois.edu"
now=`date -u +"%Y-%m-%dT%H:%M:%SZ"`

# only works on linux, not mac
#hasher=$(head /dev/urandom | tr -dc A-Za-z0-9 | head -c 30)
#hasher=$(openssl rand -base64 30)
hasher=$(uuidgen | sed 's/-//g')
echo "Password for $serviceName is $hasher"

# htpasswd is used for bcrypt. It needs apache2-utils to be installed
#hasher_bcrypted=$(htpasswd -bnBC 10 "" "$hasher" | tr -d ':\n' | sed 's/^\$2y\$/\$2a\$/')
# can also use passlib, require `pip install passlib bcrypt`
hasher_bcrypted=$(python -c 'import sys, passlib.hash; print("$2a"+passlib.hash.bcrypt.encrypt(sys.argv[1], rounds=10)[3:])' "$hasher")
echo "Encrypted password is $hasher_bcrypted"

echo "db.social.users.remove({email: '$emailaddress'})

db.social.users.insert(
{
    identityId: {
        userId: '$serviceName',
        providerId: 'userpass'
    },
    _typeHint : 'models.ClowderUser',
    firstName: '$firstName',
    lastName: '$lastName',
    fullName: '$fullName',
    email: '$emailaddress',
    authMethod: {
        _typeHint: 'securesocial.core.AuthenticationMethod',
        method: 'userPassword'
    },
    passwordInfo: {
        _typeHint: 'securesocial.core.PasswordInfo',
        hasher: 'bcrypt',
        password: '$hasher_bcrypted'
    },
    status : 'Active',
        termsOfServices: {
        accepted: true,
        acceptedDate: ISODate('$now')
    }
}, {w: 'majority', j: true});"

unset IFS
