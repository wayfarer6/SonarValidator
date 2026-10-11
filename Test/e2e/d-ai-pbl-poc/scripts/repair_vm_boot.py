exec(open('/home/ssh1032007/astra-e2e-2/gns3.py').read().split('nodes=api(')[0])
import subprocess
name=sys.argv[1]
n=next(n for n in api('/projects/'+project+'/nodes') if n['name']==name)
etc=pathlib.Path(n['node_directory'])/'etc'
subprocess.run(['docker','run','-i','--rm','--network','none','--entrypoint','/bin/sh','-v',str(etc)+':/target','gns3/ubuntu:resolute','-c',
 'mkdir -p /target/sonar_validator_prober; test ! -f /target/sonar_validator_prober/vm-boot.sh || cp /target/sonar_validator_prober/vm-boot.sh /target/sonar_validator_prober/vm-boot.sh.bak; cat > /target/sonar_validator_prober/vm-boot.sh; chmod 755 /target/sonar_validator_prober/vm-boot.sh'],input=b'''#!/bin/sh
ln -sf ../usr/lib/os-release /etc/os-release
if ! command -v ip >/dev/null && [ -d /etc/sonar_validator_prober/packages ]; then
  getent group messagebus >/dev/null || groupadd --system messagebus
  getent passwd messagebus >/dev/null || useradd --system -g messagebus -d /nonexistent -s /usr/sbin/nologin messagebus
  dpkg -i /etc/sonar_validator_prober/packages/*.deb >>/etc/sonar_validator_prober/packages-install.log 2>&1
fi
if [ -f /etc/sonar_validator_prober/start.sh ]; then sh /etc/sonar_validator_prober/start.sh; fi
exec /bin/bash
''',check=True)
url='http://127.0.0.1:3080/v2/projects/'+project+'/nodes/'+n['node_id']
req=urllib.request.Request(url,method='PUT',data=json.dumps({'properties':{'start_command':'/bin/sh /etc/sonar_validator_prober/vm-boot.sh'}}).encode(),headers={'Content-Type':'application/json'})
req.add_header('Authorization','Basic '+base64.b64encode((server['user']+':'+server['password']).encode()).decode())
urllib.request.urlopen(req).read()
if n['status']!='started': api('/projects/'+project+'/nodes/'+n['node_id']+'/start','POST')
print(name+' persistent boot repaired')
