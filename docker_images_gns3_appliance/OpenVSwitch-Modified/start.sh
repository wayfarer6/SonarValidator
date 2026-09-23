#!/bin/sh

# 1. SSH 호스트 키 중복 생성 방지 체크
if [ ! -f /etc/ssh/ssh_host_rsa_key ]; then
    ssh-keygen -A
fi
mkdir -p /run/sshd

# 2. IP 포워딩 활성화 (라우터/방화벽 겸용 시 필수)
sysctl -w net.ipv4.ip_forward=1 || true

# 3. Open vSwitch 데이터베이스 및 데몬 구동 체크
mkdir -p /etc/openvswitch /var/run/openvswitch
if [ ! -f /etc/openvswitch/conf.db ]; then
    ovsdb-tool create /etc/openvswitch/conf.db /usr/share/openvswitch/vswitch.ovsschema
fi

# OVS 데몬이 안 켜져 있다면 백그라운드 실행
if ! pgrep ovsdb-server > /dev/null; then
    ovsdb-server --remote=punix:/var/run/openvswitch/db.sock \
                 --remote=db:Open_vSwitch,Open_vSwitch,manager_options \
                 --pidfile --detach
fi

if ! pgrep ovs-vswitchd > /dev/null; then
    ovs-vswitchd --pidfile --detach
fi

# 4. SSH 데몬 백그라운드 실행
if ! pgrep sshd > /dev/null; then
    /usr/sbin/sshd
fi

echo "========================================"
echo " GNS3 OVS Container is Ready!"
echo "========================================"

# 5. GNS3 콘솔 입력 씹힘 방지 및 쉘 유지
exec /bin/sh