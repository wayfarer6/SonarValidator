#!/usr/bin/env python3
"""Stage complete RVI installers without changing pre-existing Installer deletions."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument('--binary', type=Path, required=True)
parser.add_argument('--secret-config', type=Path, required=True)
args = parser.parse_args()
secret = next((line.split('=', 1)[1].split(';', 1)[0].strip().strip('"')
               for line in args.secret_config.read_text().splitlines()
               if line.strip().startswith('TERMINAL_SHARED_SECRET=')), '')
if len(secret) < 32:
    raise SystemExit('A shared key of at least 32 characters is required')
source = root / 'SonarValidator_Prober'
assets = {
    'Installer.sh': source / 'Installer/Installer.sh',
    'sonar_validator_prober': args.binary,
    'default_template.sqlite': source / 'Installer/default_template.sqlite',
    'systemd/sonar_validator_prober.service': source / 'components/system_service_registration/systemd/prober.service',
    'systemd/sonar_validator_prober-cisco.service': source / 'components/system_service_registration/systemd/prober-cisco.service',
    'rc-service/sonar_validator_prober': source / 'components/system_service_registration/rc-service/sonar_validator_prober',
}
artifacts = Path(__file__).resolve().parent / 'artifacts'
artifacts.mkdir(exist_ok=True, mode=0o700)
for vendor, host, kind, name, prefixes in [
    ('Arista', '10.20.0.3', 'Switch', 'Arista-Switch', '10.20.0.0/24'),
    # 2026-10-10 실측 정정: Cisco guestshell 에서 관리망 10.20.0.3 의
    # TCP 3000 과 WebSocket 101 이 정상이었다. 192.168.122.32 는 폴백.
    ('Cisco', '10.20.0.3', 'Router', 'Cisco-Router', '10.20.0.0/24,192.168.35.0/24,192.168.122.0/24'),
]:
    destination = artifacts / vendor / 'Installer'
    destination.mkdir(parents=True, exist_ok=True)
    for relative, path in assets.items():
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
    config = destination / 'default.conf'
    config.write_text(f'SERVER_IP={host};\nSERVER_PORT=3000;\nNODE_TYPE={kind};\nAGENT_NAME={name};\n'
                      f'TERMINAL_SHARED_SECRET={secret};\nMANAGEMENT_PREFIX={prefixes};\n')
    config.chmod(0o600)
print(json.dumps({'binary_sha256': hashlib.sha256(args.binary.read_bytes()).hexdigest(),
                  'bundles': ['artifacts/Arista/Installer', 'artifacts/Cisco/Installer']}, indent=2))
