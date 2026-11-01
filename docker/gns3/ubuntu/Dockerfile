FROM ubuntu:26.04
WORKDIR /root
COPY common/fix-links.sh ./gns3-fix-links.sh
COPY ubuntu/start.sh ./start.sh
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      openssh-server iproute2 nftables util-linux procps iputils-ping \
      traceroute curl ca-certificates nginx telnet \
 && rm -rf /var/lib/apt/lists/* \
 && rm -f /etc/ssh/ssh_host_* \
 && chmod +x start.sh gns3-fix-links.sh \
 && ./gns3-fix-links.sh
CMD ["/root/start.sh"]
