FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# ─── Core packages + Vulnerable services ───────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Core utilities
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
    sudo \
    vim \
    wget \
    # Web server + PHP (Vector 1: Web App Exploitation)
    apache2 \
    libapache2-mod-php \
    php \
    php-mysql \
    php-cli \
    # MySQL server (Database for SQL Injection target)
    mysql-server \
    # SSH server (Vector 2: Brute Force)
    openssh-server \
    # DNS server (Vector 3: DNS Poisoning)
    dnsmasq \
    # Audit daemon (Blue Team Detection)
    auditd \
    audispd-plugins \
    # JSON processing for log forwarder
    jq \
    # Misc tools attackers expect to find
    sshpass \
    less \
    grep \
    findutils \
    && rm -rf /var/lib/apt/lists/*

RUN pip3 install --no-cache-dir psutil requests

# ─── SSH Configuration (INTENTIONALLY VULNERABLE) ──────────────────────
RUN mkdir -p /var/run/sshd
COPY config/sshd_config /etc/ssh/sshd_config

# Create vulnerable user: www-data with password 'www-data' (T1078)
RUN echo 'www-data:www-data' | chpasswd && usermod -s /bin/bash www-data

# Create additional user with weak creds
RUN useradd -m -s /bin/bash victim && echo 'victim:password123' | chpasswd

# VULNERABLE: www-data can run /bin/bash as root without password (T1548)
RUN echo 'www-data ALL=(ALL) NOPASSWD: /bin/bash' >> /etc/sudoers
RUN echo 'www-data ALL=(ALL) NOPASSWD: /usr/bin/find' >> /etc/sudoers

# ─── Web Application Setup (INTENTIONALLY VULNERABLE) ──────────────────
COPY vulnerable-app/index.php   /var/www/html/index.php
COPY vulnerable-app/admin.php   /var/www/html/admin.php
COPY vulnerable-app/config.php  /var/www/html/config.php
COPY vulnerable-app/.env        /var/www/html/.env

# Remove default Apache page
RUN rm -f /var/www/html/index.html

# Create uploads directory (writable by www-data for RCE)
RUN mkdir -p /var/www/html/uploads && \
    chown -R www-data:www-data /var/www/html/ && \
    chmod -R 777 /var/www/html/uploads

# VULNERABLE: config.php world-readable (T1552)
RUN chmod 644 /var/www/html/config.php && \
    chmod 644 /var/www/html/.env

# ─── MySQL Initialization ──────────────────────────────────────────────
COPY vulnerable-app/database.sql /docker-entrypoint-initdb.d/database.sql

# ─── DNS Configuration (INTENTIONALLY VULNERABLE) ─────────────────────
COPY config/dnsmasq.conf /etc/dnsmasq.conf

# ─── Sensitive Data (Exfiltration Targets) ─────────────────────────────
COPY sensitive-data/ /home/victim/sensitive_data/
RUN chown -R victim:victim /home/victim/sensitive_data/ && \
    chmod -R 755 /home/victim/sensitive_data/

# Create .env in victim home (T1552 — Unsecured Credentials)
COPY vulnerable-app/.env /home/victim/.env
RUN chown victim:victim /home/victim/.env && chmod 644 /home/victim/.env

# VULNERABLE: SSH directory writable (allows key injection T1098)
RUN mkdir -p /home/victim/.ssh && \
    touch /home/victim/.ssh/authorized_keys && \
    chown -R victim:victim /home/victim/.ssh && \
    chmod 700 /home/victim/.ssh && \
    chmod 600 /home/victim/.ssh/authorized_keys

RUN mkdir -p /home/www-data/.ssh && \
    touch /home/www-data/.ssh/authorized_keys && \
    chown -R www-data:www-data /home/www-data/.ssh && \
    chmod 700 /home/www-data/.ssh && \
    chmod 600 /home/www-data/.ssh/authorized_keys

# ─── Persistence Points (INTENTIONALLY WRITABLE) ──────────────────────
# VULNERABLE: Cron directories writable by www-data (T1053)
RUN chmod -R 777 /var/spool/cron/ 2>/dev/null || true && \
    chmod -R 777 /etc/cron.d/

# VULNERABLE: /tmp executable (malware staging area)
RUN chmod 1777 /tmp

# ─── Audit Rules (Blue Team Detection) ────────────────────────────────
RUN mkdir -p /etc/audit/rules.d
RUN echo '# Monitor cron modifications (DET-003)' > /etc/audit/rules.d/victim.rules && \
    echo '-w /var/spool/cron/ -p wa -k cron_modification' >> /etc/audit/rules.d/victim.rules && \
    echo '-w /etc/cron.d/ -p wa -k cron_modification' >> /etc/audit/rules.d/victim.rules && \
    echo '# Monitor SSH key changes (DET-005)' >> /etc/audit/rules.d/victim.rules && \
    echo '-w /home/ -p wa -k ssh_key_change' >> /etc/audit/rules.d/victim.rules && \
    echo '# Monitor sudoers changes (DET-007)' >> /etc/audit/rules.d/victim.rules && \
    echo '-w /etc/sudoers -p wa -k sudoers_change' >> /etc/audit/rules.d/victim.rules && \
    echo '-w /etc/sudoers.d/ -p wa -k sudoers_change' >> /etc/audit/rules.d/victim.rules && \
    echo '# Monitor systemd service creation (DET-010)' >> /etc/audit/rules.d/victim.rules && \
    echo '-w /etc/systemd/system/ -p wa -k service_creation' >> /etc/audit/rules.d/victim.rules && \
    echo '# Monitor sensitive file access' >> /etc/audit/rules.d/victim.rules && \
    echo '-w /etc/passwd -p r -k sensitive_read' >> /etc/audit/rules.d/victim.rules && \
    echo '-w /etc/shadow -p r -k sensitive_read' >> /etc/audit/rules.d/victim.rules

# ─── Log directories ──────────────────────────────────────────────────
RUN mkdir -p /var/log/apache2 /var/log/mysql && \
    touch /var/log/dnsmasq.log && \
    chmod 666 /var/log/dnsmasq.log

# ─── Monitoring Agent ─────────────────────────────────────────────────
COPY monitor/ /opt/monitor/

# ─── Startup Script ───────────────────────────────────────────────────
COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 22 80 53 3306 4444

CMD ["/start.sh"]
