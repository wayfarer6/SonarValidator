# ATICS — 적용 명령어

- C4I 서버 (Ubuntu 24.10)
- 콘솔: `telnet localhost:5022`

아래 명령어는 실제로 콘솔을 통해 장비에 적용된 그대로입니다.

```sh
echo ubuntu | sudo -S -k true
sudo sh -c 'cat > /etc/netplan/99-poc.yaml' <<'NEOF'
network:
  version: 2
  renderer: networkd
  ethernets:
    ens3:
      dhcp4: false
      addresses: [10.10.131.10/24]
      routes:
        - to: default
          via: 10.10.131.1
NEOF
sudo chmod 600 /etc/netplan/99-poc.yaml
sudo netplan apply
ip -br addr show ens3
ip route
```
