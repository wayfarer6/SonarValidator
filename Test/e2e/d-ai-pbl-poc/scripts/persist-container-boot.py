exec(open('/home/ssh1032007/astra-e2e-2/gns3.py').read().split('nodes=api(')[0])
import subprocess
for n in api('/projects/'+project+'/nodes'):
    if n['name'] not in ['Firewall']+[f'Switch-{i}' for i in range(5)]:continue
    c=n['properties']['container_id']
    boot=subprocess.check_output(['docker','exec',c,'cat','/root/start.sh']).decode()
    # Keep existing device startup, and restore the persisted agent/ACL after it.
    prefix='sh /etc/sonar_validator_prober/start.sh\n'
    if n['name']=='Firewall':
        prefix='command -v bash >/dev/null || apk add --no-network /etc/sonar_validator_prober/bash-*.apk\nsh /etc/sonar_validator_prober/cso-apply.sh\n'+prefix
    boot=boot.replace('exec /bin/sh',prefix+'exec /bin/sh')
    subprocess.run(['docker','exec','-i',c,'sh','-c','cat > /etc/sonar_validator_prober/network-start.sh; chmod 755 /etc/sonar_validator_prober/network-start.sh'],input=boot.encode(),check=True)
    req=urllib.request.Request('http://127.0.0.1:3080/v2/projects/'+project+'/nodes/'+n['node_id'],method='PUT',data=json.dumps({'properties':{'start_command':'/bin/sh /etc/sonar_validator_prober/network-start.sh'}}).encode(),headers={'Content-Type':'application/json'})
    req.add_header('Authorization','Basic '+base64.b64encode((server['user']+':'+server['password']).encode()).decode())
    urllib.request.urlopen(req).read()
    print(n['name']+' persistent startup configured')
