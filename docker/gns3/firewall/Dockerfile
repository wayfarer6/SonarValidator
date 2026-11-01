FROM alpine:3.24
WORKDIR /root
COPY common/fix-links.sh ./gns3-fix-links.sh
COPY firewall/start.sh ./start.sh
COPY firewall/nftables.conf /etc/nftables.conf
COPY firewall/dnsmasq.conf /etc/dnsmasq.conf
RUN apk add --no-cache openssh nftables dnsmasq iproute2 busybox-extras util-linux \
      curl iputils \
 && chmod +x start.sh gns3-fix-links.sh \
 && ./gns3-fix-links.sh
CMD ["/root/start.sh"]
