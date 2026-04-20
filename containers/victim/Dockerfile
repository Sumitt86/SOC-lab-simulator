FROM ubuntu:22.04

RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    cron \
    curl \
    iproute2 \
    iptables \
    net-tools \
    netcat-openbsd \
    procps \
    python3 \
    python3-pip \
    tcpdump \
    && rm -rf /var/lib/apt/lists/*

RUN pip3 install --no-cache-dir psutil requests

# Copy monitoring agent
COPY monitor/ /opt/monitor/

# Copy startup script
COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 22 80 4444

CMD ["/start.sh"]
