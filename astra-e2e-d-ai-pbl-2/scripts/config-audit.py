import hashlib, json, pathlib, subprocess, sys

root = pathlib.Path(__file__).resolve().parents[2]
out = root / 'astra-e2e-d-ai-pbl-2'
env = dict(l.split('=', 1) for l in (root / '.env').read_text().splitlines() if '=' in l and not l.startswith('#'))
secret = env['SONAR_TERMINAL_SHARED_SECRET'].strip('"\'')
secret_hash = hashlib.sha256(secret.encode()).hexdigest()

# name -> (transport, host, expected conf values)
infra = [('Gateway-Router', '172.16.255.1'), ('DMZ-Router', '172.16.255.3'), ('C4I-Network-Router', '172.16.255.4'),
         ('Survillance-Network-Router', '172.16.255.5'), ('VDI-Router', '172.16.255.6')]
switches = [(f'Switch-{i}', f'172.16.255.{101+i}') for i in range(5)]
firewall = [('Firewall', '172.16.255.2')]


def ssh(host, cmd, timeout=20):
    r = subprocess.run(['ssh', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=6', 'root@' + host, cmd],
                       capture_output=True, text=True, timeout=timeout)
    return r.stdout


def parse(text):
    conf = {}
    for line in text.splitlines():
        if '=' in line:
            k, v = line.split('=', 1)
            conf[k.strip()] = v.strip().rstrip(';').strip('"\'')
    return conf


results = []


def check(name, conf, expected):
    entry = {'name': name, 'agent_name': conf.get('AGENT_NAME'), 'node_type': conf.get('NODE_TYPE'),
             'server_ip': conf.get('SERVER_IP'), 'server_port': conf.get('SERVER_PORT'),
             'management_prefix': conf.get('MANAGEMENT_PREFIX')}
    ok = (conf.get('AGENT_NAME') == name and conf.get('NODE_TYPE') == expected['NODE_TYPE']
          and conf.get('SERVER_IP') == expected['SERVER_IP'] and conf.get('SERVER_PORT') == '3000'
          and conf.get('MANAGEMENT_PREFIX') == '172.16.255.0/24')
    entry['expected_node_type'] = expected['NODE_TYPE']
    entry['expected_server_ip'] = expected['SERVER_IP']
    sec = conf.get('TERMINAL_SHARED_SECRET', '')
    entry['secret_match'] = hashlib.sha256(sec.encode()).hexdigest() == secret_hash if sec else False
    entry['html_entities'] = ('&#' in sec or '&amp;' in sec)
    entry['pass'] = bool(ok and entry['secret_match'] and not entry['html_entities'])
    results.append(entry)


# Routers / switches / firewall: management network, secret on the node
for name, host in infra + switches + firewall:
    expected = {'NODE_TYPE': 'Router' if (name, host) in infra else ('Firewall' if name == 'Firewall' else 'Switch'),
                'SERVER_IP': '172.16.255.245'}
    try:
        conf = parse(ssh(host, 'cat /etc/sonar_validator_prober/default.conf'))
    except Exception as e:  # noqa: BLE001
        results.append({'name': name, 'pass': False, 'error': str(e)})
        continue
    check(name, conf, expected)

# Container VMs on the GNS3 host reach the backend over 192.168.122.0/24
remote = 'ssh1032007@192.168.122.1'
for name in ['TOD-Cam', 'UAV', 'VDI-1', 'VDI-2']:
    container = f'GNS3.{name}.5a790fe8-a6eb-4251-b21e-010258ac0241'
    try:
        r = subprocess.run(['ssh', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=6', remote,
                            f'docker exec {container} cat /etc/sonar_validator_prober/default.conf'],
                           capture_output=True, text=True, timeout=25)
        conf = parse(r.stdout)
    except Exception as e:  # noqa: BLE001
        results.append({'name': name, 'pass': False, 'error': str(e)})
        continue
    check(name, conf, {'NODE_TYPE': 'VM', 'SERVER_IP': '192.168.122.58'})

(out / 'evidence' / 'config-audit.json').write_text(json.dumps(results, indent=2))
for r in results:
    print(f"{r['name']:28} pass={r.get('pass')} type={r.get('node_type')} server={r.get('server_ip')} secret={r.get('secret_match')}")
print('TOTAL', sum(1 for r in results if r.get('pass')), '/', len(results))
sys.exit(0 if all(r.get('pass') for r in results) else 1)
