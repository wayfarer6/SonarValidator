import base64, configparser, json, pathlib, sys, urllib.request

config = configparser.ConfigParser()
config.read(pathlib.Path.home()/'.config/GNS3/2.2/gns3_server.conf')
server = config['Server']
project = '5a790fe8-a6eb-4251-b21e-010258ac0241'
def api(path, method='GET'):
    req = urllib.request.Request('http://127.0.0.1:3080/v2'+path, method=method)
    req.add_header('Authorization', 'Basic '+base64.b64encode((server['user']+':'+server['password']).encode()).decode())
    with urllib.request.urlopen(req, timeout=90) as response:
        body=response.read()
        return json.loads(body) if body else None

nodes=api('/projects/'+project+'/nodes')
if len(sys.argv)>1 and sys.argv[1] in ('start','stop'):
    action=sys.argv[1]
    selected=sys.argv[2:]
    for n in nodes:
        if (not selected or n['name'] in selected) and n['status']!=('started' if action=='start' else 'stopped'):
            try:
                api('/projects/'+project+'/nodes/'+n['node_id']+'/'+action, 'POST')
                print(action+' '+n['name'], file=sys.stderr, flush=True)
            except Exception as e:
                print(n['name']+': '+str(e), file=sys.stderr, flush=True)
    nodes=api('/projects/'+project+'/nodes')
print(json.dumps([{**{k:n.get(k) for k in ['name','node_id','node_type','console','status']},'container_id':n.get('properties',{}).get('container_id')} for n in nodes],indent=2))
