FROM elasticsearch:2

RUN apt-get update && apt-get install -y zip && rm -rf /var/lib/apt/lists/* && \
    for x in $(find /usr/share/elasticsearch -name \*.jar); do \
      zip -d $x org/apache/log4j/net/JMSAppender.class org/apache/log4j/net/SocketServer.class | grep 'deleting:' && echo "fixed $x"; \
    done; \
    echo "removed JMSAppender and SocketServer"
