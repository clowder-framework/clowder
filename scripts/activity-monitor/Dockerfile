FROM clowder/pyclowder:onbuild

COPY extractor_info.json *.py  /home/clowder/

ENV RABBITMQ_QUEUE="ncsa.activity.monitor" \
    MAIN_SCRIPT="ncsa.activity.monitor.py"
