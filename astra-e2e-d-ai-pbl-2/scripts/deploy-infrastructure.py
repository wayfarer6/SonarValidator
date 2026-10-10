import pathlib,subprocess,shlex,json,hashlib,datetime
root=pathlib.Path(__file__).resolve().parents[2]
out=root/'astra-e2e-d-ai-pbl-2'
stage=pathlib.Path.home()/'.local/share/sonar-e2e-2/stage'
env=dict(l.split('=',1) for l in (root/'.env').read_text().splitlines() if '=' in l and not l.startswith('#'))
nodes=[('Gateway-Router',1,'Router'),('DMZ-Router',3,'Router'),('C4I-Network-Router',4,'Router'),('Survillance-Network-Router',5,'Router'),('VDI-Router',6,'Router')]+[(f'Switch-{i}',101+i,'Switch') for i in range(5)]+[('Firewall',2,'Firewall')]
results=[]
for name,last,kind in nodes:
    host='root@172.16.255.'+str(last)
    config='\n'.join([f'SERVER_IP=172.16.255.245;', 'SERVER_PORT=3000;', f'NODE_TYPE={kind};',f'AGENT_NAME={name};','MANAGEMENT_PREFIX=172.16.255.0/24;',f'TERMINAL_SHARED_SECRET={env["SONAR_TERMINAL_SHARED_SECRET"].strip(chr(34)).strip(chr(39))};'])+'\n'
    (stage/'default.conf').write_text(config);(stage/'default.conf').chmod(0o600)
    r={'name':name,'ip':host.split('@')[1],'timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat()}
    try:
        subprocess.run(['ssh','-o','BatchMode=yes','-o','ConnectTimeout=8',host,'mkdir -p /root/astra-e2e2-stage'],check=True,capture_output=True)
        for asset in ['sonar_validator_prober','default.conf','default_template.sqlite','rc-service','systemd']:
            subprocess.run(['scp','-r',str(stage/asset),host+':/root/astra-e2e2-stage/'],check=True,capture_output=True)
        subprocess.run(['scp',str(out/'scripts/deploy-node.sh'),host+':/root/astra-e2e2-stage/'],check=True,capture_output=True)
        result=subprocess.run(['ssh',host,'sh /root/astra-e2e2-stage/deploy-node.sh /root/astra-e2e2-stage '+('openrc' if kind=='Router' else 'container')],capture_output=True,text=True,timeout=80)
        r.update(exit_code=result.returncode,log=result.stdout+result.stderr)
    except Exception as e:r['error']=str(e)
    results.append(r)
    (out/'evidence/infrastructure-deployment.json').write_text(json.dumps(results,indent=2))
    print(name,r.get('exit_code',r.get('error')),flush=True)
