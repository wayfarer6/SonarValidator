import pathlib,subprocess,sys
name=sys.argv[1];root=pathlib.Path(__file__).resolve().parents[2]
env=dict(l.split('=',1) for l in (root/'.env').read_text().splitlines() if '=' in l and not l.startswith('#'))
p=pathlib.Path.home()/'.local/share/sonar-e2e-2/qemu-default.conf'
p.write_text(f'SERVER_IP=172.16.255.245;\nSERVER_PORT=3000;\nNODE_TYPE=VM;\nAGENT_NAME={name};\nMANAGEMENT_PREFIX=172.16.255.0/24;\nTERMINAL_SHARED_SECRET='+env['SONAR_TERMINAL_SHARED_SECRET'].strip('"\'')+';\n');p.chmod(0o600)
subprocess.run(['scp',str(p),'ssh1032007@192.168.122.1:astra-e2e-2/qemu-default.conf'],check=True)
r=subprocess.run(['ssh','ssh1032007@192.168.122.1','python3','astra-e2e-2/qemu-console.py',name,'deploy'],text=True,capture_output=True)
(root/'astra-e2e-d-ai-pbl-2/logs'/('deploy-'+name+'.log')).write_text(r.stdout+r.stderr)
print(name,r.returncode);sys.exit(r.returncode)
