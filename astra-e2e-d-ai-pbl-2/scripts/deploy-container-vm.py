import subprocess,pathlib,sys,shlex,json
root=pathlib.Path(__file__).resolve().parents[2];out=root/'astra-e2e-d-ai-pbl-2'
name=sys.argv[1];host='ssh1032007@192.168.122.1';remote='/home/ssh1032007/astra-e2e-2'
env=dict(l.split('=',1) for l in (root/'.env').read_text().splitlines() if '=' in l and not l.startswith('#'))
conf=pathlib.Path.home()/'.local/share/sonar-e2e-2/vm-default.conf'
conf.write_text(f'SERVER_IP=192.168.122.58;\nSERVER_PORT=3000;\nNODE_TYPE=VM;\nAGENT_NAME={name};\nMANAGEMENT_PREFIX=172.16.255.0/24;\nTERMINAL_SHARED_SECRET='+env['SONAR_TERMINAL_SHARED_SECRET'].strip('"\'')+';\n');conf.chmod(0o600)
def run(cmd,**kw):return subprocess.run(cmd,check=True,**kw)
run(['scp',str(conf),host+':'+remote+'/stage/default.conf'])
run(['ssh',host,'python3 '+remote+'/repair_vm_boot.py '+shlex.quote(name)])
container='GNS3.'+name+'.5a790fe8-a6eb-4251-b21e-010258ac0241'
script=f'''set -eu
sleep 3
docker cp {remote}/stage {container}:/root/astra-e2e2-stage
docker cp {remote}/deploy-node.sh {container}:/root/astra-e2e2-stage/deploy-node.sh
docker cp {remote}/packages {container}:/gns3volumes/etc/sonar_validator_prober/
docker exec {container} sh -c 'ln -sf ../usr/lib/os-release /etc/os-release; getent group messagebus >/dev/null || groupadd --system messagebus; getent passwd messagebus >/dev/null || useradd --system -g messagebus -d /nonexistent -s /usr/sbin/nologin messagebus; dpkg -i /etc/sonar_validator_prober/packages/*.deb; sh /root/astra-e2e2-stage/deploy-node.sh /root/astra-e2e2-stage container; ip -br -4 addr; ip route'
'''
r=run(['ssh',host,'sh -s'],input=script,text=True,capture_output=True)
(out/'logs'/('deploy-'+name+'.log')).write_text(r.stdout+r.stderr)
print(name+' deployed')
