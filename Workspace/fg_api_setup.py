#!/usr/bin/env python3
"""P6: create FortiGate REST API user + import EasyRSA RootCA (run on MC)."""
import json
import ssl
import sys
import urllib.parse
import urllib.request

BASE = "https://10.20.0.2"
ctx = ssl._create_unverified_context()

with open(sys.argv[1]) as f:
    CA_PEM = f.read()


def req(method, path, data=None, cookie=None, raw=False, form=False):
    url = BASE + path
    body = None
    headers = {}
    if data is not None:
        if form:
            body = urllib.parse.urlencode(data).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        else:
            body = json.dumps(data).encode()
            headers["Content-Type"] = "application/json"
    if cookie:
        headers["Cookie"] = cookie
        # FortiOS requires the APSCOOKIE value echoed as CSRF token on writes
        if "=" in cookie:
            headers["X-CSRFTOKEN"] = cookie.split("=", 1)[1]
    r = urllib.request.Request(url, data=body, headers=headers, method=method)
    resp = urllib.request.urlopen(r, context=ctx, timeout=20)
    out = resp.read().decode()
    return resp, (out if raw else json.loads(out))


# 1) login (logincheck returns HTML, not JSON)
resp, _ = req("POST", "/logincheck", {"username": "admin", "secretkey": "Pa129@Y"}, raw=True, form=True)
cookie = resp.headers.get("Set-Cookie").split(";")[0]
print("login ok:", resp.status)

# 2) api-user profile
try:
    _, j = req("POST", "/api/v2/cmdb/system/api-user-profile", {
        "name": "sonar_api_profile",
        "scope": "vdom",
        "vdom": ["root"],
        "accprofile": "super_admin",
    }, cookie)
    print("profile:", j.get("status"), j.get("http_status"))
except urllib.error.HTTPError as e:
    print("profile err:", e.code, e.read().decode()[:300])

# 3) api user with trusthost = management console
key = None
try:
    _, j = req("POST", "/api/v2/cmdb/system/api-user", {
        "name": "sonar_api",
        "api-profile": "sonar_api_profile",
        "trusthost1": "10.20.0.3 255.255.255.255",
    }, cookie)
    print("api-user:", j.get("status"), j.get("http_status"))
    key = j.get("key") or (j.get("results") or {}).get("key")
except urllib.error.HTTPError as e:
    print("api-user err:", e.code, e.read().decode()[:300])

if not key:
    # fetch existing key info if user already existed
    try:
        _, j = req("GET", "/api/v2/cmdb/system/api-user/sonar_api", cookie=cookie)
        print("existing api-user:", json.dumps(j)[:400])
    except urllib.error.HTTPError as e:
        print("get api-user err:", e.code, e.read().decode()[:300])

# 4) import RootCA
try:
    _, j = req("POST", "/api/v2/cmdb/vpn-certificate/ca", {
        "name": "SonarValidatorRootCA",
        "certificate": CA_PEM,
    }, cookie)
    print("ca import:", j.get("status"), j.get("http_status"))
except urllib.error.HTTPError as e:
    print("ca err:", e.code, e.read().decode()[:300])

# 5) verify CA present
try:
    _, j = req("GET", "/api/v2/cmdb/vpn-certificate/ca/SonarValidatorRootCA", cookie=cookie)
    r = j.get("results", {})
    print("ca verify:", r.get("name"), "| issuer:", (r.get("issuer") or "")[:60])
except urllib.error.HTTPError as e:
    print("ca verify err:", e.code, e.read().decode()[:200])

if key:
    with open("/home/osboxes/fg_api_key.txt", "w") as f:
        f.write(key + "\n")
    import os
    os.chmod("/home/osboxes/fg_api_key.txt", 0o600)
    print("API KEY SAVED to ~/fg_api_key.txt")
    print("KEY:", key)
