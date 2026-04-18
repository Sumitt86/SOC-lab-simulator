FROM ubuntu:22.04

RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    curl \
    iproute2 \
    net-tools \
    netcat-openbsd \
    nmap \
    procps \
    && rm -rf /var/lib/apt/lists/*

# Copy attack scripts
COPY attacks/ /opt/attacks/
RUN chmod +x /opt/attacks/*.sh

CMD ["tail", "-f", "/dev/null"]
