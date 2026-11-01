FROM alpine:3.22
WORKDIR /root
COPY common/fix-links.sh ./gns3-fix-links.sh
COPY openvswitch/start.sh ./start.sh
RUN apk add --no-cache openvswitch openssh traceroute curl iputils busybox-extras \
      iproute2 nftables dnsmasq util-linux \
 && chmod +x start.sh gns3-fix-links.sh \
 && ./gns3-fix-links.sh
VOLUME ["/etc/openvswitch", "/root"]
CMD ["/root/start.sh"]
