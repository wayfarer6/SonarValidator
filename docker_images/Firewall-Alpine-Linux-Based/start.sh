#!/bin/sh

echo "[1/4] Checking SSH host keys..."
# 이미 호스트 키가 생성되어 있는지 확인하고 없을 때만 생성
if [ ! -f /etc/ssh/ssh_host_rsa_key ]; then
    echo "Generating new SSH host keys..."
    ssh-keygen -A
fi
mkdir -p /run/sshd

echo "[2/4] Enabling IP forwarding..."
sysctl -w net.ipv4.ip_forward=1 || true

echo "[3/4] Applying nftables rules..."
if [ -f /etc/nftables.conf ]; then
    nft -f /etc/nftables.conf
fi

echo "[4/4] Starting network services..."
# 이미 실행 중이 아닐 때만 데몬 실행
if ! pgrep sshd > /dev/null; then
    /usr/sbin/sshd
fi

if ! pgrep dnsmasq > /dev/null; then
    dnsmasq
fi

echo "========================================"
echo " Router container is ready!"
echo "========================================"

exec /bin/sh