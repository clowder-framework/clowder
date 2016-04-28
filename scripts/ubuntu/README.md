This directory contains the upstart script as well as a simple script
to install clowder. To install clowder you will first need to install
java and mongo:

```
echo "deb http://repo.mongodb.org/apt/ubuntu trusty/mongodb-org/3.0 multiverse" > /etc/apt/sources.list.d/mongodb-org-3.0.list
apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 7F0CEB10

apt-get -y update
apt-get -y install openjdk-7-jre-headless mongodb-org unzip
```

You can also install the optional packages for rabbitmq and elasticsearch

```
echo "deb http://www.rabbitmq.com/debian/ testing main" > /etc/apt/sources.list.d/rabbitmq.list
curl https://www.rabbitmq.com/rabbitmq-signing-key-public.asc | apt-key add -

echo "deb http://packages.elasticsearch.org/elasticsearch/1.3/debian stable main" > /etc/apt/sources.list.d/elasticsearch.list
curl http://packages.elasticsearch.org/GPG-KEY-elasticsearch | apt-key add -

apt-get -y update
apt-get -y install rabbitmq-server elasticsearch
```

Finally if you want to use NGINX as a proxy server

```
apt-get -y install nginx
```

Next copy the clowder.conf over to /etc/upstart and modify the path from
/home/ubuntu/clowder to the path where clowder is actually installed.

To install clowder you can use the update-clowder.sh script (again change
the path from /home/ubuntu to the parent folder where clowder should be
installed). You can run the update-clowder.sh on a cronjob so you will
allways have the latest version of clowder installed.


To have nginx be a proxy for clowder you can use the following file. This
will assume that clowder will be accesses as http://server/clowder/. You
will also need to add the following to your custom.conf in clowder/custom
folder:

```
application.context="/clowder/"
```

The nginx configuration file:

```
server {
  listen 80;
  client_max_body_size 0;

  proxy_set_header   Host $host;
  proxy_set_header   X-Real-IP $remote_addr;
  proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
  proxy_http_version 1.1;
  port_in_redirect   off;

  root /usr/share/nginx/www;
  index index.html index.htm;

  location / {
      try_files $uri $uri/ /index.html;
  }

  rewrite ^/medici$ /clowder/ permanent;
  rewrite ^/medici/(.*)$ /clowder/$1 permanent;

  rewrite ^/clowder$ /clowder/ permanent;
  location /clowder/ {
    proxy_pass http://localhost:9000;
  }
}
```


