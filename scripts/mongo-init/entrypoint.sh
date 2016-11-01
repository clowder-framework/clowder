#!/bin/bash

if [ -z "$HOSTIP" ] | [ -z "$PASSWORD" ] | [ -z "$EMAIL_ADDRESS" ]; then
        echo "parameters are empty (hostip, password, emailaddress)"
        exit
fi

hostip=$HOSTIP
emailaddress=$EMAIL_ADDRESS

if [ -z "$ADMIN" ]; then
        admin=false
else
        admin=$ADMIN
fi

if [ -z "$FIRST_NAME" ]; then
        firstname="firstname"
else
        firstname=$FIRST_NAME
fi

if [ -z "$LAST_NAME" ]; then
        lastname="lastname"
else
        lastname=$LAST_NAME
fi

fullname=$firstname$lastname

echo "+++++++++++++++++++++++++++++++++++++"
echo "Add User Account into Clowder"
echo ""
echo $(mongo --version)
echo ""
echo "++++++++++++++++++++++++++++++++++++++"

while [ $(netstat -plnt | grep ':9000')]; do
        sleep 1
done

echo "clowder is starting"

query=$(mongo $HOSTIP:27017/clowder --eval "db.social.users.find({email: '"$emailaddress"', \"termsOfServices.accepted\": true}, {identityId:1})")

if echo $query | grep -q "$emailaddress"; then
        echo "account found"
else
        echo "account not found!"
	hasher=$(python3 passwd)
	mongo $HOSTIP:27017/clowder --eval "db.social.users.remove({email: '"$emailaddress"'})"
        mongo $HOSTIP:27017/clowder --eval "db.social.users.insert(
        {
                identityId: {
			userId: '"$emailaddress"', 
			providerId: \"userpass\"
		}, 
                _typeHint: \"models.ClowderUser\", 
                firstName: '"$firstname"', 
                lastName:  '"$lastname"', 
                fullName:  '"$fullname"', 
                email:     '"$emailaddress"', 
                authMethod: {
			_typeHint: \"securesocial.core.AuthenticationMethod\", 
			method: \"userPassword\"
		}, 
                passwordInfo: {
			_typeHint: \"securesocial.core.PasswordInfo\", 
	                hasher: \"bcrypt\", 
        	        password: '"$hasher"'
		},
                active: true, 
                serverAdmin: false, 
                termsOfServices: {
			accepted: true, 
			acceptedDate: ISODate(\"2020-10-25T20:17:02.591Z\"), 
			acceptedVersion: \"2016-06-06\"}
        });db.fsyncLock();db.fsyncUnlock();"
fi

