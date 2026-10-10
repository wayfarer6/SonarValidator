import base64,json,pathlib,sys,time
from telnet_lib import Telnet
name=sys.argv[1];action=sys.argv[2] if len(sys.argv)>2 else 'deploy'
ports={'ATICS':5022,'KNCCS':5024,'AFCCS':5026,'Public-Web-Server':5036}
t=Telnet(port=ports[name],timeout=15).connect()
t.send('')
for attempt in range(10):
    idx,_,output=t.expect([r'login:\s*$',r'[Pp]assword:\s*$',r'[$#]\s*$'],timeout=20)
    if idx in (0,1):t.send('ubuntu')
    elif idx==2:break
    else:t.send('')
else:raise RuntimeError('Console login timed out')
t.send('sudo -i')
idx,_,output=t.expect([r'[Pp]assword[^\n]*:',r'#\s*$'],timeout=15)
if idx==0:
    t.send('ubuntu');idx,_,output=t.expect(r'#\s*$',timeout=15)
t.send('stty -echo');t.expect(r'#\s*$',timeout=5)
if action=='deploy':
    config=(pathlib.Path.home()/'astra-e2e-2/qemu-default.conf').read_bytes()
    payload=base64.b64encode(config).decode()
    command=f"set -e; mkdir -p /root/astra-e2e2-stage; cd /root/astra-e2e2-stage; curl -f --connect-timeout 10 --max-time 90 http://172.16.255.245:8099/bundle.tar.gz -o bundle.tar.gz; tar xzf bundle.tar.gz; curl -fsS http://172.16.255.245:8099/deploy-node.sh -o deploy-node.sh; printf '%s' '{payload}' | base64 -d > default.conf; chmod 600 default.conf; sh deploy-node.sh /root/astra-e2e2-stage systemd; systemctl is-active sonar_validator_prober; ip -br -4 addr; ip route; printf '\\nQEMU_DEPLOY_DONE\\n'"
elif action=='shutdown':command='sync; systemctl poweroff'
else:command=action+"; printf '\\nQEMU_COMMAND_DONE\\n'"
t.send(command)
if action=='shutdown':
 print(t.wait(3))
else:
 idx,_,output=t.expect(r'\nQEMU_(DEPLOY|COMMAND)_DONE\r?\n',timeout=120)
 print(output)
 if idx<0:raise RuntimeError('Console command did not finish')
 t.send('stty echo');t.wait(.2)
t.close()
