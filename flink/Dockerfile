FROM python:3.10 AS base
RUN apt-get update && \
    apt-get install -y openjdk-11-jdk-headless && \
    apt-get auto-remove -y && \
    mkdir /work && \
    useradd -m snek && \
    chown snek /work

USER snek
ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-arm64
RUN python3 -m venv /home/snek/venv && \
    /home/snek/venv/bin/pip install -U pip wheel

COPY requirements.txt /home/snek/requirements.txt
RUN /home/snek/venv/bin/pip install -r /home/snek/requirements.txt && \
    pip cache purge

WORKDIR /work
ENTRYPOINT [ "/home/snek/venv/bin/python" ]
